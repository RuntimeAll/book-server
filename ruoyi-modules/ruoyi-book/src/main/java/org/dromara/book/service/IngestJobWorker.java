package org.dromara.book.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.config.IngestJobAsyncConfig;
import org.dromara.book.domain.bo.IngestQuestionBo;
import org.dromara.book.domain.entity.BizIngestJob;
import org.dromara.book.domain.entity.BizIngestJobItem;
import org.dromara.book.mapper.BizIngestJobItemMapper;
import org.dromara.book.mapper.BizIngestJobMapper;
import org.dromara.book.util.SplitClient;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * 批量录题作业异步 Worker（PRD-A-002 路B）。
 *
 * <p>在专用线程池 {@code ingestJobExecutor} 跑（与 web 线程隔离）。状态机：
 * {@code PENDING → EXTRACT_ING（分流抽取）→ SPLIT_ING（调 toolkit /split）→ DONE / FAILED(error_msg)}。
 *
 * <p>抽取分流（MVP 稳健优先）：
 * <ul>
 *   <li><b>图片</b>(jpg/png/webp/gif)：source_type=image, lane=slow → 字节 base64 → /split image_base64。主干。</li>
 *   <li><b>PDF/DOCX</b>：类路径无 POI/PDFBox 依赖 → 不实现自动抽取，直接 FAILED + 友好提示（不崩）。</li>
 * </ul>
 *
 * <p>🔴 异步线程无请求上下文 → teacherId 从 {@code job.teacherId} 取，不用 LoginHelper。
 * 多表写全程 {@code TenantHelper.ignore(DataPermissionHelper.ignore(...))} 包裹（biz_* 无 tenant_id）。
 * worker 内全程 try/catch，绝不让作业卡在中间态。
 *
 * @author backend-dev (PRD-A-002 路B)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestJobWorker {

    private final BizIngestJobMapper jobMapper;
    private final BizIngestJobItemMapper itemMapper;
    private final SplitClient splitClient;
    private final IIngestService ingestService;

    /** 支持的图片后缀（小写，不含点） */
    private static final List<String> IMAGE_EXTS = List.of("jpg", "jpeg", "png", "webp", "gif");

    /** 后缀 → MIME */
    private static String mimeOf(String ext) {
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }

    /**
     * 异步执行录题作业。整个方法 try/catch 兜底，绝不让作业卡在中间态。
     *
     * @param jobId    作业 id
     * @param rawBytes 上传文件原始字节（worker 持字节，不依赖请求上下文）
     */
    @Async(IngestJobAsyncConfig.INGEST_JOB_EXECUTOR)
    public void run(Long jobId, byte[] rawBytes) {
        long startMs = System.currentTimeMillis();
        try {
            doRun(jobId, rawBytes);
        } catch (Exception e) {
            log.error("[ingest-worker] 作业异常 jobId={} err={}", jobId, e.getMessage(), e);
            markFailed(jobId, "录题异常：" + safeMsg(e));
        } finally {
            log.info("[ingest-worker] 作业结束 jobId={} 耗时={}ms", jobId, System.currentTimeMillis() - startMs);
        }
    }

    private void doRun(Long jobId, byte[] rawBytes) {
        BizIngestJob job = selectJob(jobId);
        if (job == null) {
            log.warn("[ingest-worker] job 不存在 jobId={}", jobId);
            return;
        }
        // ===== 1. 抽取分流：整页 → 富文本（B2 维护者拍板：路B 走 TextIn 外部 OCR）=====
        updateStatus(jobId, BizIngestJob.STATUS_EXTRACT_ING, null);

        String ext = extOf(job.getSourceFileName());
        boolean isImage = IMAGE_EXTS.contains(ext);

        String markdown = null;
        List<String> imageBase64Fallback = null;   // 仅 TextIn 不可用时给 /split 兜底（opus 多模态读图）

        if (rawBytes == null || rawBytes.length == 0) {
            markFailed(jobId, "上传文件为空，无法拆题");
            return;
        }
        String b64 = Base64.getEncoder().encodeToString(rawBytes);

        if (isImage) {
            // 图片：优先 TextIn /ocr 转富文本；失败则退 opus 多模态读图（imageBase64 兜底）
            markdown = tryOcr(jobId, b64);
            if (StringUtils.isNotBlank(markdown)) {
                updateMeta(jobId, "image", "fast");
            } else {
                imageBase64Fallback = new ArrayList<>(1);
                imageBase64Fallback.add(b64);
                updateMeta(jobId, "image", "slow");
            }
        } else if ("pdf".equals(ext)) {
            // 🔴 PDF 现已支持（TextIn /ocr 整页转富文本）—— 旧实现直接 FAILED 是 B2 漏的真实路径
            markdown = tryOcr(jobId, b64);
            if (StringUtils.isBlank(markdown)) {
                markFailed(jobId, "PDF 整页 OCR 失败（TextIn 不可用或为纯扫描无文字层），请改用清晰图片上传");
                return;
            }
            updateMeta(jobId, "pdf", "fast");
        } else if ("docx".equals(ext)) {
            // DOCX 文字层抽取（POI XWPF 已在类路径，零新依赖）→ 快档；POI 抽不出再退 TextIn
            try {
                markdown = extractDocxText(rawBytes);
            } catch (Exception e) {
                log.warn("[ingest-worker] docx POI 抽取异常 jobId={} err={}（转 TextIn 兜底）", jobId, e.getMessage());
                markdown = null;
            }
            if (StringUtils.isBlank(markdown)) {
                markdown = tryOcr(jobId, b64);
            }
            if (StringUtils.isBlank(markdown)) {
                markFailed(jobId, "Word 文档无可抽取文本（扫描/图片型 Word，请改用图片上传）");
                return;
            }
            updateMeta(jobId, "docx", "fast");
        } else {
            markFailed(jobId, "暂不支持的文件格式（当前支持 图片 jpg/png/webp/gif、PDF、Word docx）");
            return;
        }

        // ===== 2. 拆单题（置 SPLIT_ING）：只拆题干 + 抽原卷解析，不在大调用里解题（B2 根因纠正）=====
        updateStatus(jobId, BizIngestJob.STATUS_SPLIT_ING, null);
        // from_source 抽原卷答案；其余（ai_solve/stem_only）拆完单题再走 /solve，故 split 只拆题干
        String splitMode = "from_source".equalsIgnoreCase(job.getAnswerMode()) ? "from_source" : "stem_only";
        SplitClient.SplitResponse resp;
        try {
            resp = splitClient.split(markdown, imageBase64Fallback, splitMode, null, null);
        } catch (Exception e) {
            log.error("[ingest-worker] 调 /split 失败 jobId={} err={}", jobId, e.getMessage(), e);
            markFailed(jobId, "AI 拆题失败：" + safeMsg(e));
            return;
        }
        if (!resp.isOk() && resp.getError() != null) {
            markFailed(jobId, "AI 拆题失败：" + truncate(resp.getError(), 900));
            return;
        }

        // ===== 3. 逐题写 biz_ingest_job_item（题干态，dna/答案待 SOLVING 回填）=====
        List<SplitClient.SplitQuestion> questions = resp.getQuestions() == null ? List.of() : resp.getQuestions();
        Date now = new Date();
        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            int seq = 1;
            for (SplitClient.SplitQuestion q : questions) {
                BizIngestJobItem item = new BizIngestJobItem();
                item.setJobId(jobId);
                item.setSeq(seq++);
                item.setStemText(q.getStem());
                item.setQuestionType(mapQtype(q.getQtype()));
                if (q.getOptions() != null && !q.getOptions().isEmpty()) {
                    item.setOptionsJson(JsonUtils.toJsonString(q.getOptions()));
                }
                item.setAnswerText(q.getAnswer());
                item.setAnalyzeText(q.getAnalysis());
                item.setHasFigure(q.isHasFigure() ? 1 : 0);
                item.setDifficulty(mapDifficulty(q.getDnaDifficulty()));
                item.setDnaJson(q.getDnaJson());
                item.setVerifyVerdict(null);
                item.setNeedReview(0);
                item.setItemStatus(BizIngestJobItem.STATUS_PENDING);
                item.setCreateTime(now);
                item.setUpdateTime(now);
                itemMapper.insert(item);
            }
            return null;
        }));

        // 回填作业计数 + dropped
        BizIngestJob cnt = new BizIngestJob();
        cnt.setId(jobId);
        cnt.setQuestionCount(questions.size());
        if (resp.getDropped() != null && !resp.getDropped().isEmpty()) {
            cnt.setDroppedJson(JsonUtils.toJsonString(resp.getDropped()));
        }
        cnt.setUpdateTime(new Date());
        updateJob(cnt);
        log.info("[ingest-worker] 拆题完成 jobId={} 题数={} dropped={}",
            jobId, questions.size(), resp.getDropped() == null ? 0 : resp.getDropped().size());

        // ===== 4. 解题 + 打标（置 SOLVING）：单题粒度并发调 /solve 或 /label（B2 核心）=====
        // stem_only 模式跳过（只录题干）；from_source/ai_solve 逐题 solve/label。
        if (!"stem_only".equalsIgnoreCase(job.getAnswerMode()) && !questions.isEmpty()) {
            updateStatus(jobId, BizIngestJob.STATUS_SOLVING, null);
            solveAndLabelItems(jobId);
        }

        // ===== 5. DONE（N=0 也是 DONE，前端展空态）=====
        BizIngestJob done = new BizIngestJob();
        done.setId(jobId);
        done.setStatus(BizIngestJob.STATUS_DONE);
        done.setUpdateTime(new Date());
        updateJob(done);

        // ===== 6. commit_mode='direct' → 拆完直接全量入库（跳审核）=====
        if ("direct".equalsIgnoreCase(job.getCommitMode()) && !questions.isEmpty()) {
            autoCommitAll(jobId);
        }
    }

    /** TextIn /ocr 兜底包装：失败不抛，返回 null（调用方据此降级）。 */
    private String tryOcr(Long jobId, String fileBase64) {
        try {
            return splitClient.ocr(fileBase64);
        } catch (Exception e) {
            log.warn("[ingest-worker] TextIn /ocr 异常 jobId={} err={}（降级处理）", jobId, e.getMessage());
            return null;
        }
    }

    /** 单题解题并发上限（每次 /solve 是 ~20-40s 网络调用，控并发护 toolkit/中转池）。 */
    private static final int SOLVE_CONCURRENCY = 4;

    /**
     * SOLVING 步骤：对已拆出的 pending item **单题粒度并发** solve/label，回填答案/解析/DNA/验算。
     *
     * <p>🔴 B2 根因纠正核心：不再整卷一次大 JSON（截断崩），改逐题独立调用 —— 每次输出小、永不截断；
     * 有原卷答案的题走 /label（不重解只打标），无答案的题走 /solve（解题+自动打标+验算）。
     * 单题失败不拖垮整批（该题保留题干态）。worker 线程无请求上下文 → DB 写全程 ignore 包裹。
     */
    private void solveAndLabelItems(Long jobId) {
        List<BizIngestJobItem> items = selectPendingItems(jobId);
        if (items.isEmpty()) {
            return;
        }
        BizIngestJob job = selectJob(jobId);
        String gradeHint = job == null ? null : job.getGradeHint();
        java.util.concurrent.ExecutorService pool =
            java.util.concurrent.Executors.newFixedThreadPool(Math.min(SOLVE_CONCURRENCY, items.size()));
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (BizIngestJobItem item : items) {
                futures.add(pool.submit(() -> solveOneItem(item, gradeHint)));
            }
            for (java.util.concurrent.Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    log.warn("[ingest-worker] 单题 solve/label 任务异常 jobId={} err={}", jobId, e.getMessage());
                }
            }
        } finally {
            pool.shutdown();
        }
    }

    /** 单题 solve/label：有答案→/label（只打标），无答案→/solve（解题+打标+验算）。回填 item。 */
    private void solveOneItem(BizIngestJobItem item, String gradeHint) {
        try {
            List<String> options = parseOptions(item.getOptionsJson());
            String qtype = qtypeText(item.getQuestionType());
            SplitClient.SolveResult r;
            boolean hasAnswer = StringUtils.isNotBlank(item.getAnswerText());
            if (hasAnswer) {
                // 原卷自带答案：不重解，只打标
                r = splitClient.label(item.getStemText(), options, qtype, item.getAnswerText(), item.getAnalyzeText());
            } else {
                // 无答案：AI 解题 + 自动打标 + 验算
                r = splitClient.solve(item.getStemText(), options, qtype, gradeHint);
            }
            if (r == null) {
                return;
            }
            BizIngestJobItem iu = new BizIngestJobItem();
            iu.setId(item.getId());
            if (!hasAnswer) {
                if (StringUtils.isNotBlank(r.getAnswer())) {
                    iu.setAnswerText(r.getAnswer());
                }
                if (StringUtils.isNotBlank(r.getAnalysis())) {
                    iu.setAnalyzeText(r.getAnalysis());
                }
                if (r.getVerifyVerdict() != null) {
                    iu.setVerifyVerdict(r.getVerifyVerdict());
                }
            }
            if (StringUtils.isNotBlank(r.getDnaJson())) {
                iu.setDnaJson(r.getDnaJson());
            }
            if (r.getDnaDifficulty() != null) {
                iu.setDifficulty(mapDifficulty(r.getDnaDifficulty()));
            }
            iu.setUpdateTime(new Date());
            TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
                itemMapper.updateById(iu);
                return null;
            }));
        } catch (Exception e) {
            // 单题失败不拖垮整批：保留题干态，仅记日志
            log.warn("[ingest-worker] 单题 solve/label 失败 itemId={} err={}",
                item == null ? null : item.getId(), e.getMessage());
        }
    }

    /** options_json → List<String>（容错，解析失败返回空）。 */
    private static List<String> parseOptions(String optionsJson) {
        if (StringUtils.isBlank(optionsJson)) {
            return List.of();
        }
        try {
            List<String> list = JsonUtils.parseArray(optionsJson, String.class);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 库题型码 → 文本（选择/填空/解答），喂 toolkit /solve。 */
    private static String qtypeText(Integer code) {
        if (code == null) {
            return "解答";
        }
        return switch (code) {
            case 1 -> "选择";
            case 2 -> "填空";
            default -> "解答";
        };
    }

    /** direct 模式：拆完直接对所有 pending item 调 ingestQuestion 入库。 */
    private void autoCommitAll(Long jobId) {
        try {
            BizIngestJob job = selectJob(jobId);
            if (job == null) {
                return;
            }
            List<BizIngestJobItem> items = selectPendingItems(jobId);
            int committed = 0;
            for (BizIngestJobItem item : items) {
                if (commitOneItem(job, item)) {
                    committed++;
                }
            }
            log.info("[ingest-worker] direct 入库完成 jobId={} committed={}", jobId, committed);
        } catch (Exception e) {
            // direct 入库失败不回退作业状态（DONE 保留，已拆 item 在），仅记日志
            log.error("[ingest-worker] direct 入库异常 jobId={} err={}", jobId, e.getMessage(), e);
        }
    }

    /**
     * 入库单个 item：组 IngestQuestionBo(status='0' 草稿) → ingestQuestion → 回填 item。
     * 由 worker(direct) 与 service(review commit) 共用；返回是否成功入库。
     */
    public boolean commitOneItem(BizIngestJob job, BizIngestJobItem item) {
        if (item == null || job == null) {
            return false;
        }
        if (BizIngestJobItem.STATUS_COMMITTED.equals(item.getItemStatus())
            || BizIngestJobItem.STATUS_DROPPED.equals(item.getItemStatus())) {
            return false;
        }
        IngestQuestionBo bo = new IngestQuestionBo();
        bo.setStatus("0");
        bo.setSubjectId(job.getSubjectId());
        bo.setQuestionType(item.getQuestionType() != null ? item.getQuestionType() : 5);
        bo.setDifficult(item.getDifficulty() != null ? item.getDifficulty() : 2);
        bo.setStemText(item.getStemText());
        bo.setAnswerText(item.getAnswerText());
        bo.setAnalyzeText(item.getAnalyzeText());
        bo.setImportSource("ingest-batch");
        bo.setImportBatchId(String.valueOf(job.getId()));
        // 配图：has_figure 时挂源文件 OSS 图（整批源图，role=figure）。源文件非图（PDF/DOCX 当前不走到此）时跳过。
        if (item.getHasFigure() != null && item.getHasFigure() == 1
            && StringUtils.isNotBlank(job.getSourceOssUrl()) && "image".equals(job.getSourceType())) {
            IngestQuestionBo.ImageRef img = new IngestQuestionBo.ImageRef();
            img.setOssUrl(job.getSourceOssUrl());
            img.setRole("figure");
            img.setSeq(0);
            img.setIsDecorative(0);
            bo.setImages(List.of(img));
        }
        java.util.Map<String, Object> r = ingestService.ingestQuestion(bo, job.getTeacherId());
        Object qid = r.get("questionId");
        Long committedQid = qid == null ? null : Long.valueOf(String.valueOf(qid));

        // 回填 item + job.committedCount++（ignore 包裹）
        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            BizIngestJobItem iu = new BizIngestJobItem();
            iu.setId(item.getId());
            iu.setCommittedQuestionId(committedQid);
            iu.setItemStatus(BizIngestJobItem.STATUS_COMMITTED);
            iu.setUpdateTime(new Date());
            itemMapper.updateById(iu);
            return null;
        }));
        bumpCommittedCount(job.getId());
        return true;
    }

    // ==================== qtype / difficulty 映射 ====================

    /** qtype 文本 → 库题型码：选择1/填空2/解答5（默认5）。 */
    static int mapQtype(String qtype) {
        if (qtype == null) {
            return 5;
        }
        String t = qtype.trim();
        if (t.contains("选择")) {
            return 1;
        }
        if (t.contains("填空")) {
            return 2;
        }
        if (t.contains("解答")) {
            return 5;
        }
        return 5;
    }

    /** dna.difficulty 1-4 → 库 1-3：1→1,2→2,3/4→3；无 dna 默认 2。 */
    static int mapDifficulty(Integer dnaDifficulty) {
        if (dnaDifficulty == null) {
            return 2;
        }
        return switch (dnaDifficulty) {
            case 1 -> 1;
            case 2 -> 2;
            default -> 3; // 3、4 与越界值统一压到 3
        };
    }

    // ==================== DB helpers（全 ignore 包裹） ====================

    private BizIngestJob selectJob(Long jobId) {
        return TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> jobMapper.selectById(jobId)));
    }

    private List<BizIngestJobItem> selectPendingItems(Long jobId) {
        return TenantHelper.ignore(() -> DataPermissionHelper.ignore(() ->
            itemMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizIngestJobItem>()
                .eq(BizIngestJobItem::getJobId, jobId)
                .eq(BizIngestJobItem::getItemStatus, BizIngestJobItem.STATUS_PENDING)
                .orderByAsc(BizIngestJobItem::getSeq))));
    }

    private void updateStatus(Long jobId, String status, String errorMsg) {
        BizIngestJob upd = new BizIngestJob();
        upd.setId(jobId);
        upd.setStatus(status);
        if (errorMsg != null) {
            upd.setErrorMsg(truncate(errorMsg, 1000));
        }
        upd.setUpdateTime(new Date());
        updateJob(upd);
    }

    private void updateMeta(Long jobId, String sourceType, String lane) {
        BizIngestJob upd = new BizIngestJob();
        upd.setId(jobId);
        upd.setSourceType(sourceType);
        upd.setLane(lane);
        upd.setUpdateTime(new Date());
        updateJob(upd);
    }

    private void markFailed(Long jobId, String msg) {
        BizIngestJob upd = new BizIngestJob();
        upd.setId(jobId);
        upd.setStatus(BizIngestJob.STATUS_FAILED);
        upd.setErrorMsg(truncate(msg, 1000));
        upd.setUpdateTime(new Date());
        updateJob(upd);
    }

    private void bumpCommittedCount(Long jobId) {
        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            BizIngestJob fresh = jobMapper.selectById(jobId);
            if (fresh == null) {
                return null;
            }
            BizIngestJob upd = new BizIngestJob();
            upd.setId(jobId);
            upd.setCommittedCount((fresh.getCommittedCount() == null ? 0 : fresh.getCommittedCount()) + 1);
            upd.setUpdateTime(new Date());
            jobMapper.updateById(upd);
            return null;
        }));
    }

    private void updateJob(BizIngestJob upd) {
        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            jobMapper.updateById(upd);
            return null;
        }));
    }

    /** DOCX 文字层抽取（POI XWPF·含段落+表格文本，经 EasyExcel 传递依赖已在类路径）。返回纯文本喂 /split。 */
    private static String extractDocxText(byte[] bytes) throws Exception {
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc =
                 new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(bytes));
             org.apache.poi.xwpf.extractor.XWPFWordExtractor ex =
                 new org.apache.poi.xwpf.extractor.XWPFWordExtractor(doc)) {
            String text = ex.getText();
            return text == null ? "" : text.trim();
        }
    }

    private static String extOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String safeMsg(Throwable e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String m = cause.getMessage();
        return m == null ? cause.getClass().getSimpleName() : m;
    }
}

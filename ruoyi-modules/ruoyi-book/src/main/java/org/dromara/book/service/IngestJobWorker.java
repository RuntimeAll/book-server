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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        List<String> imageBase64Fallback = null;   // 无文字层时给 /split 的 opus 多模态读图页
        // 🔴 PRD-A-024 批1·裁图源页：图片=源图；PDF slow=各页图。文字层(fast)无页图 → 不裁。
        List<String> cropPageImages = new ArrayList<>();

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
            // 图片作业永远保留源图供裁图（无论走 fast/slow），裁图从原图检 bbox。
            cropPageImages.add(b64);
        } else if ("pdf".equals(ext)) {
            // 🔴 PRD-A-024 批1·PDF 统一分流（D9，不用 TextIn）：本地检文字层 →
            //   有字层 lane=fast 抽全文（同 docx 文本路）；纯扫描 lane=slow 按页转图（同图片多模态路）。
            SplitClient.PdfConvertResult pdf;
            try {
                pdf = splitClient.convertPdf(b64);
            } catch (Exception e) {
                log.error("[ingest-worker] 调 /convert_pdf 失败 jobId={} err={}", jobId, e.getMessage(), e);
                markFailed(jobId, "PDF 分流失败：" + safeMsg(e));
                return;
            }
            if (pdf == null || !pdf.isOk()) {
                markFailed(jobId, "PDF 分流失败：" + (pdf == null ? "空响应" : truncate(pdf.getError(), 800)));
                return;
            }
            if ("fast".equalsIgnoreCase(pdf.getLane())) {
                markdown = pdf.getMarkdown();
                if (StringUtils.isBlank(markdown)) {
                    markFailed(jobId, "PDF 文字层为空，无法拆题");
                    return;
                }
                updateMeta(jobId, "pdf", "fast");
            } else {
                // slow：纯扫描页图走 opus 多模态（多页=多张页图）
                List<String> pages = pdf.getImages();
                if (pages == null || pages.isEmpty()) {
                    markFailed(jobId, "PDF 纯扫描转图为空，无法拆题");
                    return;
                }
                imageBase64Fallback = new ArrayList<>(pages);
                cropPageImages.addAll(pages);
                updateMeta(jobId, "pdf", "slow");
            }
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
            resp = splitClient.split(markdown, imageBase64Fallback, splitMode, null, null, job.getTeacherId());
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

        // ===== 3.5 裁图（PRD-A-024 批1）：图片/PDF慢档源页 → /crop_figures → OSS → figures_json =====
        if (!cropPageImages.isEmpty() && !questions.isEmpty()) {
            try {
                cropAndAttachFigures(jobId, cropPageImages, questions.size());
            } catch (Exception e) {
                // 裁图失败不拖垮作业（题干态照常入库，只是无真裁图），仅记日志
                log.warn("[ingest-worker] 裁图整体异常 jobId={} err={}", jobId, e.getMessage());
            }
        }

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
        // 🔴 PRD-A-024 批0·归因透传：登录老师 user_id 传给 /solve·/label → toolkit 落 conv_trace.teacher_id。
        Long teacherId = job == null ? null : job.getTeacherId();
        java.util.concurrent.ExecutorService pool =
            java.util.concurrent.Executors.newFixedThreadPool(Math.min(SOLVE_CONCURRENCY, items.size()));
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (BizIngestJobItem item : items) {
                futures.add(pool.submit(() -> solveOneItem(item, gradeHint, teacherId)));
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
    private void solveOneItem(BizIngestJobItem item, String gradeHint, Long teacherId) {
        try {
            List<String> options = parseOptions(item.getOptionsJson());
            String qtype = qtypeText(item.getQuestionType());
            SplitClient.SolveResult r;
            boolean hasAnswer = StringUtils.isNotBlank(item.getAnswerText());
            if (hasAnswer) {
                // 原卷自带答案：不重解，只打标
                r = splitClient.label(item.getStemText(), options, qtype, item.getAnswerText(), item.getAnalyzeText(), teacherId);
            } else {
                // 无答案：AI 解题 + 自动打标 + 验算
                r = splitClient.solve(item.getStemText(), options, qtype, gradeHint, teacherId);
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
        // 配图（PRD-A-024 批1）：优先用裁出真图（figures_json，仅 assigned!=false 的作真题图）；
        // 无裁图时退回旧行为（整批源图挂 has_figure，仅 image 源）。
        List<IngestQuestionBo.ImageRef> figRefs = buildFigureImageRefs(item.getFiguresJson());
        if (!figRefs.isEmpty()) {
            bo.setImages(figRefs);
        } else if (item.getHasFigure() != null && item.getHasFigure() == 1
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

    // ==================== 裁图入库（PRD-A-024 批1）====================

    /** 整卷启发式 conf 闸（D5/H1）：低于此的候选框视作误检丢弃。 */
    private static final double CROP_MIN_CONF = 0.5;

    /**
     * 裁图并按 D5/H1 保守闸归属，写 figures_json（+ has_figure=1）。整体调用方已 try 兜底，绝不影响主流程。
     *
     * <p>单题图（questionCount==1）：全部检出图挂第一题（H1 单题 conf 0.92+）。
     * 整卷（多题）：conf≥0.5 过滤后，若「图数≤题数」→ 按 y序×题序 zip 挂题（assigned=true）；
     * 否则 assigned=false（图不丢，落 item 层待人工挂，commit 不作真题图）。
     */
    private void cropAndAttachFigures(Long jobId, List<String> pageImages, int questionCount) {
        // 1. 逐页检图 + conf 闸 + 上传 OSS（OSS 走 BE，toolkit 不碰凭据，D6）
        List<Fig> valid = new ArrayList<>();
        for (int p = 0; p < pageImages.size(); p++) {
            SplitClient.CropResult cr;
            try {
                cr = splitClient.cropFigures(pageImages.get(p));
            } catch (Exception e) {
                log.warn("[ingest-worker] /crop_figures 调用失败 jobId={} page={} err={}", jobId, p, e.getMessage());
                continue;
            }
            if (cr == null || !cr.isOk() || cr.getFigures() == null) {
                continue;
            }
            for (SplitClient.CropFigure f : cr.getFigures()) {
                if (f.getConf() < CROP_MIN_CONF) {
                    continue;   // D5 conf 闸
                }
                Fig fig = uploadCrop(f.getImageBase64());
                if (fig == null) {
                    continue;
                }
                fig.bbox = f.getBbox();
                fig.conf = f.getConf();
                fig.page = p;
                fig.y1 = (f.getBbox() != null && f.getBbox().size() >= 2) ? f.getBbox().get(1) : 0;
                valid.add(fig);
            }
        }
        if (valid.isEmpty()) {
            return;
        }
        // 2. 排序 (页, y1)：整卷 y 序单调由此保证
        valid.sort((a, b) -> a.page != b.page ? Integer.compare(a.page, b.page) : Integer.compare(a.y1, b.y1));

        // 3. 取本作业 items（按 seq）
        List<BizIngestJobItem> items = selectItemsBySeq(jobId);
        if (items.isEmpty()) {
            return;
        }

        // 4. 归属
        Map<Long, List<Map<String, Object>>> byItem = new LinkedHashMap<>();
        if (questionCount == 1 || items.size() == 1) {
            for (Fig fig : valid) {
                addFigTo(byItem, items.get(0).getId(), fig, true);
            }
        } else {
            boolean gatePass = valid.size() <= questionCount;   // conf 已过滤；y 序单调已由排序保证
            for (int i = 0; i < valid.size(); i++) {
                int idx = Math.min(i, items.size() - 1);   // 超题数的图兜底挂最后一题，不丢
                addFigTo(byItem, items.get(idx).getId(), valid.get(i), gatePass);
            }
        }

        // 5. 落 figures_json + has_figure=1（ignore 包裹）
        Date now = new Date();
        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            for (Map.Entry<Long, List<Map<String, Object>>> e : byItem.entrySet()) {
                BizIngestJobItem iu = new BizIngestJobItem();
                iu.setId(e.getKey());
                iu.setFiguresJson(JsonUtils.toJsonString(e.getValue()));
                iu.setHasFigure(1);
                iu.setUpdateTime(now);
                itemMapper.updateById(iu);
            }
            return null;
        }));
        log.info("[ingest-worker] 裁图完成 jobId={} 有效图数={} 归属题数={} 题数={}",
            jobId, valid.size(), byItem.size(), questionCount);
    }

    /** 把一张裁图追加到某 item 的图列（seq 从 1 起，assigned 标是否确信归属）。 */
    private static void addFigTo(Map<Long, List<Map<String, Object>>> byItem, Long itemId, Fig fig, boolean assigned) {
        List<Map<String, Object>> list = byItem.computeIfAbsent(itemId, k -> new ArrayList<>());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seq", list.size() + 1);
        m.put("assetId", fig.assetId);
        m.put("ossUrl", fig.ossUrl);
        m.put("bbox", fig.bbox);
        m.put("conf", fig.conf);
        m.put("assigned", assigned);
        list.add(m);
    }

    /**
     * base64 PNG → OSS + 注册 image_asset（走 {@link IIngestService#uploadImageBytes}，与整书录入同范式）。
     * 🔴 biz_question_image.asset_id NOT NULL 且 ingestQuestion 跳过 assetId==null 的 ImageRef，
     *    故必须经 image_asset 拿 assetId，不能只裸传 ossUrl。失败返 null（不抛，不拖垮作业）。
     */
    private Fig uploadCrop(String imageBase64) {
        if (StringUtils.isBlank(imageBase64)) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(imageBase64);
            Map<String, Object> r = ingestService.uploadImageBytes(bytes, ".png", "image/png", "figure", "ingest-crop");
            Object assetId = r.get("assetId");
            Object ossUrl = r.get("ossUrl");
            if (assetId == null || ossUrl == null) {
                return null;
            }
            Fig fig = new Fig();
            fig.assetId = Long.valueOf(String.valueOf(assetId));
            fig.ossUrl = String.valueOf(ossUrl);
            return fig;
        } catch (Exception e) {
            log.warn("[ingest-worker] 裁图上传 OSS/注册 image_asset 失败 err={}", e.getMessage());
            return null;
        }
    }

    /** figures_json → ImageRef 列（role=figure）；只取 assigned!=false 且带 assetId 的（整卷未确信归属的不作真题图）。 */
    private List<IngestQuestionBo.ImageRef> buildFigureImageRefs(String figuresJson) {
        List<IngestQuestionBo.ImageRef> refs = new ArrayList<>();
        if (StringUtils.isBlank(figuresJson)) {
            return refs;
        }
        try {
            List<Map> figs = JsonUtils.parseArray(figuresJson, Map.class);
            if (figs == null) {
                return refs;
            }
            for (Map f : figs) {
                Object assigned = f.get("assigned");
                if (assigned instanceof Boolean && !((Boolean) assigned)) {
                    continue;   // 未确信归属，不作真题图（图仍在 figures_json 待人工挂）
                }
                Object assetId = f.get("assetId");
                if (assetId == null) {
                    continue;   // 无 assetId 无法落 biz_question_image（asset_id NOT NULL）
                }
                IngestQuestionBo.ImageRef ref = new IngestQuestionBo.ImageRef();
                ref.setAssetId(Long.valueOf(String.valueOf(assetId)));
                Object url = f.get("ossUrl");
                if (url != null) {
                    ref.setOssUrl(String.valueOf(url));
                }
                ref.setRole("figure");
                Object seq = f.get("seq");
                ref.setSeq(seq instanceof Number ? ((Number) seq).intValue() : refs.size() + 1);
                ref.setIsDecorative(0);
                refs.add(ref);
            }
        } catch (Exception e) {
            log.warn("[ingest-worker] figures_json 解析失败（跳过裁图挂载）err={}", e.getMessage());
        }
        return refs;
    }

    private List<BizIngestJobItem> selectItemsBySeq(Long jobId) {
        return TenantHelper.ignore(() -> DataPermissionHelper.ignore(() ->
            itemMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizIngestJobItem>()
                .eq(BizIngestJobItem::getJobId, jobId)
                .orderByAsc(BizIngestJobItem::getSeq))));
    }

    /** 裁图临时载体（页内位置用于整卷 y 序归属）。 */
    private static class Fig {
        Long assetId;
        String ossUrl;
        List<Integer> bbox;
        double conf;
        int page;
        int y1;
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

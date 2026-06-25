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
        // 1. 抽取分流（置 EXTRACT_ING）
        updateStatus(jobId, BizIngestJob.STATUS_EXTRACT_ING, null);

        String ext = extOf(job.getSourceFileName());
        boolean isImage = IMAGE_EXTS.contains(ext);

        String markdown = null;
        List<String> imageBase64 = null;

        if (isImage) {
            // 图片主干：source_type=image / lane=slow / base64 → /split image_base64
            if (rawBytes == null || rawBytes.length == 0) {
                markFailed(jobId, "上传图为空，无法拆题");
                return;
            }
            String b64 = Base64.getEncoder().encodeToString(rawBytes);
            imageBase64 = new ArrayList<>(1);
            imageBase64.add(b64);
            updateMeta(jobId, "image", "slow");
        } else if ("pdf".equals(ext) || "docx".equals(ext) || "doc".equals(ext)) {
            // PDF/DOCX：无 POI/PDFBox 依赖 → 不支持自动抽取，友好降级（不崩）
            markFailed(jobId, "暂不支持该文件格式的自动抽取（当前支持图片；PDF/Word 文字层后续接入）");
            return;
        } else {
            markFailed(jobId, "暂不支持的文件格式（当前支持图片 jpg/png/webp/gif；PDF/Word 文字层后续接入）");
            return;
        }

        // 2. 调 toolkit /split（置 SPLIT_ING）
        updateStatus(jobId, BizIngestJob.STATUS_SPLIT_ING, null);
        SplitClient.SplitResponse resp;
        try {
            resp = splitClient.split(markdown, imageBase64, job.getAnswerMode(), job.getGradeHint(), null);
        } catch (Exception e) {
            log.error("[ingest-worker] 调 /split 失败 jobId={} err={}", jobId, e.getMessage(), e);
            markFailed(jobId, "AI 拆题失败：" + safeMsg(e));
            return;
        }
        if (!resp.isOk() && resp.getError() != null) {
            markFailed(jobId, "AI 拆题失败：" + truncate(resp.getError(), 900));
            return;
        }

        // 3. 逐题写 biz_ingest_job_item（全程 ignore 包裹）
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

        // 4. 回填作业计数 + dropped + DONE（N=0 也是 DONE，前端展空态）
        BizIngestJob upd = new BizIngestJob();
        upd.setId(jobId);
        upd.setQuestionCount(questions.size());
        if (resp.getDropped() != null && !resp.getDropped().isEmpty()) {
            upd.setDroppedJson(JsonUtils.toJsonString(resp.getDropped()));
        }
        upd.setStatus(BizIngestJob.STATUS_DONE);
        upd.setUpdateTime(new Date());
        updateJob(upd);

        log.info("[ingest-worker] 拆题完成 jobId={} 题数={} dropped={}",
            jobId, questions.size(), resp.getDropped() == null ? 0 : resp.getDropped().size());

        // 5. commit_mode='direct' → 拆完直接全量入库（跳审核）
        if ("direct".equalsIgnoreCase(job.getCommitMode()) && !questions.isEmpty()) {
            autoCommitAll(jobId);
        }
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

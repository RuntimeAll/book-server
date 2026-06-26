package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.IngestJobCommitBo;
import org.dromara.book.domain.bo.IngestJobItemEditBo;
import org.dromara.book.domain.entity.BizIngestJob;
import org.dromara.book.domain.entity.BizIngestJobItem;
import org.dromara.book.domain.vo.IngestJobVo;
import org.dromara.book.mapper.BizIngestJobItemMapper;
import org.dromara.book.mapper.BizIngestJobMapper;
import org.dromara.book.service.IIngestService;
import org.dromara.book.service.IngestJobWorker;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量上传录题作业 Controller（PRD-A-002 路B）。
 *
 * <p>前缀 {@code /teacher/ingest/job}，命中 {@link MisiktEnvelopeAdvice} 自动包
 * {@code {code:1, message, response}}。全端点 {@code @SaCheckLogin}；teacherId 由
 * {@link LoginHelper#getUserId()} 注入，绝不信 body。
 *
 * <p>🔴 归属校验（C5 防越权）：job/item 的查/改/删/入库均比对 {@code job.teacherId == 当前登录}，
 * 不等抛业务异常。
 *
 * @author backend-dev (PRD-A-002 路B)
 */
@RestController
@RequestMapping("/teacher/ingest")
@RequiredArgsConstructor
public class IngestJobController {

    private final BizIngestJobMapper jobMapper;
    private final BizIngestJobItemMapper itemMapper;
    private final IIngestService ingestService;
    private final IngestJobWorker ingestJobWorker;
    /** B5 源件留存：走若依标准 OSS service（注册 sys_oss 表，非直连 OssFactory 只写 image_asset）。 */
    private final ISysOssService sysOssService;

    /** 允许上传后缀（图片主干 + pdf/docx 受理后由 worker 友好降级） */
    private static final long MAX_FILE_SIZE = 30L * 1024 * 1024;

    /**
     * 1) POST /teacher/ingest/job — 上传创建作业。
     *
     * <p>multipart：file + subjectId + answerMode + commitMode + gradeHint。存源文件 OSS、建 job(PENDING)、
     * 雪花 id、teacherId=LoginHelper、提交 @Async worker、立即返 {jobId}。
     */
    @SaCheckLogin
    @PostMapping("/job")
    public Map<String, Object> create(@RequestParam("file") MultipartFile file,
                                      @RequestParam("subjectId") String subjectId,
                                      @RequestParam(value = "answerMode", required = false) String answerMode,
                                      @RequestParam(value = "commitMode", required = false) String commitMode,
                                      @RequestParam(value = "gradeHint", required = false) String gradeHint) {
        Long teacherId = LoginHelper.getUserId();
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        if (StringUtils.isBlank(subjectId)) {
            throw new ServiceException("subjectId 不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ServiceException("文件不能超过 30MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ServiceException("读取上传文件失败：" + e.getMessage());
        }
        String fileName = StringUtils.defaultString(file.getOriginalFilename());
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";

        // 来源类型分流
        String detectedSourceType;
        if (List.of("jpg", "jpeg", "png", "webp", "gif").contains(ext)) {
            detectedSourceType = "image";
        } else if ("pdf".equals(ext)) {
            detectedSourceType = "pdf";
        } else if ("docx".equals(ext) || "doc".equals(ext)) {
            detectedSourceType = "docx";
        } else {
            detectedSourceType = "text";
        }

        // 🔴 B5 源件留存（维护者拍板）：原图/源文件**全部留存，不丢**；OSS 上传**走若依标准 service
        //   注册 sys_oss 表**（图片/pdf/docx 一视同仁），不再仅图片直连 OssFactory 写 image_asset。
        //   失败不阻断建作业（拆题用内存字节，不依赖 OSS）；图片源件的 url 同时作 has_figure 题配图。
        String sourceOssUrl = null;
        try {
            SysOssVo oss = sysOssService.upload(file);
            sourceOssUrl = oss == null ? null : oss.getUrl();
        } catch (Exception e) {
            sourceOssUrl = null;
        }

        // 建 job(PENDING)
        Date now = new Date();
        BizIngestJob job = new BizIngestJob();
        job.setTeacherId(teacherId);
        job.setSubjectId(subjectId);
        job.setSourceFileName(StringUtils.substring(fileName, 0, 255));
        job.setSourceOssUrl(sourceOssUrl);
        job.setSourceType(detectedSourceType);
        job.setAnswerMode(StringUtils.isBlank(answerMode) ? "from_source" : answerMode);
        job.setCommitMode(StringUtils.isBlank(commitMode) ? "review" : commitMode);
        job.setGradeHint(gradeHint);
        job.setStatus(BizIngestJob.STATUS_PENDING);
        job.setQuestionCount(0);
        job.setCommittedCount(0);
        job.setCreateUser(teacherId);
        job.setCreateBy(String.valueOf(teacherId));
        job.setCreateTime(now);
        job.setUpdateTime(now);
        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            jobMapper.insert(job);
            return null;
        }));

        // 提交异步 worker（持内存字节，不依赖请求上下文）
        ingestJobWorker.run(job.getId(), bytes);

        Map<String, Object> r = new HashMap<>();
        r.put("jobId", job.getId());
        return r;
    }

    /**
     * 2) GET /teacher/ingest/jobs?mine=1 — 当前老师作业列表（时间倒序）。供多功能球轮询。
     *
     */
    @SaCheckLogin
    @GetMapping("/jobs")
    public List<IngestJobVo> listMine(@RequestParam(value = "mine", required = false) String mine) {
        Long teacherId = LoginHelper.getUserId();
        List<BizIngestJob> jobs = TenantHelper.ignore(() -> DataPermissionHelper.ignore(() ->
            jobMapper.selectList(new LambdaQueryWrapper<BizIngestJob>()
                .eq(BizIngestJob::getTeacherId, teacherId)
                .orderByDesc(BizIngestJob::getCreateTime))));
        List<IngestJobVo> out = new ArrayList<>(jobs.size());
        for (BizIngestJob j : jobs) {
            IngestJobVo vo = new IngestJobVo();
            vo.setId(j.getId());
            vo.setStatus(j.getStatus());
            vo.setQuestionCount(j.getQuestionCount());
            vo.setCommittedCount(j.getCommittedCount());
            vo.setSourceFileName(j.getSourceFileName());
            vo.setErrorMsg(j.getErrorMsg());
            vo.setCreateTime(j.getCreateTime());
            out.add(vo);
        }
        return out;
    }

    /**
     * 3) GET /teacher/ingest/job/{jobId} — 作业详情 + 拆出题列表（供审核页）。先校验归属。
     */
    @SaCheckLogin
    @GetMapping("/job/{jobId}")
    public Map<String, Object> detail(@PathVariable("jobId") Long jobId) {
        BizIngestJob job = requireOwnedJob(jobId);
        List<BizIngestJobItem> items = TenantHelper.ignore(() -> DataPermissionHelper.ignore(() ->
            itemMapper.selectList(new LambdaQueryWrapper<BizIngestJobItem>()
                .eq(BizIngestJobItem::getJobId, jobId)
                .orderByAsc(BizIngestJobItem::getSeq))));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("job", job);
        r.put("items", items);
        return r;
    }

    /**
     * 4) POST /teacher/ingest/job/{jobId}/commit — body {itemIds:[long]}（空=全部 pending）。
     *
     * <p>对每个勾选 item 组 IngestQuestionBo(status='0') → ingestQuestion → 回填 item + job.committedCount++。
     * 返回 {committed:n}。校验归属。
     */
    @SaCheckLogin
    @PostMapping("/job/{jobId}/commit")
    public Map<String, Object> commit(@PathVariable("jobId") Long jobId,
                                      @RequestBody(required = false) IngestJobCommitBo bo) {
        BizIngestJob job = requireOwnedJob(jobId);
        List<Long> itemIds = bo == null ? null : bo.getItemIds();

        List<BizIngestJobItem> targets = TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            LambdaQueryWrapper<BizIngestJobItem> w = new LambdaQueryWrapper<BizIngestJobItem>()
                .eq(BizIngestJobItem::getJobId, jobId)
                .eq(BizIngestJobItem::getItemStatus, BizIngestJobItem.STATUS_PENDING)
                .orderByAsc(BizIngestJobItem::getSeq);
            if (itemIds != null && !itemIds.isEmpty()) {
                w.in(BizIngestJobItem::getId, itemIds);
            }
            return itemMapper.selectList(w);
        }));

        int committed = 0;
        for (BizIngestJobItem item : targets) {
            // item.jobId 已经 = jobId（查询条件锁死），归属随 job 校验过，安全
            if (ingestJobWorker.commitOneItem(job, item)) {
                committed++;
            }
        }
        Map<String, Object> r = new HashMap<>();
        r.put("committed", committed);
        return r;
    }

    /**
     * 5) DELETE /teacher/ingest/job/{jobId}/item/{itemId} — 软弃（item_status='dropped'，不真删）。校验归属。
     */
    @SaCheckLogin
    @DeleteMapping("/job/{jobId}/item/{itemId}")
    public Map<String, Object> dropItem(@PathVariable("jobId") Long jobId,
                                        @PathVariable("itemId") Long itemId) {
        requireOwnedJob(jobId);
        BizIngestJobItem item = TenantHelper.ignore(() -> DataPermissionHelper.ignore(() ->
            itemMapper.selectById(itemId)));
        if (item == null || !jobId.equals(item.getJobId())) {
            throw new ServiceException("拆出题不存在或不属于该作业");
        }
        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            BizIngestJobItem upd = new BizIngestJobItem();
            upd.setId(itemId);
            upd.setItemStatus(BizIngestJobItem.STATUS_DROPPED);
            upd.setUpdateTime(new Date());
            itemMapper.updateById(upd);
            return null;
        }));
        Map<String, Object> r = new HashMap<>();
        r.put("dropped", true);
        return r;
    }

    /**
     * 6) PUT /teacher/ingest/job/{jobId}/item/{itemId} — 审核页「就地改题」（PRD-A-002 B5）。
     *
     * <p>改拆错的题面/答案/解析/题型，存回 biz_ingest_job_item（入库前暂存编辑，commit 读改后值）。
     * 仅 pending 项可改（已入库 committed 不可改）；仅更新非 null 字段。校验归属。
     */
    @SaCheckLogin
    @PutMapping("/job/{jobId}/item/{itemId}")
    public Map<String, Object> updateItem(@PathVariable("jobId") Long jobId,
                                          @PathVariable("itemId") Long itemId,
                                          @RequestBody(required = false) IngestJobItemEditBo bo) {
        requireOwnedJob(jobId);
        BizIngestJobItem item = TenantHelper.ignore(() -> DataPermissionHelper.ignore(() ->
            itemMapper.selectById(itemId)));
        if (item == null || !jobId.equals(item.getJobId())) {
            throw new ServiceException("拆出题不存在或不属于该作业");
        }
        if (!BizIngestJobItem.STATUS_PENDING.equals(item.getItemStatus())) {
            throw new ServiceException("该题已入库或已弃，不可编辑");
        }
        if (bo != null) {
            TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
                BizIngestJobItem upd = new BizIngestJobItem();
                upd.setId(itemId);
                if (bo.getStemText() != null) {
                    upd.setStemText(bo.getStemText());
                }
                if (bo.getAnswerText() != null) {
                    upd.setAnswerText(bo.getAnswerText());
                }
                if (bo.getAnalyzeText() != null) {
                    upd.setAnalyzeText(bo.getAnalyzeText());
                }
                if (bo.getQuestionType() != null) {
                    upd.setQuestionType(bo.getQuestionType());
                }
                upd.setUpdateTime(new Date());
                itemMapper.updateById(upd);
                return null;
            }));
        }
        Map<String, Object> r = new HashMap<>();
        r.put("updated", true);
        return r;
    }

    // ==================== 归属校验 ====================

    /** 取作业并校验归属（teacher_id == 当前登录），不存在/越权抛异常。 */
    private BizIngestJob requireOwnedJob(Long jobId) {
        Long teacherId = LoginHelper.getUserId();
        BizIngestJob job = TenantHelper.ignore(() -> DataPermissionHelper.ignore(() ->
            jobMapper.selectById(jobId)));
        if (job == null) {
            throw new ServiceException("作业不存在");
        }
        if (job.getTeacherId() == null || !job.getTeacherId().equals(teacherId)) {
            throw new ServiceException("无权访问该作业");
        }
        return job;
    }
}

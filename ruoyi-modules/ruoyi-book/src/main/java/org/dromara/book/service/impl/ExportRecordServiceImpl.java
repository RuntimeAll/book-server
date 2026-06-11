package org.dromara.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.domain.bo.ExportOptions;
import org.dromara.book.domain.bo.ExportPaperBo;
import org.dromara.book.domain.entity.BizExportRecord;
import org.dromara.book.domain.vo.ExportRecordVo;
import org.dromara.book.domain.vo.ExportRetryVo;
import org.dromara.book.domain.vo.ExportSubmitVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.mapper.BizExportRecordMapper;
import org.dromara.book.service.ExportPdfWorker;
import org.dromara.book.service.IExportRecordService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 试卷导出任务 Service 实现（PRD-A-014）。
 *
 * <p>要点：
 * <ul>
 *   <li>user_id 一律 {@code LoginHelper.getUserId()} 取，硬隔离，绝不信前端。</li>
 *   <li>单用户并发 1（G7）：submit 前查该 user status in ('0','1') 在途任务，有则抛
 *       ServiceException（envelope code:500，message 含"已有导出任务"）。</li>
 *   <li>越权（G4/G6）：getRecord/retry 按 user_id 过滤，非本人返 null → 控制器转约定错误。</li>
 *   <li>预估模型：{@code estimatedSeconds = 2 + n × k × (1+含答案0.5+含解析0.8)}，
 *       k = 最近 20 条 done 的 duration_ms/题 滚动均值，冷启动 k=0.8。</li>
 * </ul>
 *
 * @author backend-dev
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportRecordServiceImpl implements IExportRecordService {

    /** 冷启动 k：每题 0.8 秒 */
    private static final double COLD_K = 0.8;

    /** 滚动均值取最近 N 条 done */
    private static final int ROLLING_N = 20;

    private final BizExportRecordMapper exportRecordMapper;
    private final ExportPdfWorker exportPdfWorker;
    private final ObjectMapper objectMapper;

    @Override
    public ExportSubmitVo submit(ExportPaperBo bo) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("未登录不能导出");
        }
        if (bo.getIds() == null || bo.getIds().isEmpty()) {
            throw new ServiceException("导出题目不能为空");
        }

        // G7 单用户并发 1：查在途任务（status 0 排队 / 1 生成中）
        long running = exportRecordMapper.selectCount(new LambdaQueryWrapper<BizExportRecord>()
            .eq(BizExportRecord::getUserId, userId)
            .in(BizExportRecord::getStatus, BizExportRecord.STATUS_QUEUED, BizExportRecord.STATUS_RUNNING));
        if (running > 0) {
            throw new ServiceException("已有导出任务正在进行，请等待完成后再试");
        }

        // options JSON（2026-06-11 起 showAnswer/showExplain 双开关，不再写 variant）
        ExportOptions options = new ExportOptions();
        options.setShowAnswer(Boolean.TRUE.equals(bo.getShowAnswer()));
        options.setShowExplain(Boolean.TRUE.equals(bo.getShowExplain()));
        options.setWatermark(Boolean.TRUE.equals(bo.getWatermark()));
        options.setIds(bo.getIds());
        String optionsJson;
        try {
            optionsJson = objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            throw new ServiceException("导出配置序列化失败");
        }

        Date now = new Date();
        BizExportRecord record = new BizExportRecord();
        record.setUserId(userId);
        record.setPaperId(parseLong(bo.getPaperId()));
        record.setFileName(trim255(bo.getFileName()));
        record.setOptions(optionsJson);
        record.setStatus(BizExportRecord.STATUS_QUEUED);
        record.setProgress(0);
        // 2026-06-11 用户拍板：不设过期，文件留存到用户自己删（删除连 OSS 一起删，见 deleteRecord）
        record.setCreateTime(now);
        record.setUpdateTime(now);
        exportRecordMapper.insert(record);

        int estimated = estimateSeconds(bo.getIds().size(),
            options.getShowAnswer(), options.getShowExplain());

        // 触发异步 Worker（专用线程池）
        exportPdfWorker.run(record.getId());

        log.info("[export-submit] user={} recordId={} n={} answer={} explain={} est={}s",
            userId, record.getId(), bo.getIds().size(),
            options.getShowAnswer(), options.getShowExplain(), estimated);
        return new ExportSubmitVo(record.getId(), estimated);
    }

    @Override
    public ExportRecordVo getRecord(Long recordId) {
        Long userId = LoginHelper.getUserId();
        if (userId == null || recordId == null) {
            return null;
        }
        BizExportRecord record = exportRecordMapper.selectById(recordId);
        // 越权：非本人记录返 null（控制器转 404/约定错误）
        if (record == null || !userId.equals(record.getUserId())) {
            return null;
        }
        return ExportRecordVo.of(record);
    }

    @Override
    public MisiktPageVo<ExportRecordVo> pageMine(long pageNum, long pageSize) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("未登录不能查看导出记录");
        }
        long pn = pageNum <= 0 ? 1 : pageNum;
        long ps = pageSize <= 0 ? 10 : Math.min(pageSize, 100);

        Page<BizExportRecord> page = new Page<>(pn, ps);
        // 仅本人，按创建时间倒序
        IPage<BizExportRecord> result = exportRecordMapper.selectPage(page,
            new LambdaQueryWrapper<BizExportRecord>()
                .eq(BizExportRecord::getUserId, userId)
                .orderByDesc(BizExportRecord::getCreateTime));

        List<ExportRecordVo> voList = new ArrayList<>();
        for (BizExportRecord e : result.getRecords()) {
            voList.add(ExportRecordVo.of(e));
        }
        // 转 misikt 分页（键名 list）
        Page<ExportRecordVo> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(voList);
        return MisiktPageVo.of(voPage);
    }

    @Override
    public ExportRetryVo retry(Long recordId) {
        Long userId = LoginHelper.getUserId();
        if (userId == null || recordId == null) {
            return null;
        }
        BizExportRecord old = exportRecordMapper.selectById(recordId);
        // 越权：非本人记录返 null
        if (old == null || !userId.equals(old.getUserId())) {
            return null;
        }
        // 并发 1：重试也受约束
        long running = exportRecordMapper.selectCount(new LambdaQueryWrapper<BizExportRecord>()
            .eq(BizExportRecord::getUserId, userId)
            .in(BizExportRecord::getStatus, BizExportRecord.STATUS_QUEUED, BizExportRecord.STATUS_RUNNING));
        if (running > 0) {
            throw new ServiceException("已有导出任务正在进行，请等待完成后再试");
        }

        // 以原 options 新建任务
        Date now = new Date();
        BizExportRecord neo = new BizExportRecord();
        neo.setUserId(userId);
        neo.setPaperId(old.getPaperId());
        neo.setFileName(old.getFileName());
        neo.setOptions(old.getOptions());
        neo.setStatus(BizExportRecord.STATUS_QUEUED);
        neo.setProgress(0);
        neo.setCreateTime(now);
        neo.setUpdateTime(now);
        exportRecordMapper.insert(neo);

        exportPdfWorker.run(neo.getId());
        log.info("[export-retry] user={} oldId={} newId={}", userId, recordId, neo.getId());
        return new ExportRetryVo(neo.getId());
    }

    @Override
    public boolean deleteRecord(Long recordId) {
        Long userId = LoginHelper.getUserId();
        if (userId == null || recordId == null) {
            return false;
        }
        BizExportRecord record = exportRecordMapper.selectById(recordId);
        // 越权 / 不存在：返 false（控制器转 404）
        if (record == null || !userId.equals(record.getUserId())) {
            return false;
        }
        // 进行中任务不许删（Worker 还会回写该行，删了会产生孤儿 OSS 文件）
        if (BizExportRecord.STATUS_QUEUED.equals(record.getStatus())
            || BizExportRecord.STATUS_RUNNING.equals(record.getStatus())) {
            throw new ServiceException("任务进行中，等完成或失败后再删除");
        }
        // 2026-06-11 用户拍板：删除记录时连 OSS 文件一起删（best-effort，OSS 删失败不挡记录删除，留 warn）
        if (record.getFileUrl() != null && !record.getFileUrl().isBlank()) {
            try {
                OssFactory.instance().delete(record.getFileUrl());
            } catch (Exception e) {
                log.warn("[export-delete] OSS 文件删除失败（继续删记录） recordId={} url={} err={}",
                    recordId, record.getFileUrl(), e.getMessage());
            }
        }
        exportRecordMapper.deleteById(recordId);
        log.info("[export-delete] user={} recordId={} fileUrl={}", userId, recordId, record.getFileUrl());
        return true;
    }

    /**
     * 预估秒数：base 2 + n × k × (1 + 含答案0.5 + 含解析0.8)。
     * k = 最近 ROLLING_N 条 done 的 duration_ms/题 滚动均值（秒），冷启动 COLD_K。
     */
    private int estimateSeconds(int n, boolean showAnswer, boolean showExplain) {
        double k = rollingK();
        double factor = 1.0 + (showAnswer ? 0.5 : 0) + (showExplain ? 0.8 : 0);
        double est = 2 + n * k * factor;
        return (int) Math.ceil(est);
    }

    /**
     * 滚动 k：最近 ROLLING_N 条 done 的 (duration_ms/1000) / 题数 平均。无样本则 COLD_K。
     * 题数从 options.ids 长度算（避免再 join paper_question）。
     */
    private double rollingK() {
        try {
            Page<BizExportRecord> page = new Page<>(1, ROLLING_N);
            IPage<BizExportRecord> done = exportRecordMapper.selectPage(page,
                new LambdaQueryWrapper<BizExportRecord>()
                    .eq(BizExportRecord::getStatus, BizExportRecord.STATUS_DONE)
                    .isNotNull(BizExportRecord::getDurationMs)
                    .orderByDesc(BizExportRecord::getCreateTime));
            double sum = 0;
            int cnt = 0;
            for (BizExportRecord r : done.getRecords()) {
                if (r.getDurationMs() == null || r.getDurationMs() <= 0) {
                    continue;
                }
                int qn = questionCount(r.getOptions());
                if (qn <= 0) {
                    continue;
                }
                sum += (r.getDurationMs() / 1000.0) / qn;
                cnt++;
            }
            return cnt == 0 ? COLD_K : sum / cnt;
        } catch (Exception e) {
            log.warn("[export-estimate] 滚动 k 计算失败，用冷启动: {}", e.getMessage());
            return COLD_K;
        }
    }

    private int questionCount(String optionsJson) {
        if (optionsJson == null) {
            return 0;
        }
        try {
            ExportOptions o = objectMapper.readValue(optionsJson, ExportOptions.class);
            return o.getIds() == null ? 0 : o.getIds().size();
        } catch (Exception e) {
            return 0;
        }
    }

    private Long parseLong(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trim255(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 255 ? s.substring(0, 255) : s;
    }
}

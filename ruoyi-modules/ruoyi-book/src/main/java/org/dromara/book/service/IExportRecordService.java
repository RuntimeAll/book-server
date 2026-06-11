package org.dromara.book.service;

import org.dromara.book.domain.bo.ExportPaperBo;
import org.dromara.book.domain.vo.ExportRecordVo;
import org.dromara.book.domain.vo.ExportRetryVo;
import org.dromara.book.domain.vo.ExportSubmitVo;
import org.dromara.book.domain.vo.MisiktPageVo;

/**
 * 试卷导出任务 Service（PRD-A-014）。
 *
 * <p>5 端点支撑：提交任务（单用户并发 1）/ 查详情（越权 404）/ 分页（仅本人）/ 重试（仅本人）
 * / 删除（仅本人，连 OSS 文件）。
 * 落库 + 触发异步 Worker，user_id 一律 LoginHelper 取，绝不信前端。
 *
 * @author backend-dev
 */
public interface IExportRecordService {

    /**
     * 提交导出任务：落 biz_export_record(status=0) + 触发异步 Worker。
     *
     * <p>单用户并发 1：提交前查该 user 是否有 status in ('0','1') 的在途任务，有则抛
     * {@link org.dromara.common.core.exception.ServiceException}（G7）。
     *
     * @param bo 导出入参（paperId? + ids + fileName + showAnswer + showExplain + watermark）
     * @return {recordId, estimatedSeconds}
     */
    ExportSubmitVo submit(ExportPaperBo bo);

    /**
     * 查导出记录详情（越权返 null → 控制器转 404/约定错误）。
     *
     * @param recordId 记录 id
     * @return VO，非本人记录返 null
     */
    ExportRecordVo getRecord(Long recordId);

    /**
     * 分页查当前登录用户的导出记录（仅本人，不信前端身份）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页大小
     * @return misikt 风格分页（键名 list）
     */
    MisiktPageVo<ExportRecordVo> pageMine(long pageNum, long pageSize);

    /**
     * 重试：以原记录的 options 新建一个任务（仅本人，越权返 null）。
     *
     * @param recordId 原记录 id
     * @return {recordId}（新任务），非本人返 null
     */
    ExportRetryVo retry(Long recordId);

    /**
     * 删除导出记录（2026-06-11 去过期改版：用户自管，删除连 OSS 文件一起删 best-effort）。
     *
     * <p>进行中（status 0/1）任务不许删（Worker 还会回写该行）→ 抛 ServiceException。
     *
     * @param recordId 记录 id
     * @return true=已删；false=不存在或非本人（控制器转 404）
     */
    boolean deleteRecord(Long recordId);
}

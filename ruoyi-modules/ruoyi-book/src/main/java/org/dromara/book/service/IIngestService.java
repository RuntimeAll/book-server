package org.dromara.book.service;

import org.dromara.book.domain.bo.IngestAiBo;
import org.dromara.book.domain.bo.IngestBookBo;
import org.dromara.book.domain.bo.IngestKgContentBo;
import org.dromara.book.domain.bo.IngestKgTreeBo;
import org.dromara.book.domain.bo.IngestPatternBo;
import org.dromara.book.domain.bo.IngestQuestionBo;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 录入接口 Service（PRD-C-204 B1）—— 走接口本地录入，4 组 7 端点。
 *
 * <p>独立于现有 QuestionService CRUD，隔离 C-203 回归；全部 upsert 语义可重跑。
 * 录入者/teacher_id 由 Controller 注入（LoginHelper.getUserId），不信 body。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
public interface IIngestService {

    /** 组1：整树 upsert biz_subject，返回 {upserted:n}。 */
    Map<String, Object> upsertKgTree(IngestKgTreeBo bo);

    /** 组1：知识点内容块 upsert biz_subject_content，返回 {subjectId, blocks:n}。 */
    Map<String, Object> upsertKgContent(IngestKgContentBo bo);

    /** 组2：上传图 multipart 分支 → OSS + 去重 image_asset，返回 {assetId, ossUrl, dedup}。 */
    Map<String, Object> uploadImage(MultipartFile file, String assetKind);

    /** 组2：上传图本地路径分支 → OSS + 去重 image_asset，返回 {assetId, ossUrl, dedup}。 */
    Map<String, Object> uploadImageByLocalPath(String localPath, String assetKind);

    /** 组3：upsert 教辅 + 教辅树，返回 {bookId, bookSubjectNodes:n}。 */
    Map<String, Object> upsertBook(IngestBookBo bo);

    /** 组3：录一道题（事务多表），externalKey 幂等，返回 {questionId, created}。 */
    Map<String, Object> ingestQuestion(IngestQuestionBo bo, Long teacherId);

    /** 组4：upsert biz_question_ai（DNA），回写 biz_question 打标元，返回 {aiId}。 */
    Map<String, Object> upsertAi(Long questionId, IngestAiBo bo, Long teacherId);

    /**
     * PRD-C-204：upsert 题型目录 biz_question_pattern。
     * 幂等键 = (anchorSubjectId, name)，命中则更新返回旧 id，否则新建。
     * 返回 {patternId, created}。
     */
    Map<String, Object> upsertPattern(IngestPatternBo bo);
}

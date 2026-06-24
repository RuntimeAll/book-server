package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.IngestAiBo;
import org.dromara.book.domain.bo.IngestBookBo;
import org.dromara.book.domain.bo.IngestImageBo;
import org.dromara.book.domain.bo.IngestKgContentBo;
import org.dromara.book.domain.bo.IngestKgTreeBo;
import org.dromara.book.domain.bo.IngestPatternBo;
import org.dromara.book.domain.bo.IngestQuestionBo;
import org.dromara.book.service.IIngestService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 题库录入接口 Controller（PRD-C-204 B1）—— 走接口本地录入，4 组 7 端点。
 *
 * <p>前缀 {@code /teacher/ingest/**}，命中 {@link MisiktEnvelopeAdvice} 自动包
 * {@code {code:1, message:"成功", response:<T>}}。本地脚本双头鉴权（Authorization: Bearer + clientid）。
 *
 * <p>🔴 独立于现有 {@code QuestionController} CRUD，隔离 C-203 回归；teacher_id 由
 * {@link LoginHelper#getUserId()} 注入，绝不信 body。全部端点 {@code @SaCheckLogin}。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@RestController
@RequestMapping("/teacher/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final IIngestService ingestService;

    // ===== 组1 KG =====

    /** POST /teacher/ingest/kg/tree — 批量 upsert biz_subject 节点 → {upserted:n}。 */
    @SaCheckLogin
    @PostMapping("/kg/tree")
    public Map<String, Object> kgTree(@RequestBody IngestKgTreeBo bo) {
        return ingestService.upsertKgTree(bo);
    }

    /** POST /teacher/ingest/kg/content — upsert biz_subject_content → {subjectId, blocks:n}。 */
    @SaCheckLogin
    @PostMapping("/kg/content")
    public Map<String, Object> kgContent(@RequestBody IngestKgContentBo bo) {
        return ingestService.upsertKgContent(bo);
    }

    // ===== 组2 图资产 =====

    /**
     * POST /teacher/ingest/image（multipart 分支）— 上传一张图 → OSS + 去重 image_asset。
     * 返回 {assetId, ossUrl, dedup}。
     */
    @SaCheckLogin
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> imageMultipart(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "assetKind", required = false) String assetKind) {
        return ingestService.uploadImage(file, assetKind);
    }

    /**
     * POST /teacher/ingest/image（JSON {localPath} 分支）— 本地磁盘文件直传 → OSS + 去重。
     * 返回 {assetId, ossUrl, dedup}。
     */
    @SaCheckLogin
    @PostMapping(value = "/image", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> imageLocalPath(@RequestBody IngestImageBo bo) {
        return ingestService.uploadImageByLocalPath(bo.getLocalPath(), bo.getAssetKind());
    }

    // ===== 组3 教辅 + 录题 =====

    /** POST /teacher/ingest/book — upsert biz_book + 教辅树 → {bookId, bookSubjectNodes:n}。 */
    @SaCheckLogin
    @PostMapping("/book")
    public Map<String, Object> book(@RequestBody IngestBookBo bo) {
        return ingestService.upsertBook(bo);
    }

    /** POST /teacher/ingest/question — 录一道题（事务多表）→ {questionId, created}。 */
    @SaCheckLogin
    @PostMapping("/question")
    public Map<String, Object> question(@RequestBody IngestQuestionBo bo) {
        Long teacherId = LoginHelper.getUserId();
        return ingestService.ingestQuestion(bo, teacherId);
    }

    /**
     * POST /teacher/ingest/pattern — upsert 题型目录（PRD-C-204）。
     * 幂等：按 (anchorSubjectId,name) 命中则更新返回旧 id，否则新建 → {patternId, created}。
     */
    @SaCheckLogin
    @PostMapping("/pattern")
    public Map<String, Object> pattern(@RequestBody IngestPatternBo bo) {
        return ingestService.upsertPattern(bo);
    }

    // ===== 组4 AI 打标 =====

    /** POST /teacher/ingest/ai/{questionId} — upsert biz_question_ai（DNA）→ {aiId}。 */
    @SaCheckLogin
    @PostMapping("/ai/{questionId}")
    public Map<String, Object> ai(@PathVariable("questionId") Long questionId,
                                  @RequestBody IngestAiBo bo) {
        Long teacherId = LoginHelper.getUserId();
        return ingestService.upsertAi(questionId, bo, teacherId);
    }
}

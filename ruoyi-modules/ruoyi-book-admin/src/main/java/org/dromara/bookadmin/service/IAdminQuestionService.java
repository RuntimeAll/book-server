package org.dromara.bookadmin.service;

import org.dromara.book.domain.bo.SubjectLazyTreeBo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.domain.vo.SubjectNodeVo;
import org.dromara.bookadmin.domain.bo.AdminQuestionPageBo;

import java.util.List;
import java.util.Map;

public interface IAdminQuestionService {

    /**
     * 分页查询题目（admin 通道，方法内部走 Mapper 自查，不调教师端 service）。
     *
     * <p>H1 卡 Bug2 补丁：签名从 {@code QuestionPageBo} → {@link AdminQuestionPageBo}，
     * 新增字段 {@code tagIds} 支持多选标签 OR 语义筛选。共有字段（subjectId / questionType /
     * difficult / keyWord / 等）通过继承沿用，behavior 不变。
     *
     * @param bo 分页 + 筛选入参（misikt 风格 pageIndex / keyWord / difficult / + admin 独有 tagIds）
     * @return misikt 风格分页 VO
     */
    MisiktPageVo<QuestionItemVo> adminPage(AdminQuestionPageBo bo);

    /**
     * 章节-知识点树懒加载（admin 通道，方法内部走 {@code BizSubjectMapper} 自查，
     * <strong>不</strong>调教师端 {@code ISubjectService.lazyTree}）。
     *
     * <p>V0.1 简化（与教师端等效）：一次性 SELECT * FROM biz_subject + 内存建树，
     * 忽略 {@code bo.parentId} 永远返整树（misikt 抓包真实行为）。
     *
     * <p>BUG-1 数据修复保留：DB 仍有少量 "节点 XXXX" 占位名（W-6 已大幅清洗，
     * 但兜底逻辑保留以防新增数据漏网），admin 端按 level 兜底前缀。
     *
     * @param bo 懒加载入参（parentId 当前忽略）
     * @return 嵌套树（学段 → 学科 → 教材 → 章节 → 知识点）
     */
    List<SubjectNodeVo> adminLazyTree(SubjectLazyTreeBo bo);

    /**
     * 题目详情查询（V-5 — 编辑回填用）。
     *
     * <p>与教师端 {@code QuestionServiceImpl#selectById} 当前等效（同 mapper SQL + 同回填策略），
     * 但独立维护 — admin 后续可解除 status='1' 限制 / 加创建人过滤等。
     *
     * <p>回填：
     * <ul>
     *   <li>{@code questionKnowledges}（source='U'，用户标注知识点）</li>
     *   <li>{@code questionStdKnowledges}（source='S'，标准库知识点）</li>
     *   <li>{@code freeTags}（biz_question_free_tag JOIN biz_free_tag）</li>
     * </ul>
     *
     * <p>鉴权：Controller 层 {@code @SaCheckPermission("admin:question:list")}（编辑回填属 list 范围；
     * 后续 V-1 edit 端点才挂 admin:question:edit 写权限）。
     *
     * @param id 题目 ID
     * @return 详情 VO，未找到时返 {@code null}
     */
    QuestionDetailVo adminSelectById(Long id);

    /**
     * 题目软删（V-2 — 状态 0/1 → 2，含引用校验）。
     *
     * <p>业务规则：
     * <ol>
     *   <li>查 biz_paper_question 引用次数 N</li>
     *   <li>N &gt; 0 → 抛 {@code ServiceException("该题被 N 张试卷引用，无法删除")}</li>
     *   <li>N == 0 → {@code UPDATE biz_question SET status='2', update_time=NOW() WHERE id=?}</li>
     * </ol>
     *
     * <p>软删<strong>不动</strong> biz_question_knowledge / biz_question_free_tag —
     * 历史试卷里仍能渲染知识点 tag（PRD §3.2）。
     *
     * <p>事务：{@code @Transactional(rollbackFor = Exception.class)}（虽只单表 UPDATE，
     * 但未来扩展可能加联动写，保留事务边界）。
     *
     * @param id 题目 ID
     */
    void adminSoftDelete(Long id);

    /**
     * 题目发布（V-3 — 状态机 0 → 1）。
     *
     * <p>SQL：{@code UPDATE biz_question SET status='1', update_time=NOW() WHERE id=? AND status='0'}。
     *
     * <p>影响行数 0 → 抛 {@code ServiceException("状态非法（当前不是草稿，不能发布）")}。
     *
     * <p>不允许反向：'1' → '0' / '2' → 任何（PRD §3.8）。
     *
     * @param id 题目 ID
     */
    void adminPublish(Long id);

    /**
     * 题目新建 / 编辑统一入口（V-1 + V-6 — H1 卡段② BE 波 2b）。
     *
     * <p>分支：
     * <ul>
     *   <li>{@code bo.id == null} → 新建：INSERT biz_question (status='0' 草稿)
     *       + INSERT biz_question_knowledge (source='U')
     *       + INSERT biz_question_free_tag + biz_free_tag 字典（自动建）</li>
     *   <li>{@code bo.id != null} → 编辑：UPDATE biz_question (status 不动，要发布走 publish)
     *       + 全量替换 biz_question_knowledge (source='U')
     *       + 全量替换 biz_question_free_tag</li>
     * </ul>
     *
     * <p>同一事务边界（{@code @Transactional(rollbackFor = Exception.class)}）：
     * 任意 step 抛 {@code ServiceException} 整体回滚。
     *
     * <p>校验（PRD §3.1 + §6 R7）：
     * <ul>
     *   <li>questionType ∈ {1,2,3,4,5} / difficult ∈ {1..4} / subjectId 存在</li>
     *   <li>选择题 (type=1)：optionsJson ≥ 2 + correctAnswer ∈ keys</li>
     *   <li>questionKnowledges.size ≥ 1，每个 knowledgeId 在 biz_subject 存在</li>
     *   <li>stemText 与 stemImgUrl 至少有一个非空（PRD §6 R7）</li>
     * </ul>
     *
     * <p>knowledge source 强制 'U'：防 FE 误传 'S' 污染标准库知识点（PRD §3.5）。
     *
     * @param bo 入参（id 可空 = 新建 / 非空 = 编辑）
     * @return 题目 ID（新建为新自增 / 编辑为原 id）
     */
    Long adminEdit(org.dromara.bookadmin.domain.bo.AdminQuestionEditBo bo);

    /**
     * admin 图上传 (V-4 — H1 卡段② BE 波 2c)。
     *
     * <p>multipart/form-data 上传 PNG / JPG / JPEG / WEBP 图片到 OSS，同时写
     * {@code image_asset} 表追踪。返回 {@code {url, assetId}}。
     *
     * <p>OSS 路径：{@code admin-upload/<YYYY-MM-dd>/<uuid>.<ext>}（手动构造 key，
     * 绕开 {@link org.dromara.common.oss.core.OssClient#uploadSuffix} 默认走 properties.getPrefix() 的逻辑）。
     *
     * <p>校验：
     * <ul>
     *   <li>file 非空 → 否则 {@code ServiceException("上传文件不能为空")}</li>
     *   <li>size ≤ 5MB → 否则 {@code ServiceException("文件大小不能超过 5MB")}</li>
     *   <li>后缀 ∈ {".png", ".jpg", ".jpeg", ".webp"}（小写比较）→ 否则
     *       {@code ServiceException("仅支持 png/jpg/jpeg/webp 格式")}</li>
     *   <li>type ∈ {"stem", "answer", "explain", null}（null 默认 "stem"）</li>
     * </ul>
     *
     * <p>image_asset INSERT（字段与现有数据语义对齐）：
     * <ul>
     *   <li>{@code entity_type='question'} / {@code asset_kind=type}（默认 stem）</li>
     *   <li>{@code entity_ref='admin'}（标识来源 — 表无 source 字段，复用 entity_ref）</li>
     *   <li>{@code src_url='admin-upload://<oss-key>'}（NOT NULL 兜底，标识 admin 直传）</li>
     *   <li>{@code url_hash=sha256(src_url)}（NOT NULL UNIQUE，char(64) 正好 SHA-256 hex 长度）</li>
     *   <li>{@code rel_path=oss-key}（NOT NULL，复用 OSS key 作 rel 标识）</li>
     *   <li>{@code local_path=''}（NOT NULL 但 admin 直传无本地路径，给空串）</li>
     *   <li>{@code status='ok'} / {@code oss_url=...} / {@code oss_uploaded_ts=NOW()}</li>
     *   <li>{@code file_size} / {@code content_type} / {@code ext}（不带点）</li>
     * </ul>
     *
     * <p>事务：{@code @Transactional(rollbackFor = Exception.class)}，主要保护
     * image_asset INSERT 失败时不返成功 url。OSS 上传已成功但 image_asset 写失败时
     * OSS 文件会留着（业务可接受 — 后续 cron 清理）。
     *
     * <p>鉴权：Controller 层 {@code @SaCheckPermission("admin:question:edit")}（写权限）。
     *
     * @param file multipart 文件（必填）
     * @param type 图片用途 stem / answer / explain（可选，默认 stem）
     * @return {@code {"url": "https://...", "assetId": 108573}}
     */
    java.util.Map<String, Object> adminUploadFile(org.springframework.web.multipart.MultipartFile file, String type);


    /**
     * admin 端 freeTag 字典搜索 (H1 卡 Bug2 补丁 — 列表页 tag 多选筛选 + 编辑页搜索式 multi-select 共用候选源)。
     *
     * <p>SQL 行为（详见 {@link org.dromara.bookadmin.mapper.AdminFreeTagWriteMapper#selectListByKeyword(String, int)}）：
     * <ul>
     *   <li>keyword null / 空（trim 后）→ 返热门 top {@code limit}（按 use_count 倒序）</li>
     *   <li>keyword 非空 → LIKE '%keyword%' 模糊匹配，再按 use_count 倒序</li>
     *   <li>同热度按 id 倒序兜底稳定排序</li>
     * </ul>
     *
     * <p>limit 兜底规则：
     * <ul>
     *   <li>null / 0 / 负数 → 默认 20</li>
     *   <li>大于 100 → clamp 到 100（防 FE 误传巨值打 DB）</li>
     * </ul>
     *
     * <p>返结构：{@code [{id: Long, name: String, useCount: Integer}, ...]}
     * — 字段已驼峰，FE 直接消费。
     *
     * @param keyword 关键字（null / 空 / 纯空格 → trim 后等效"返热门"）
     * @param limit   返回上限（null / 非正 → 默认 20；&gt; 100 → clamp 到 100）
     * @return tag 候选列表（按 use_count 倒序）
     */
    List<Map<String, Object>> adminFreeTagSearch(String keyword, Integer limit);

}

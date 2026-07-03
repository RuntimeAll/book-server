package org.dromara.book.service;

import java.util.List;
import java.util.Map;

/**
 * 讲义（片段汇聚）Service（PRD-C-207）。
 *
 * <p>权限模型正本 = only-one/权限与内容归属模型-定版.md（v2，2026-07-03）：
 * owner=创建者 uid，官方=uid1 仅本人可改；可见=官方+本部门全员互见+我（草稿仅本人）；
 * 写校验序 = owner 本人 ∥ org_admin 同部门 ∥ superadmin。
 *
 * @author codeplace-C PRD-C-207
 */
public interface IKgLectureService {

    /**
     * 取某 KG 节点（任意层）的完整讲义 = 片段按树序汇聚成一份 Tiptap doc。
     *
     * @param subjectId 挂载锚 KG 节点 id（课时 L4 常用）
     * @param bookId    教辅套 id，空默认 CC7S
     * @param owner     指定讲义源（来源切换器选中，官方兜底）；null=默认视图（我的>本部门管理员>官方）
     * @param viewerId  当前登录者 uid；null=匿名（只见官方）
     */
    Map<String, Object> getLecture(String subjectId, String bookId, Long owner, Long viewerId);

    /** 讲义目录：册内每课时的可见讲义源（官方+本部门全员+我；草稿仅本人）。 */
    Map<String, Object> getCatalog(String bookId, Long viewerId);

    /**
     * 批量保存片段（upsert）。targetOwner=null 存自己名下；否则按写校验序判权（管理员改部门成员的）。
     */
    Map<String, Object> saveFrags(String bookId, Long targetOwner, List<Map<String, Object>> frags, long loginId);

    /** 删除某 owner 某书某前缀的片段（管理页 下架/删；同写校验序）。 */
    Map<String, Object> removeFrags(String bookId, long owner, String subjectPrefix, long loginId);

    /** 管理页：成员列表（superadmin=全部/org_admin=本部门；其余 403）。 */
    List<Map<String, Object>> getMembers(long loginId);

    /** 管理页：讲义清单（owner×课时聚合，范围同上）。 */
    List<Map<String, Object>> getManageLectures(long loginId);
}

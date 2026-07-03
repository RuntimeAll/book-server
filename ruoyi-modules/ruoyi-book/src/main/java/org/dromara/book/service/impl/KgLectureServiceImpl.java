package org.dromara.book.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.book.mapper.KgLectureFragMapper;
import org.dromara.book.service.IKgLectureService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 讲义（片段汇聚）Service 实现（PRD-C-207，权限模型 v2 = only-one/权限与内容归属模型-定版.md）。
 *
 * @author codeplace-C PRD-C-207
 */
@Service
@RequiredArgsConstructor
public class KgLectureServiceImpl implements IKgLectureService {

    private static final String DEFAULT_BOOK = "CC7S";
    /** 官方讲义 = 超管账号（权限模型 §1：官方=uid1 的版本，仅其本人可改） */
    private static final long OFFICIAL_OWNER = 1L;
    private static final String ROLE_SUPER = "superadmin";
    private static final String ROLE_ORG_ADMIN = "org_admin";

    private final KgLectureFragMapper fragMapper;

    // ── 可见性（权限模型 §3：官方 + 本部门全员互见 + 我） ───────────────

    private Set<Long> visibleOwners(Long viewerId) {
        Set<Long> owners = new LinkedHashSet<>();
        owners.add(OFFICIAL_OWNER);
        if (viewerId != null) {
            owners.addAll(fragMapper.selectMyDeptMemberIds(viewerId));
            owners.add(viewerId);
        }
        return owners;
    }

    // ── 写校验（权限模型 §4：owner 本人 ∥ org_admin 同部门 ∥ superadmin；官方仅本人） ──

    private void checkWritable(long loginId, long targetOwner) {
        if (targetOwner == OFFICIAL_OWNER) {
            if (loginId != OFFICIAL_OWNER) {
                throw new ServiceException("官方讲义仅超管账号本人可改", 403);
            }
            return;
        }
        if (loginId == targetOwner) {
            return;
        }
        List<String> roles = fragMapper.selectRoleKeys(loginId);
        if (roles.contains(ROLE_SUPER)) {
            return;
        }
        if (roles.contains(ROLE_ORG_ADMIN) && fragMapper.countSameDept(loginId, targetOwner) > 0) {
            return;
        }
        throw new ServiceException("无权限操作该用户的讲义（仅本人/同部门机构管理员/超管）", 403);
    }

    @Override
    public Map<String, Object> getLecture(String subjectId, String bookId, Long owner, Long viewerId) {
        String book = StringUtils.isBlank(bookId) ? DEFAULT_BOOK : bookId;
        Map<String, Object> out = new HashMap<>();
        out.put("node", fragMapper.selectNode(subjectId));
        out.put("bookId", book);
        out.put("owner", owner);

        Set<Long> visible = visibleOwners(viewerId);
        // 指定源：该源 + 官方兜底；默认视图：我的 > 本部门管理员 > 官方（同事的不进默认视图，只作可选源）
        Set<Long> fetchOwners = new LinkedHashSet<>();
        Set<Long> orgAdmins = new LinkedHashSet<>();
        if (owner != null && visible.contains(owner)) {
            fetchOwners.add(owner);
            fetchOwners.add(OFFICIAL_OWNER);
        } else {
            owner = null; // 不可见的源退回默认视图
            fetchOwners.add(OFFICIAL_OWNER);
            if (viewerId != null) {
                orgAdmins.addAll(fragMapper.selectMyOrgAdminIds(viewerId));
                fetchOwners.addAll(orgAdmins);
                fetchOwners.add(viewerId);
            }
        }

        List<Map<String, Object>> rows = fragMapper.selectFragsByPrefixOwners(book, subjectId, fetchOwners);
        Map<String, Map<String, Object>> best = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            long o = ((Number) r.get("ownerId")).longValue();
            boolean draft = "1".equals(String.valueOf(r.get("status")));
            if (draft && (viewerId == null || o != viewerId)) {
                continue;
            }
            String sid = (String) r.get("subjectId");
            int score = score(o, owner, viewerId, orgAdmins);
            Map<String, Object> cur = best.get(sid);
            if (cur == null || score > score(((Number) cur.get("ownerId")).longValue(), owner, viewerId, orgAdmins)) {
                best.put(sid, r);
            }
        }
        out.put("docJson", assemble(new ArrayList<>(best.values())));
        return out;
    }

    /** 覆盖序打分：指定源=9 > 我的=3 > 本部门管理员=2 > 官方=1。 */
    private int score(long o, Long requested, Long viewerId, Set<Long> orgAdmins) {
        if (requested != null) {
            return o == requested ? 9 : 1;
        }
        if (viewerId != null && o == viewerId) {
            return 3;
        }
        if (orgAdmins.contains(o)) {
            return 2;
        }
        return o == OFFICIAL_OWNER ? 1 : 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> assemble(List<Map<String, Object>> frags) {
        if (frags == null || frags.isEmpty()) {
            return null;
        }
        List<Object> content = new ArrayList<>();
        for (Map<String, Object> f : frags) {
            String cj = (String) f.get("contentJson");
            if (StringUtils.isBlank(cj)) {
                continue;
            }
            Map<String, Object> frag = JsonUtils.parseObject(cj, Map.class);
            if (frag == null) {
                continue;
            }
            Object nodes = frag.get("content");
            if (nodes instanceof List) {
                content.addAll((List<Object>) nodes);
            }
        }
        Map<String, Object> doc = new HashMap<>();
        doc.put("type", "doc");
        doc.put("content", content);
        return doc;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCatalog(String bookId, Long viewerId) {
        String book = StringUtils.isBlank(bookId) ? DEFAULT_BOOK : bookId;
        Map<String, Object> out = new HashMap<>();
        String volumeId = fragMapper.selectVolumeOfBook(book);
        out.put("volumeId", volumeId);
        out.put("viewer", viewerId);
        List<Map<String, Object>> lessons = new ArrayList<>();
        if (StringUtils.isNotBlank(volumeId)) {
            Set<Long> visible = visibleOwners(viewerId);
            long viewer = viewerId == null ? -1L : viewerId;
            List<Map<String, Object>> rows = fragMapper.selectLessonSources(volumeId, visible, viewer);
            Set<Long> ownerIds = new LinkedHashSet<>();
            for (Map<String, Object> r : rows) {
                ownerIds.add(((Number) r.get("owner")).longValue());
            }
            Map<Long, String> names = new HashMap<>();
            if (!ownerIds.isEmpty()) {
                for (Map<String, Object> u : fragMapper.selectUserNames(ownerIds)) {
                    names.put(((Number) u.get("userId")).longValue(), (String) u.get("nickName"));
                }
            }
            Map<String, Map<String, Object>> byLesson = new LinkedHashMap<>();
            for (Map<String, Object> r : rows) {
                String lessonId = (String) r.get("lessonId");
                Map<String, Object> lesson = byLesson.computeIfAbsent(lessonId, k -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("lessonId", lessonId);
                    m.put("lessonName", r.get("lessonName"));
                    m.put("sources", new ArrayList<Map<String, Object>>());
                    return m;
                });
                long o = ((Number) r.get("owner")).longValue();
                Map<String, Object> src = new HashMap<>();
                src.put("bookId", r.get("bookId"));
                src.put("bookName", r.get("bookName"));
                src.put("owner", o);
                src.put("ownerName", names.getOrDefault(o, String.valueOf(o)));
                ((List<Map<String, Object>>) lesson.get("sources")).add(src);
            }
            lessons.addAll(byLesson.values());
        }
        out.put("lessons", lessons);
        return out;
    }

    @Override
    public Map<String, Object> saveFrags(String bookId, Long targetOwner, List<Map<String, Object>> frags, long loginId) {
        String book = StringUtils.isBlank(bookId) ? DEFAULT_BOOK : bookId;
        long owner = targetOwner == null ? loginId : targetOwner;
        checkWritable(loginId, owner);
        List<Map<String, Object>> results = new ArrayList<>();
        int ok = 0;
        int fail = 0;
        for (Map<String, Object> f : frags) {
            String subjectId = str(f.get("subjectId"));
            Map<String, Object> r = new HashMap<>();
            r.put("subjectId", subjectId);
            try {
                if (StringUtils.isBlank(subjectId) || fragMapper.selectNode(subjectId) == null) {
                    throw new IllegalArgumentException("subjectId 不存在于 KG: " + subjectId);
                }
                Object cj = f.get("contentJson");
                String contentJson = cj == null ? null
                    : (cj instanceof String ? (String) cj : JsonUtils.toJsonString(cj));
                String status = "1".equals(str(f.get("status"))) ? "1" : "0";
                int n = fragMapper.upsertFrag(IdUtil.getSnowflakeNextId(), subjectId, subjectId.length() / 3,
                    book, owner, str(f.get("title")), contentJson, extractText(contentJson),
                    status, String.valueOf(loginId));
                r.put("action", n > 1 ? "updated" : "created"); // ON DUPLICATE: 1=insert, 2=update
                ok++;
            } catch (Exception e) {
                r.put("action", "fail");
                r.put("reason", e.getMessage());
                fail++;
            }
            results.add(r);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("ok", fail == 0);
        out.put("owner", owner);
        out.put("results", results);
        out.put("stats", Map.of("ok", ok, "fail", fail));
        return out;
    }

    @Override
    public Map<String, Object> removeFrags(String bookId, long owner, String subjectPrefix, long loginId) {
        checkWritable(loginId, owner);
        if (StringUtils.isBlank(subjectPrefix) || subjectPrefix.length() < 9) {
            throw new ServiceException("subjectPrefix 至少到节(9位)，防误删整册", 400);
        }
        String book = StringUtils.isBlank(bookId) ? DEFAULT_BOOK : bookId;
        int n = fragMapper.deleteFrags(book, owner, subjectPrefix);
        Map<String, Object> out = new HashMap<>();
        out.put("ok", true);
        out.put("removed", n);
        return out;
    }

    // ── 管理页（权限模型 §6） ─────────────────────────────────────────

    /** superadmin=全部；org_admin=本部门；其余 403。返回 null 表示全部（超管）。 */
    private Long manageScopeDept(long loginId) {
        List<String> roles = fragMapper.selectRoleKeys(loginId);
        if (roles.contains(ROLE_SUPER)) {
            return null;
        }
        if (roles.contains(ROLE_ORG_ADMIN)) {
            Long dept = fragMapper.selectDeptId(loginId);
            if (dept != null) {
                return dept;
            }
        }
        throw new ServiceException("仅超管/机构管理员可访问管理页", 403);
    }

    @Override
    public List<Map<String, Object>> getMembers(long loginId) {
        return fragMapper.selectMembers(manageScopeDept(loginId));
    }

    @Override
    public List<Map<String, Object>> getManageLectures(long loginId) {
        Long dept = manageScopeDept(loginId);
        List<Long> owners = dept == null ? null : fragMapper.selectMyDeptMemberIds(loginId);
        List<Map<String, Object>> rows = fragMapper.selectManageLectures(owners);
        // 补 owner 显示名
        Set<Long> ownerIds = new LinkedHashSet<>();
        for (Map<String, Object> r : rows) {
            ownerIds.add(((Number) r.get("owner")).longValue());
        }
        Map<Long, String> names = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            for (Map<String, Object> u : fragMapper.selectUserNames(ownerIds)) {
                names.put(((Number) u.get("userId")).longValue(), (String) u.get("nickName"));
            }
        }
        for (Map<String, Object> r : rows) {
            r.put("ownerName", names.getOrDefault(((Number) r.get("owner")).longValue(), ""));
        }
        return rows;
    }

    /** 从 Tiptap JSON 抽纯文本（stem_text 镜像）。 */
    @SuppressWarnings("unchecked")
    private String extractText(String contentJson) {
        if (StringUtils.isBlank(contentJson)) {
            return null;
        }
        Map<String, Object> doc = JsonUtils.parseObject(contentJson, Map.class);
        if (doc == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        walkText(doc, sb);
        return sb.length() > 60000 ? sb.substring(0, 60000) : sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void walkText(Map<String, Object> node, StringBuilder sb) {
        Object text = node.get("text");
        if (text instanceof String) {
            sb.append((String) text);
        }
        Object content = node.get("content");
        if (content instanceof List) {
            for (Object c : (List<Object>) content) {
                if (c instanceof Map) {
                    walkText((Map<String, Object>) c, sb);
                }
            }
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}

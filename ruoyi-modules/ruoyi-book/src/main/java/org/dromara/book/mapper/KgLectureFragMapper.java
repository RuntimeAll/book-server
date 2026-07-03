package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 讲义片段 只读 Mapper（PRD-C-207）。
 *
 * <p>讲义 = 挂 KG 节点（biz_subject）的原子教学片段（表 biz_kg_lecture_frag）。
 * 某层完整讲义 = {@code WHERE subject_id LIKE '<前缀>%' ORDER BY subject_id} 汇聚（前缀零填充=树序，免递归）。
 * 一节点多份 = book_id（教辅套）× owner_id（0官方/个人）。P1 只读官方（owner_id=0）。
 * biz_* 无 tenant_id，关多租户拦截器。
 *
 * @author codeplace-C PRD-C-207
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface KgLectureFragMapper {

    /** KG 节点 {id,name,level}（挂载锚，任意层）。 */
    @Select("SELECT id, name, level FROM biz_subject WHERE id = #{subjectId}")
    Map<String, Object> selectNode(@Param("subjectId") String subjectId);

    /**
     * 取某前缀下、某书、可见 owner 集内的全部片段，按 subject_id 树序。
     * 返回 {subjectId, kgLevel, ownerId, status, contentJson}；覆盖序（我的>机构>官方）在 service 层裁决。
     */
    @Select("<script>" +
        "SELECT subject_id AS subjectId, kg_level AS kgLevel, owner_id AS ownerId, status, content_json AS contentJson " +
        "FROM biz_kg_lecture_frag " +
        "WHERE book_id = #{bookId} AND subject_id LIKE CONCAT(#{subjectPrefix}, '%') " +
        "AND owner_id IN <foreach item='o' collection='owners' open='(' separator=',' close=')'>#{o}</foreach> " +
        "ORDER BY subject_id" +
        "</script>")
    List<Map<String, Object>> selectFragsByPrefixOwners(@Param("bookId") String bookId,
                                                        @Param("subjectPrefix") String subjectPrefix,
                                                        @Param("owners") Collection<Long> owners);

    /** 某书片段所在的册前缀（LEFT 3 位=册根 id）。 */
    @Select("SELECT DISTINCT SUBSTRING(subject_id, 1, 3) FROM biz_kg_lecture_frag WHERE book_id = #{bookId} LIMIT 1")
    String selectVolumeOfBook(@Param("bookId") String bookId);

    /**
     * 册内每课时(L4)挂了哪些讲义源（限可见 owner 集；草稿只对本人可见）。
     * 返回 {lessonId, lessonName, bookId, bookName, owner}。SUBSTRING(,1,12) 把 L4/L5 片段收敛到所属课时。
     */
    @Select("<script>" +
        "SELECT DISTINCT SUBSTRING(f.subject_id, 1, 12) AS lessonId, s.name AS lessonName, " +
        "       f.book_id AS bookId, b.series AS bookName, f.owner_id AS owner " +
        "FROM biz_kg_lecture_frag f " +
        "LEFT JOIN biz_subject s ON s.id = SUBSTRING(f.subject_id, 1, 12) " +
        "LEFT JOIN biz_book b ON b.id = f.book_id " +
        "WHERE f.subject_id LIKE CONCAT(#{volumePrefix}, '%') " +
        "AND f.owner_id IN <foreach item='o' collection='owners' open='(' separator=',' close=')'>#{o}</foreach> " +
        "AND (f.status = '0' OR f.owner_id = #{viewerId}) " +
        "ORDER BY lessonId, owner" +
        "</script>")
    List<Map<String, Object>> selectLessonSources(@Param("volumePrefix") String volumePrefix,
                                                  @Param("owners") Collection<Long> owners,
                                                  @Param("viewerId") long viewerId);

    /** 我所在部门（sys_dept）的机构管理员 uid 们（默认视图覆盖序用：我的>本部门管理员>官方）。 */
    @Select("SELECT ur.user_id FROM sys_user_role ur " +
        "JOIN sys_role r ON r.role_id = ur.role_id AND r.role_key = 'org_admin' AND r.del_flag = '0' " +
        "JOIN sys_user u ON u.user_id = ur.user_id AND u.del_flag = '0' " +
        "WHERE u.dept_id = (SELECT dept_id FROM sys_user WHERE user_id = #{userId})")
    List<Long> selectMyOrgAdminIds(@Param("userId") long userId);

    /** 本部门全体成员 uid（可见圈=部门互见，权限模型 §3）。 */
    @Select("SELECT user_id FROM sys_user WHERE del_flag = '0' " +
        "AND dept_id = (SELECT dept_id FROM sys_user WHERE user_id = #{userId})")
    List<Long> selectMyDeptMemberIds(@Param("userId") long userId);

    /** 登录者角色 key 集（写校验：superadmin / org_admin 判定）。 */
    @Select("SELECT r.role_key FROM sys_user_role ur " +
        "JOIN sys_role r ON r.role_id = ur.role_id AND r.del_flag = '0' WHERE ur.user_id = #{userId}")
    List<String> selectRoleKeys(@Param("userId") long userId);

    /** 两用户是否同部门（org_admin 管理范围判定）。 */
    @Select("SELECT COUNT(*) FROM sys_user a JOIN sys_user b ON a.dept_id = b.dept_id " +
        "WHERE a.user_id = #{a} AND b.user_id = #{b} AND a.del_flag = '0' AND b.del_flag = '0'")
    int countSameDept(@Param("a") long a, @Param("b") long b);

    /** 管理页：讲义清单（owner×课时聚合；owners=null 时超管看全部）。 */
    @Select("<script>" +
        "SELECT SUBSTRING(f.subject_id, 1, 12) AS lessonId, s.name AS lessonName, f.book_id AS bookId, " +
        "       f.owner_id AS owner, COUNT(*) AS fragCount, MAX(f.update_time) AS updatedAt " +
        "FROM biz_kg_lecture_frag f LEFT JOIN biz_subject s ON s.id = SUBSTRING(f.subject_id, 1, 12) " +
        "<where><if test='owners != null'>f.owner_id IN " +
        "<foreach item='o' collection='owners' open='(' separator=',' close=')'>#{o}</foreach></if></where> " +
        "GROUP BY lessonId, lessonName, bookId, owner ORDER BY owner, lessonId" +
        "</script>")
    List<Map<String, Object>> selectManageLectures(@Param("owners") Collection<Long> owners);

    /** 删除某 owner 某书某前缀的全部片段（管理页 下架/删；权限在 service 校验）。 */
    @Update("DELETE FROM biz_kg_lecture_frag WHERE book_id = #{bookId} AND owner_id = #{owner} " +
        "AND subject_id LIKE CONCAT(#{prefix}, '%')")
    int deleteFrags(@Param("bookId") String bookId, @Param("owner") long owner, @Param("prefix") String prefix);

    /** 管理页：成员列表（deptId=null 超管看全部）。 */
    @Select("<script>" +
        "SELECT u.user_id AS userId, u.user_name AS userName, u.nick_name AS nickName, " +
        "       u.dept_id AS deptId, d.dept_name AS deptName, GROUP_CONCAT(DISTINCT r.role_key) AS roleKeys " +
        "FROM sys_user u LEFT JOIN sys_dept d ON d.dept_id = u.dept_id " +
        "LEFT JOIN sys_user_role ur ON ur.user_id = u.user_id " +
        "LEFT JOIN sys_role r ON r.role_id = ur.role_id AND r.del_flag = '0' " +
        "WHERE u.del_flag = '0' <if test='deptId != null'>AND u.dept_id = #{deptId}</if> " +
        "GROUP BY u.user_id, u.user_name, u.nick_name, u.dept_id, d.dept_name ORDER BY u.user_id" +
        "</script>")
    List<Map<String, Object>> selectMembers(@Param("deptId") Long deptId);

    /** 某用户 dept_id。 */
    @Select("SELECT dept_id FROM sys_user WHERE user_id = #{userId}")
    Long selectDeptId(@Param("userId") long userId);

    /** owner 显示名（来源切换器标签用）。 */
    @Select("<script>" +
        "SELECT user_id AS userId, nick_name AS nickName FROM sys_user " +
        "WHERE user_id IN <foreach item='u' collection='userIds' open='(' separator=',' close=')'>#{u}</foreach>" +
        "</script>")
    List<Map<String, Object>> selectUserNames(@Param("userIds") Collection<Long> userIds);

    /** upsert 单片段（UK subject_id+book_id+owner_id，幂等；owner 由 service 强制=登录者）。 */
    @Update("INSERT INTO biz_kg_lecture_frag(id, subject_id, kg_level, book_id, owner_id, title, content_json, stem_text, sort, status, create_by) " +
        "VALUES(#{id}, #{subjectId}, #{kgLevel}, #{bookId}, #{ownerId}, #{title}, #{contentJson}, #{stemText}, 0, #{status}, #{createBy}) " +
        "ON DUPLICATE KEY UPDATE title = VALUES(title), content_json = VALUES(content_json), " +
        "stem_text = VALUES(stem_text), status = VALUES(status), update_by = VALUES(create_by), update_time = NOW()")
    int upsertFrag(@Param("id") long id, @Param("subjectId") String subjectId, @Param("kgLevel") int kgLevel,
                   @Param("bookId") String bookId, @Param("ownerId") long ownerId, @Param("title") String title,
                   @Param("contentJson") String contentJson, @Param("stemText") String stemText,
                   @Param("status") String status, @Param("createBy") String createBy);
}

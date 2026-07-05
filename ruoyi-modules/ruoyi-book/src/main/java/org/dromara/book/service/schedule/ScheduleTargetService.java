package org.dromara.book.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.ClassStudentsBo;
import org.dromara.book.domain.bo.ScheduleTargetBo;
import org.dromara.book.domain.entity.BizClass;
import org.dromara.book.domain.entity.BizClassStudent;
import org.dromara.book.domain.entity.BizCoursePlan;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizScheduleSession;
import org.dromara.book.domain.entity.BizStudent;
import org.dromara.book.mapper.BizClassMapper;
import org.dromara.book.mapper.BizClassStudentMapper;
import org.dromara.book.mapper.BizCoursePlanLessonMapper;
import org.dromara.book.mapper.BizCoursePlanMapper;
import org.dromara.book.mapper.BizScheduleSessionMapper;
import org.dromara.book.mapper.BizStudentMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对象档案 Service（PRD-C-213 /teacher/schedule/target + /class）。
 *
 * <p>学生/班级两类一等公民；page 卡片墙实时聚合（已排/已上/请假数、绑定计划+进度、下一课、学员数），
 * 禁加冗余列。归属 = create_by（登录老师）。归档≠删（不进排课选择器）。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class ScheduleTargetService {

    /** 日历色板（自动轮转分配） */
    private static final String[] PALETTE = {
        "#0f766e", "#b45309", "#7c3aed", "#be123c", "#0369a1", "#4d7c0f", "#a21caf", "#c2410c"
    };

    private final BizStudentMapper studentMapper;
    private final BizClassMapper classMapper;
    private final BizClassStudentMapper classStudentMapper;
    private final BizScheduleSessionMapper sessionMapper;
    private final BizCoursePlanMapper planMapper;
    private final BizCoursePlanLessonMapper lessonMapper;

    private boolean isClass(String targetType) {
        return "1".equals(targetType);
    }

    /** 建档：color 空则色板轮转分配。返回新 id。 */
    @Transactional(rollbackFor = Exception.class)
    public Long create(ScheduleTargetBo bo) {
        if (bo.getName() == null || bo.getName().isBlank()) {
            throw new ServiceException("对象名称不能为空");
        }
        String color = bo.getColor();
        if (color == null || color.isBlank()) {
            color = nextColor();
        }
        String profile = bo.getProfileJson() == null ? null : JsonUtils.toJsonString(bo.getProfileJson());
        if (isClass(bo.getTargetType())) {
            BizClass c = new BizClass();
            c.setName(bo.getName());
            c.setGrade(bo.getGrade());
            c.setSubject(bo.getSubject());
            c.setColor(color);
            c.setProfileJson(profile);
            c.setArchived("0");
            classMapper.insert(c);
            return c.getId();
        }
        BizStudent s = new BizStudent();
        s.setName(bo.getName());
        s.setGrade(bo.getGrade());
        s.setSubject(bo.getSubject());
        s.setTextbook(bo.getTextbook());
        s.setParentPhone(bo.getParentPhone());
        s.setColor(color);
        s.setProfileJson(profile);
        s.setArchived("0");
        studentMapper.insert(s);
        return s.getId();
    }

    private String nextColor() {
        Long uid = LoginHelper.getUserId();
        long n = studentMapper.selectCount(new LambdaQueryWrapper<BizStudent>().eq(BizStudent::getCreateBy, uid))
            + classMapper.selectCount(new LambdaQueryWrapper<BizClass>().eq(BizClass::getCreateBy, uid));
        return PALETTE[(int) (n % PALETTE.length)];
    }

    /** 改基本维（不动 profile_json）。 */
    public void update(Long id, ScheduleTargetBo bo) {
        if (isClass(bo.getTargetType())) {
            BizClass c = classMapper.selectById(id);
            if (c == null) {
                throw new ServiceException("班课不存在");
            }
            if (bo.getName() != null) c.setName(bo.getName());
            c.setGrade(bo.getGrade());
            c.setSubject(bo.getSubject());
            if (bo.getColor() != null && !bo.getColor().isBlank()) c.setColor(bo.getColor());
            classMapper.updateById(c);
            return;
        }
        BizStudent s = studentMapper.selectById(id);
        if (s == null) {
            throw new ServiceException("学生不存在");
        }
        if (bo.getName() != null) s.setName(bo.getName());
        s.setGrade(bo.getGrade());
        s.setSubject(bo.getSubject());
        s.setTextbook(bo.getTextbook());
        s.setParentPhone(bo.getParentPhone());
        if (bo.getColor() != null && !bo.getColor().isBlank()) s.setColor(bo.getColor());
        studentMapper.updateById(s);
    }

    /** 整体覆写 profile_json（老师改肖像，含转正/删除 pending 信号）。 */
    public void overwriteProfile(Long id, String targetType, Object profile) {
        String json = profile == null ? null : JsonUtils.toJsonString(profile);
        if (isClass(targetType)) {
            BizClass c = classMapper.selectById(id);
            if (c == null) throw new ServiceException("班课不存在");
            c.setProfileJson(json);
            classMapper.updateById(c);
        } else {
            BizStudent s = studentMapper.selectById(id);
            if (s == null) throw new ServiceException("学生不存在");
            s.setProfileJson(json);
            studentMapper.updateById(s);
        }
    }

    /** 归档/取消归档。 */
    public void setArchived(Long id, String targetType, String archived) {
        if (isClass(targetType)) {
            BizClass c = classMapper.selectById(id);
            if (c == null) throw new ServiceException("班课不存在");
            c.setArchived(archived);
            classMapper.updateById(c);
        } else {
            BizStudent s = studentMapper.selectById(id);
            if (s == null) throw new ServiceException("学生不存在");
            s.setArchived(archived);
            studentMapper.updateById(s);
        }
    }

    /** 归档/取消归档（先查两表判类型，供无 targetType 的归档端点用）。 */
    public void archiveAuto(Long id, String archived) {
        BizStudent s = studentMapper.selectById(id);
        if (s != null) {
            s.setArchived(archived);
            studentMapper.updateById(s);
            return;
        }
        BizClass c = classMapper.selectById(id);
        if (c == null) throw new ServiceException("对象不存在");
        c.setArchived(archived);
        classMapper.updateById(c);
    }

    /** 设班课成员（整体覆盖）。 */
    @Transactional(rollbackFor = Exception.class)
    public void setClassStudents(Long classId, ClassStudentsBo bo) {
        if (classMapper.selectById(classId) == null) {
            throw new ServiceException("班课不存在");
        }
        classStudentMapper.delete(new LambdaQueryWrapper<BizClassStudent>().eq(BizClassStudent::getClassId, classId));
        if (bo.getStudentIds() != null) {
            for (Long sid : bo.getStudentIds()) {
                if (sid == null) continue;
                BizClassStudent cs = new BizClassStudent();
                cs.setClassId(classId);
                cs.setStudentId(sid);
                classStudentMapper.insert(cs);
            }
        }
    }

    /** 卡片墙（含实时聚合）。targetType 空=学生+班级都返。 */
    public List<Map<String, Object>> page(String targetType, String keyword, boolean includeArchived) {
        Long uid = LoginHelper.getUserId();
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> out = new ArrayList<>();
        if (targetType == null || "0".equals(targetType)) {
            LambdaQueryWrapper<BizStudent> w = new LambdaQueryWrapper<BizStudent>()
                .eq(BizStudent::getCreateBy, uid)
                .like(keyword != null && !keyword.isBlank(), BizStudent::getName, keyword)
                .orderByDesc(BizStudent::getId);
            if (!includeArchived) w.eq(BizStudent::getArchived, "0");
            for (BizStudent s : studentMapper.selectList(w)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", String.valueOf(s.getId()));
                m.put("targetType", "0");
                m.put("name", s.getName());
                m.put("grade", s.getGrade());
                m.put("subject", s.getSubject());
                m.put("textbook", s.getTextbook());
                m.put("parentPhone", s.getParentPhone());
                m.put("color", s.getColor());
                m.put("archived", s.getArchived());
                fillAggregates(m, "0", s.getId(), today);
                out.add(m);
            }
        }
        if (targetType == null || "1".equals(targetType)) {
            LambdaQueryWrapper<BizClass> w = new LambdaQueryWrapper<BizClass>()
                .eq(BizClass::getCreateBy, uid)
                .like(keyword != null && !keyword.isBlank(), BizClass::getName, keyword)
                .orderByDesc(BizClass::getId);
            if (!includeArchived) w.eq(BizClass::getArchived, "0");
            for (BizClass c : classMapper.selectList(w)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", String.valueOf(c.getId()));
                m.put("targetType", "1");
                m.put("name", c.getName());
                m.put("grade", c.getGrade());
                m.put("subject", c.getSubject());
                m.put("color", c.getColor());
                m.put("archived", c.getArchived());
                long stuCount = classStudentMapper.selectCount(
                    new LambdaQueryWrapper<BizClassStudent>().eq(BizClassStudent::getClassId, c.getId()));
                m.put("studentCount", stuCount);
                fillAggregates(m, "1", c.getId(), today);
                out.add(m);
            }
        }
        return out;
    }

    /** 详情（含 profile）。 */
    public Map<String, Object> detail(Long id, String targetType) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (isClass(targetType)) {
            BizClass c = classMapper.selectById(id);
            if (c == null) throw new ServiceException("班课不存在");
            m.put("id", String.valueOf(c.getId()));
            m.put("targetType", "1");
            m.put("name", c.getName());
            m.put("grade", c.getGrade());
            m.put("subject", c.getSubject());
            m.put("color", c.getColor());
            m.put("archived", c.getArchived());
            m.put("profile", parseJson(c.getProfileJson()));
            List<Long> sids = classStudentMapper.selectList(
                    new LambdaQueryWrapper<BizClassStudent>().eq(BizClassStudent::getClassId, id))
                .stream().map(BizClassStudent::getStudentId).toList();
            m.put("studentIds", sids.stream().map(String::valueOf).toList());
            m.put("studentCount", sids.size());
        } else {
            BizStudent s = studentMapper.selectById(id);
            if (s == null) throw new ServiceException("学生不存在");
            m.put("id", String.valueOf(s.getId()));
            m.put("targetType", "0");
            m.put("name", s.getName());
            m.put("grade", s.getGrade());
            m.put("subject", s.getSubject());
            m.put("textbook", s.getTextbook());
            m.put("parentPhone", s.getParentPhone());
            m.put("color", s.getColor());
            m.put("archived", s.getArchived());
            m.put("profile", parseJson(s.getProfileJson()));
        }
        return m;
    }

    private void fillAggregates(Map<String, Object> m, String targetType, Long targetId, LocalDate today) {
        List<BizScheduleSession> ss = sessionMapper.selectList(new LambdaQueryWrapper<BizScheduleSession>()
            .eq(BizScheduleSession::getTargetType, targetType)
            .eq(BizScheduleSession::getTargetId, targetId));
        long scheduled = ss.stream().filter(x -> "0".equals(x.getSessionStatus())).count();
        long done = ss.stream().filter(x -> "1".equals(x.getSessionStatus())).count();
        long leave = ss.stream().filter(x -> "2".equals(x.getSessionStatus())).count();
        m.put("scheduledCount", scheduled);
        m.put("doneCount", done);
        m.put("leaveCount", leave);

        // 绑定计划 + 进度（取有绑定的首个 plan_id）
        Long planId = ss.stream().map(BizScheduleSession::getPlanId).filter(java.util.Objects::nonNull)
            .findFirst().orElse(null);
        if (planId != null) {
            BizCoursePlan plan = planMapper.selectById(planId);
            long total = lessonMapper.selectCount(
                new LambdaQueryWrapper<BizCoursePlanLesson>().eq(BizCoursePlanLesson::getPlanId, planId));
            long progressDone = ss.stream()
                .filter(x -> planId.equals(x.getPlanId()) && "1".equals(x.getSessionStatus())).count();
            m.put("planId", String.valueOf(planId));
            m.put("planName", plan == null ? null : plan.getName());
            m.put("progressDone", progressDone);
            m.put("progressTotal", total);
        } else {
            m.put("planId", null);
            m.put("planName", null);
            m.put("progressDone", 0);
            m.put("progressTotal", 0);
        }

        // 下一课：date>=today 且 status='0' 最早
        BizScheduleSession next = ss.stream()
            .filter(x -> "0".equals(x.getSessionStatus()) && x.getSessionDate() != null
                && !x.getSessionDate().isBefore(today))
            .min(Comparator.comparing(BizScheduleSession::getSessionDate)
                .thenComparing(x -> x.getStartTime() == null ? "" : x.getStartTime()))
            .orElse(null);
        if (next != null) {
            Map<String, Object> ns = new LinkedHashMap<>();
            ns.put("id", String.valueOf(next.getId()));
            ns.put("date", String.valueOf(next.getSessionDate()));
            ns.put("start", next.getStartTime());
            ns.put("end", next.getEndTime());
            ns.put("title", sessionTitle(next));
            ns.put("prepStatus", "3".equals(next.getSessionType()) ? null : next.getPrepStatus());
            m.put("nextSession", ns);
        } else {
            m.put("nextSession", null);
        }
    }

    private String sessionTitle(BizScheduleSession s) {
        if ("3".equals(s.getSessionType())) return s.getExternalTitle();
        if (s.getPlanLessonId() != null) {
            BizCoursePlanLesson l = lessonMapper.selectById(s.getPlanLessonId());
            if (l != null && l.getTitle() != null) return l.getTitle();
        }
        return "2".equals(s.getSessionType()) ? "测试" : "正课";
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        return JsonUtils.parseObject(json, Object.class);
    }
}

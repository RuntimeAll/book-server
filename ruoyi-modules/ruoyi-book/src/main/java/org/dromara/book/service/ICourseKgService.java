package org.dromara.book.service;

import org.dromara.book.domain.vo.CourseKgVo;

/**
 * 课时知识梳理讲义 Service（只读）。
 *
 * @author backend-dev
 */
public interface ICourseKgService {

    /**
     * 组装一个课时（level4）的完整知识梳理讲义。
     *
     * @param courseId 课时节点 id（level4），如 901001002001
     * @return 讲义 VO；课时不存在返回 null
     */
    CourseKgVo getCourseKg(String courseId);
}

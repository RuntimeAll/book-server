package org.dromara.book.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 题目主表 Mapper（biz_question）。
 *
 * <p>page 走 mapper.xml 自定义 SQL — 走 misikt 字段命名（stemImg 等），过滤条件由 Wrapper 注入。
 *
 * @author backend-dev
 */
@Mapper
public interface BizQuestionMapper extends BaseMapperPlus<BizQuestion, BizQuestion> {

    /**
     * 分页查询题目列表（不含 questionKnowledges，由 Service 二次填充）。
     *
     * @param page    MyBatis-Plus 分页对象
     * @param wrapper LambdaQueryWrapper / QueryWrapper 注入 WHERE 条件
     * @return 分页 VO（仅 BizQuestion 主字段映射 + 别名 stemImg 等）
     */
    IPage<QuestionItemVo> selectQuestionPage(IPage<QuestionItemVo> page,
                                             @Param(Constants.WRAPPER) Wrapper<BizQuestion> wrapper);

    /**
     * 单题详情查询（不含 questionKnowledges / questionStdKnowledges，由 Service 二次填充）。
     *
     * @param id 题目 ID
     * @return 详情 VO（含 answer/explain/file/video 等详情字段）
     */
    org.dromara.book.domain.vo.QuestionDetailVo selectQuestionDetailById(@Param("id") Long id);
}

package org.dromara.book.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.book.domain.entity.BizPaper;
import org.dromara.book.domain.vo.PaperDetailVo;
import org.dromara.book.domain.vo.PaperListItemVo;
import org.dromara.book.domain.vo.PaperSectionVo;

import java.util.List;

@Mapper
public interface BizPaperMapper extends BizBaseMapper<BizPaper> {
    @Select("SELECT * FROM biz_paper WHERE id = #{paperId} FOR UPDATE")
    BizPaper lockById(@Param("paperId") Long paperId);

    PaperDetailVo selectPaperDetailHeader(@Param("paperId") Long paperId,
                                         @Param("currentUserId") String currentUserId);

    List<PaperSectionVo> selectSectionsByPaperId(@Param("paperId") Long paperId);

    IPage<PaperListItemVo> selectPaperListPage(IPage<PaperListItemVo> page,
                                             @Param(Constants.WRAPPER) Wrapper<PaperListItemVo> wrapper);
}

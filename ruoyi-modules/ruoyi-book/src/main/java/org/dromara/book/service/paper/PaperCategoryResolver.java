package org.dromara.book.service.paper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.entity.BizPaperCategory;
import org.dromara.book.domain.vo.BasketEntryVo;
import org.dromara.book.mapper.BizPaperCategoryMapper;
import org.dromara.book.mapper.PaperClassificationMapper;
import org.dromara.book.mapper.PaperClassificationMapper.SubjectDimensions;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaperCategoryResolver {
    private static final String PUBLIC_PAPER_ROOT_ID = "3001";

    private final PaperClassificationMapper classificationMapper;
    private final BizPaperCategoryMapper categoryMapper;

    /** Called only after selections have passed source ownership/availability validation. */
    public String infer(Collection<BasketEntryVo> selections) {
        if (selections.isEmpty()) {
            return null;
        }
        List<Long> bookIds = selections.stream().map(BasketEntryVo::getSourceBookId)
            .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> bookSubjects = new HashMap<>();
        if (!bookIds.isEmpty()) {
            classificationMapper.selectBookSubjects(bookIds)
                .forEach(book -> bookSubjects.put(book.getId(), book.getSubjectId()));
        }
        Set<String> subjectIds = new HashSet<>();
        for (BasketEntryVo entry : selections) {
            // A book instance belongs to the book's curriculum, not necessarily the original question's.
            String id = bookSubjects.get(entry.getSourceBookId());
            if (id == null || id.isBlank()) {
                id = entry.getQuestion().getSubjectId();
            }
            if (id == null || id.isBlank()) {
                return null;
            }
            subjectIds.add(id);
        }
        List<SubjectDimensions> dimensions = classificationMapper.selectSubjectDimensions(List.copyOf(subjectIds));
        Set<String> resolvedIds = new HashSet<>();
        Set<Curriculum> curricula = new HashSet<>();
        for (SubjectDimensions dimension : dimensions) {
            resolvedIds.add(dimension.getSourceId());
            curricula.add(new Curriculum(dimension.getSubject(), dimension.getStage(), dimension.getGrade(),
                dimension.getVolume()));
        }
        if (!resolvedIds.equals(subjectIds) || curricula.size() != 1) {
            return null;
        }
        Curriculum curriculum = curricula.iterator().next();
        if (curriculum.subject() == null || curriculum.stage() == null || curriculum.grade() == null) {
            return null;
        }
        LambdaQueryWrapper<BizPaperCategory> query = new LambdaQueryWrapper<BizPaperCategory>()
            .eq(BizPaperCategory::getNodeKind, "grade")
            .eq(BizPaperCategory::getParentId, PUBLIC_PAPER_ROOT_ID)
            .eq(BizPaperCategory::getSubject, curriculum.subject())
            .eq(BizPaperCategory::getStage, curriculum.stage())
            .eq(BizPaperCategory::getGrade, curriculum.grade());
        if (curriculum.volume() == null) {
            query.isNull(BizPaperCategory::getVolume);
        } else {
            query.eq(BizPaperCategory::getVolume, curriculum.volume());
        }
        List<BizPaperCategory> categories = categoryMapper.selectList(query);
        if (categories.size() != 1) {
            String reason = categories.isEmpty() ? "缺失" : "重复";
            throw new ServiceException("卷库公共年级分类" + reason + "，请先完成分类主数据对账", 409);
        }
        return categories.get(0).getId();
    }

    private record Curriculum(Integer subject, Integer stage, Integer grade, Integer volume) {
    }
}

package org.dromara.book.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.PaperDetailVo;
import org.dromara.book.domain.vo.PaperSourceVo;
import org.dromara.book.service.IPaperDetailService;
import org.dromara.book.service.IPaperSourceService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaperSourceServiceImpl implements IPaperSourceService {
    private final IPaperDetailService detailService;

    @Override
    public PaperSourceVo getPaperSource(Long paperId) {
        PaperDetailVo detail = detailService.getPaperDetail(paperId);
        if (detail == null) {
            return null;
        }
        PaperSourceVo source = new PaperSourceVo();
        source.setPaperId(detail.getPaperId());
        source.setPaperName(detail.getPaperName());
        source.setExamYear(detail.getExamYear());
        source.setScore(detail.getScore());
        source.setSuggestTime(detail.getSuggestTime());
        source.setQuestions(detail.getSections().stream().flatMap(section -> section.getQuestions().stream()).toList());
        return source;
    }
}

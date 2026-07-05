package org.dromara.book.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 私有题池检索 Service（PRD-C-213 /teacher/question/pool）。
 *
 * <p>口径：create_user = 我 且 is_public=0（私有池，版权红线不公开）。按 topic_tag / star_level / 关键词过滤。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class QuestionPoolService {

    private final BizQuestionMapper questionMapper;

    public Map<String, Object> pool(String topicTag, String starLevel, String keyword,
                                    Integer pageNum, Integer pageSize) {
        Long uid = LoginHelper.getUserId();
        long pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);

        QueryWrapper<BizQuestion> w = new QueryWrapper<BizQuestion>()
            .select("id", "question_type", "difficult", "stem_text", "star_level", "topic_tag", "source_ref")
            .eq("create_user", uid)
            .eq("is_public", 0)
            .ne("status", "2")
            .eq(topicTag != null && !topicTag.isBlank(), "topic_tag", topicTag)
            .eq(starLevel != null && !starLevel.isBlank(), "star_level", starLevel)
            .like(keyword != null && !keyword.isBlank(), "stem_text", keyword)
            .orderByDesc("id");

        Page<Map<String, Object>> page = new Page<>(pn, ps);
        Page<Map<String, Object>> result = questionMapper.selectMapsPage(page, w);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : result.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(row.get("id")));
            m.put("questionType", row.get("question_type"));
            m.put("difficult", row.get("difficult"));
            m.put("stemText", row.get("stem_text"));
            m.put("starLevel", row.get("star_level"));
            m.put("topicTag", row.get("topic_tag"));
            m.put("sourceRef", row.get("source_ref"));
            list.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", result.getTotal());
        r.put("list", list);
        r.put("pageNum", pn);
        r.put("pageSize", ps);
        return r;
    }
}

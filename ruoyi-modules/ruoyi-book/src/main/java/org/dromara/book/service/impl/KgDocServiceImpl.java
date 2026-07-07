package org.dromara.book.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.book.mapper.KgDocMapper;
import org.dromara.book.service.IKgDocService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 课件文档 Service 实现（PRD-C-205，只读+超管保存）。
 *
 * @author codeplace-C PRD-C-205
 */
@Service
@RequiredArgsConstructor
public class KgDocServiceImpl implements IKgDocService {

    private static final String DEFAULT_BOOK = "CC7S";

    private final KgDocMapper kgDocMapper;

    @Override
    public Map<String, Object> getDoc(String courseId, String bookId) {
        String book = StringUtils.isBlank(bookId) ? DEFAULT_BOOK : bookId;
        Map<String, Object> out = new HashMap<>();
        Map<String, Object> course = kgDocMapper.selectCourse(courseId);
        out.put("course", course);
        out.put("bookId", book);
        String docJson = kgDocMapper.selectDocJson(courseId, book);
        // 透传为对象，便于前端直接喂 Umo；空则 null
        out.put("docJson", StringUtils.isBlank(docJson) ? null : JsonUtils.parseObject(docJson, Object.class));
        return out;
    }

    @Override
    public int saveDoc(String courseId, String bookId, String title, String docJson) {
        String book = StringUtils.isBlank(bookId) ? DEFAULT_BOOK : bookId;
        return kgDocMapper.upsertDoc(courseId, book, title, docJson);
    }

    @Override
    public List<Map<String, Object>> getQuestions(String qidsCsv) {
        List<Long> ids = new ArrayList<>();
        if (StringUtils.isNotBlank(qidsCsv)) {
            for (String s : qidsCsv.split(",")) {
                String t = s.trim();
                if (t.matches("\\d{1,20}")) {   // 只收纯数字，防注入
                    ids.add(Long.parseLong(t));
                }
            }
        }
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return kgDocMapper.selectQuestionsByIds(ids);
    }
}

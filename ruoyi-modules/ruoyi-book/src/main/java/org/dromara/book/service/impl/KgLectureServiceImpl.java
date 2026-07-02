package org.dromara.book.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.book.mapper.KgLectureFragMapper;
import org.dromara.book.service.IKgLectureService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 讲义（片段汇聚）Service 实现（PRD-C-207）。
 *
 * <p>getLecture：把某前缀下的片段按树序拼成一份 Tiptap doc（服务端拼，前端仍拿完整 doc、复用只读渲染）。
 * getCatalog：某书所在册每课时挂了哪些讲义源，供左树灰置 + 来源切换器。P1 只官方（owner_id=0）。
 *
 * @author codeplace-C PRD-C-207
 */
@Service
@RequiredArgsConstructor
public class KgLectureServiceImpl implements IKgLectureService {

    private static final String DEFAULT_BOOK = "CC7S";

    private final KgLectureFragMapper fragMapper;

    @Override
    public Map<String, Object> getLecture(String subjectId, String bookId) {
        String book = StringUtils.isBlank(bookId) ? DEFAULT_BOOK : bookId;
        Map<String, Object> out = new HashMap<>();
        out.put("node", fragMapper.selectNode(subjectId));
        out.put("bookId", book);
        out.put("docJson", assemble(fragMapper.selectFragsByPrefix(book, subjectId)));
        return out;
    }

    /** 片段列表（已按 subject_id 树序）→ 拼成一份 {type:doc, content:[...]}；无片段则 null。 */
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
    public Map<String, Object> getCatalog(String bookId) {
        String book = StringUtils.isBlank(bookId) ? DEFAULT_BOOK : bookId;
        Map<String, Object> out = new HashMap<>();
        String volumeId = fragMapper.selectVolumeOfBook(book);
        out.put("volumeId", volumeId);
        List<Map<String, Object>> lessons = new ArrayList<>();
        if (StringUtils.isNotBlank(volumeId)) {
            // 按 lessonId 聚合来源
            Map<String, Map<String, Object>> byLesson = new LinkedHashMap<>();
            for (Map<String, Object> r : fragMapper.selectLessonSources(volumeId)) {
                String lessonId = (String) r.get("lessonId");
                Map<String, Object> lesson = byLesson.computeIfAbsent(lessonId, k -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("lessonId", lessonId);
                    m.put("lessonName", r.get("lessonName"));
                    m.put("sources", new ArrayList<Map<String, Object>>());
                    return m;
                });
                Map<String, Object> src = new HashMap<>();
                src.put("bookId", r.get("bookId"));
                src.put("bookName", r.get("bookName"));
                src.put("owner", r.get("owner"));
                ((List<Map<String, Object>>) lesson.get("sources")).add(src);
            }
            lessons.addAll(byLesson.values());
        }
        out.put("lessons", lessons);
        return out;
    }
}

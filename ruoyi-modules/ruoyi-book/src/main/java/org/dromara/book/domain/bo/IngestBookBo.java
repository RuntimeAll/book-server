package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 教辅 + 教辅树 upsert 入参（POST /teacher/ingest/book）—— PRD-C-204 B1。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@Data
public class IngestBookBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 教辅版本 */
    private Book book;

    /** 教辅目录树（含 level5 课时） */
    private List<BookSubjectNode> bookSubject;

    @Data
    public static class Book implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String id;
        private String fullName;
        private String edition;
        private String grade;
        private String term;
        private String series;
        private String bookType;
        private String publisher;
        private String subjectName;
        private String coverUrl;
    }

    @Data
    public static class BookSubjectNode implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String id;
        private String parentId;
        private String name;
        private Integer level;
        private Integer sort;
    }
}

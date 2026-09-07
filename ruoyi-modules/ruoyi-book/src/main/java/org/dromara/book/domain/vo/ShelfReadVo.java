package org.dromara.book.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Read models for shelf navigation and bounded content, not persistence entities. */
public final class ShelfReadVo {
    private ShelfReadVo() {
    }

    @Data
    public static class Outline {
        private String id;
        private String title;
        private String bookType;
        private String subjectId;
        private String ownerId;
        private String grade;
        private String edition;
        private Boolean isPublic;
        private List<Node> tree = new ArrayList<>();
    }

    @Data
    public static class Node {
        private String id;
        private String bookId;
        private String parentId;
        private Integer seq;
        private String nodeType;
        private String name;
        /** Direct items only; the reader never requests an empty node. */
        private long itemCount;
        /** Questions in this node and all its descendants. */
        private long questionCount;
        private List<Node> children = new ArrayList<>();
    }

    @Data
    public static class NodeDetail {
        private String id;
        private String bookId;
        private String parentId;
        private Integer seq;
        private String nodeType;
        private String name;
        private String kpId;
        private JsonNode meta;
    }

    @Data
    public static class Item {
        private String id;
        private String bookId;
        private String nodeId;
        private Integer seq;
        private String kind;
        private String questionId;
        private JsonNode override;
        private JsonNode explain;
        private JsonNode content;
        private Integer sourcePage;
        private Integer usedCount;
        /** Original text for the edit dialog; question below is already override-resolved. */
        private String originalStemText;
        private Question question;
        private boolean questionMissing;
    }

    @Data
    public static class Question {
        private String id;
        private Integer questionType;
        private Integer difficult;
        private String subjectId;
        private String stemText;
        private String stemTextContent;
        private String stemImg;
        private String blockJson;
        private String answerTextContent;
        private String analyzeTextContent;
        private String answerImg;
        private String explainImg;
        private String answerBlockJson;
        private String analyzeBlockJson;
    }

    @Data
    public static class ItemPage {
        private NodeDetail node;
        private List<Item> rows;
        private long total;
        private int pageNum;
        private int pageSize;
        private boolean hasMore;
    }

    /** Mapper aggregate projection, shared by book and direct-node statistics. */
    @Data
    public static class Counts {
        private Long id;
        private long itemCount;
        private long questionCount;
        private long nodeCount;
    }
}

package org.dromara.book.domain.bo;

import lombok.Data;

import java.util.List;

/**
 * 书架·整树一次建书入参（POST /teacher/shelf/import，PRD-002 直出书交接面，契约§3）。
 *
 * <p>结构：{title,bookType,subjectId?,grade?,edition?,sourceJobId?,tree:[{name,nodeType,kpId?,children?,items:[{questionId}|{explain}]}]}
 *
 * @author backend-dev
 */
@Data
public class ShelfImportBo {

    private String title;

    private String bookType;

    private String subjectId;

    private String grade;

    private String edition;

    /** 录入直出书溯源 job id（可选） */
    private Long sourceJobId;

    /** 编排风格元数据（可选） */
    private Object styleMeta;

    private List<ImportNode> tree;

    /**
     * 目录节点（递归）。
     */
    @Data
    public static class ImportNode {
        private String name;
        private String nodeType;
        private Long kpId;
        private Object meta;
        private List<ImportNode> children;
        private List<ImportItem> items;
    }

    /**
     * 内容项。kind 缺省按 questionId 有无推断（有=question，无=explain）。
     */
    @Data
    public static class ImportItem {
        private String kind;
        /** 字符串收防雪花号截尾 */
        private String questionId;
        private Object override;
        private Object explain;
    }
}

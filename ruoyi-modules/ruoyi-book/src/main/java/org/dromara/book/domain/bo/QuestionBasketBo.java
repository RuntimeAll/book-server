package org.dromara.book.domain.bo;

import lombok.Data;
import java.util.List;

@Data
public class QuestionBasketBo {
    private String namespace = "default";
    private List<SelectionReferenceBo> items;
    private List<String> entryKeys;
    private List<EntryVersion> entries;

    @Data
    public static class EntryVersion {
        private String entryKey;
        private Long basketEntryId;
    }
}

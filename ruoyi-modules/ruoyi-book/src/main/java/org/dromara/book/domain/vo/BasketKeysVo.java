package org.dromara.book.domain.vo;

import java.util.List;

/** Complete bounded membership without rendering data or persisted snapshots. */
public record BasketKeysVo(List<String> entryKeys) {
    public BasketKeysVo {
        entryKeys = List.copyOf(entryKeys);
    }
}

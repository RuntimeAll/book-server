package org.dromara.book.domain.vo;

import java.util.List;

public record BasketPageVo(List<BasketEntryVo> list, long total, int pageIndex, int pageSize) {
}

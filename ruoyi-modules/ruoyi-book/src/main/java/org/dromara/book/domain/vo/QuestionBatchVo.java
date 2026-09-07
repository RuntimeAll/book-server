package org.dromara.book.domain.vo;

import java.util.List;

/** Distinct questions and missing identifiers, both in input order. */
public record QuestionBatchVo(List<QuestionDetailVo> items, List<String> missingIds) {
}

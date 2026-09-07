package org.dromara.book.service.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.book.domain.vo.BasketEntryVo;
import org.dromara.book.domain.vo.CreateExamPaperVo;
import org.dromara.book.domain.vo.PaperDetailVo;
import org.dromara.book.domain.vo.PaperSectionVo;
import org.dromara.book.domain.vo.PaperSourceQuestionVo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class BasketPaperSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void identifiersAreStringsWithoutGlobalBigNumberSerializer() throws Exception {
        BasketEntryVo basket = new BasketEntryVo();
        basket.setQuestionId(1L);
        basket.setSourceBookId(2L);
        basket.setSourceItemId(2077049541005160450L);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(basket));
        assertTrue(json.get("questionId").isTextual());
        assertTrue(json.get("sourceBookId").isTextual());
        assertEquals("2077049541005160450", json.get("sourceItemId").textValue());
        CreateExamPaperVo created = new CreateExamPaperVo(17851210617516L, 1);
        assertEquals("17851210617516", mapper.readTree(mapper.writeValueAsString(created)).get("paperId").textValue());
        PaperDetailVo detail = new PaperDetailVo();
        detail.setPaperId(2L);
        assertTrue(mapper.readTree(mapper.writeValueAsString(detail)).get("paperId").isTextual());
        PaperSourceQuestionVo row = new PaperSourceQuestionVo();
        row.setPaperQuestionId(3L);
        row.setSourceBookId(2L);
        row.setSourceItemId(4L);
        JsonNode rowJson = mapper.readTree(mapper.writeValueAsString(row));
        assertTrue(rowJson.get("paperQuestionId").isTextual());
        assertTrue(rowJson.get("sourceBookId").isTextual());
        assertTrue(rowJson.get("sourceItemId").isTextual());
        PaperSectionVo section = new PaperSectionVo();
        section.setSectionId(4L);
        assertTrue(mapper.readTree(mapper.writeValueAsString(section)).get("sectionId").isTextual());
    }
}

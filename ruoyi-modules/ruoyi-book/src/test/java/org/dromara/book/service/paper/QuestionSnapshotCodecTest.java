package org.dromara.book.service.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class QuestionSnapshotCodecTest {
    private final QuestionSnapshotCodec codec = new QuestionSnapshotCodec(new ObjectMapper());

    @Test
    void structuredStemOrFigureInvalidatesAnswersButExplicitReplacementSurvives() {
        for (String override : java.util.List.of("{\"blockJson\":{\"v\":1,\"rows\":[]}}", "{\"figure\":null}")) {
            QuestionDetailVo result = codec.resolve(original(), override);
            assertNull(result.getAnswer());
            assertNull(result.getAnswerTextContent());
            assertNull(result.getAnswerBlockJson());
            assertNull(result.getExplain());
            assertNull(result.getAnalyzeBlockJson());
        }
        QuestionDetailVo result = codec.resolve(original(),
            "{\"blockJson\":{\"v\":1,\"rows\":[]},\"answer\":\"new\",\"analysis\":\"reason\"}");
        assertEquals("new", result.getAnswer());
        assertEquals("reason", result.getExplain());
    }

    @Test
    void freezesIndependentSnapshotWithoutMutatingSource() {
        QuestionDetailVo original = original();
        QuestionDetailVo resolved = codec.resolve(original, "{\"stem\":\"new stem\",\"answer\":\"new answer\",\"analysis\":\"new explanation\"}");
        assertEquals("new stem", resolved.getStemText());
        assertEquals("new answer", resolved.getAnswer());
        assertEquals("new explanation", resolved.getAnalyzeTextContent());
        assertFalse(resolved.getBlockJson().contains("old stem"));
        assertEquals("old answer", original.getAnswer());
        String frozen = codec.encode(resolved);
        resolved.setAnswer("changed later");
        assertEquals("new answer", codec.decode(frozen).getAnswer());
    }

    @Test
    void changedStemWithoutNewAnswerCannotReuseOldAnswerOrAnalysis() {
        QuestionDetailVo result = codec.resolve(original(), "{\"stem\":\"changed\"}");
        assertNull(result.getAnswer());
        assertNull(result.getAnswerTextContent());
        assertNull(result.getAnswerImg());
        assertNull(result.getAnswerBlockJson());
        assertNull(result.getExplain());
        assertNull(result.getAnalyzeTextContent());
        assertNull(result.getExplainImg());
        assertNull(result.getAnalyzeBlockJson());
    }

    @Test
    void metadataDoesNotInvalidateAnswerAndExplicitNullDoesNotFallback() {
        QuestionDetailVo metadata = codec.resolve(original(), "{\"role\":\"example\",\"roleSeq\":2}");
        assertEquals("old answer", metadata.getAnswer());
        QuestionDetailVo cleared = codec.resolve(original(), "{\"stem\":null,\"options\":null,\"answer\":null,\"analysis\":null}");
        assertNull(cleared.getStemText());
        assertNull(cleared.getStemTextContent());
        assertNull(cleared.getStemImg());
        assertNull(cleared.getBlockJson());
        assertNull(cleared.getAnswer());
        assertNull(cleared.getExplain());
    }

    @Test
    void optionOverrideReplacesOptionsAndInvalidatesAnswer() {
        QuestionDetailVo result = codec.resolve(original(), "{\"options\":[\"new A\",\"new B\"]}");
        assertTrue(result.getBlockJson().contains("old stem"));
        assertTrue(result.getBlockJson().contains("new A"));
        assertFalse(result.getBlockJson().contains("old option"));
        assertNull(result.getAnswer());
        assertNull(result.getExplain());
    }

    @Test
    void figureAndAnalysisSurviveSnapshotRoundTrip() {
        QuestionDetailVo result = codec.resolve(original(),
            "{\"stem\":\"new stem\",\"figure\":\"https://example.test/new.png\",\"analysis\":\"new analysis\"}");
        QuestionDetailVo restored = codec.decode(codec.encode(result));
        assertEquals("https://example.test/new.png", restored.getStemImg());
        assertTrue(restored.getBlockJson().contains("https://example.test/new.png"));
        assertFalse(restored.getBlockJson().contains("old.png"));
        assertEquals("new analysis", restored.getExplain());
        assertNull(restored.getAnswer());
    }

    @Test
    void explicitAnswerBlocksAreIndependentOfStemAndOldText() {
        QuestionDetailVo result = codec.resolve(original(),
            "{\"stem\":\"changed\",\"answerBlockJson\":{\"v\":1,\"rows\":[]},\"analyzeBlockJson\":null}");
        assertEquals("{\"v\":1,\"rows\":[]}", result.getAnswerBlockJson());
        assertNull(result.getAnswer());
        assertNull(result.getAnalyzeBlockJson());
        assertThrows(ServiceException.class, () -> codec.resolve(original(), "{\"options\":42}"));
        assertThrows(ServiceException.class, () -> codec.decode("{\"id\":null}"));
    }

    private QuestionDetailVo original() {
        QuestionDetailVo q = new QuestionDetailVo();
        q.setId(1L);
        q.setStatus("1");
        q.setStemText("old stem");
        q.setStemTextContent("old stem");
        q.setStemImg("https://example.test/old.png");
        q.setAnswer("old answer");
        q.setAnswerTextContent("old answer");
        q.setAnswerImg("https://example.test/answer.png");
        q.setAnswerBlockJson("{\"v\":1,\"rows\":[]}");
        q.setExplain("old explanation");
        q.setAnalyzeTextContent("old explanation");
        q.setExplainImg("https://example.test/explain.png");
        q.setAnalyzeBlockJson("{\"v\":1,\"rows\":[]}");
        q.setBlockJson("{\"v\":1,\"rows\":[{\"cells\":[{\"type\":\"text\",\"md\":\"old stem\"}]},{\"cells\":[{\"type\":\"option\",\"label\":\"A\",\"content\":[{\"type\":\"text\",\"md\":\"old option\"}]}]},{\"cells\":[{\"type\":\"image\",\"url\":\"https://example.test/old.png\"}]}]}");
        return q;
    }
}

package org.dromara.book.service.paper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.book.domain.entity.BizPaper;
import org.dromara.book.domain.entity.BizPaperQuestion;
import org.dromara.book.domain.entity.BizPaperSection;
import org.dromara.book.domain.entity.BizQuestionBasketEntry;

final class PaperTestMetadata {
    private PaperTestMetadata() {
    }

    static void initialize() {
        for (Class<?> type : new Class<?>[] {BizPaper.class, BizPaperQuestion.class,
            BizPaperSection.class, BizQuestionBasketEntry.class}) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "paper-unit-test");
            assistant.setCurrentNamespace(type.getName());
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }
}

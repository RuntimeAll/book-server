package org.dromara.book.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.util.EduTermUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.MonthDay;

/**
 * 期段边界 yml 覆盖口（PRD-C-213 R1a）。缺省 = EduTermUtil 类常量
 * （7/1 暑假、9/1 上学期、1/16 寒假、2/16 下学期起始）；需调整时 yml 配：
 * <pre>
 * edu:
 *   term:
 *     summer-start: 07-01
 *     autumn-start: 09-01
 *     winter-start: 01-16
 *     spring-start: 02-16
 * </pre>
 *
 * @author backend-dev
 */
@Slf4j
@Component
public class EduTermConfig {

    @Value("${edu.term.summer-start:}")
    private String summerStart;

    @Value("${edu.term.autumn-start:}")
    private String autumnStart;

    @Value("${edu.term.winter-start:}")
    private String winterStart;

    @Value("${edu.term.spring-start:}")
    private String springStart;

    @PostConstruct
    public void apply() {
        EduTermUtil.configure(parse(summerStart), parse(autumnStart), parse(winterStart), parse(springStart));
    }

    /** "MM-dd" → MonthDay；空/解析失败返回 null（保持缺省）。 */
    private MonthDay parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return MonthDay.parse("--" + s.trim());
        } catch (Exception e) {
            log.warn("edu.term 期段边界配置无法解析（需 MM-dd）：{}，保持缺省", s);
            return null;
        }
    }
}

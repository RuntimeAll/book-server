package org.dromara.admincommon.ocr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * OCR 模块配置 — 绑定 application.yml 的 {@code ocr:} 段。
 *
 * <p>PRD-B-006 期：仅占位结构（值留空），让 Spring 加载本 bean 即 done；
 * PRD-B-007 期：真正填 textin 凭据 + endpoint。</p>
 *
 * <p>典型配置（B-007 期 prod）：</p>
 * <pre>
 * ocr:
 *   default-provider: textin
 *   textin:
 *     endpoint: https://api.textin.com
 *     app-id: ${TEXTIN_APP_ID}
 *     secret-code: ${TEXTIN_SECRET_CODE}
 *     formula-level: 1
 *     get-image: objects
 *     timeout: 120s
 * </pre>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
@Data
@Component
@ConfigurationProperties(prefix = "ocr")
public class OcrProperties {

    /**
     * 默认 provider 名（如 "textin"）— 业务侧不指定时使用。
     * B-006 期空；B-007 期填 "textin"。
     */
    private String defaultProvider;

    /**
     * Textin 配置（B-007 期填）
     */
    private Textin textin = new Textin();

    /**
     * Textin provider 子配置
     */
    @Data
    public static class Textin {
        /** API endpoint URL */
        private String endpoint;
        /** 应用 ID（生产从环境变量 / password/ 注入） */
        private String appId;
        /** 应用 secret code */
        private String secretCode;
        /** 公式识别层级（0/1/2）— 默认 1 */
        private Integer formulaLevel = 1;
        /** 切图输出 — "none"/"objects"/"page" */
        private String getImage = "objects";
        /** 请求超时 */
        private Duration timeout = Duration.ofSeconds(120);
    }

}

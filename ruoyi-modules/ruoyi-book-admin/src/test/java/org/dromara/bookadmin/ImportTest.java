package org.dromara.bookadmin;

import org.dromara.admincommon.ocr.provider.IOcrProvider;
import org.dromara.admincommon.ocr.provider.model.OcrRequest;
import org.dromara.ai.provider.IAiTextGenerator;
import org.dromara.ai.provider.model.AiModelAlias;
import org.dromara.ai.provider.model.AiRequest;

/**
 * PRD-B-006 G9 编译验证类 — 验证 ruoyi-book-admin 模块能编译时引用 ruoyi-ai / admin-common.ocr 的接口和 POJO。
 *
 * <p>此类不被 surefire 真正执行（无 @Test 注解），只是为了让 {@code mvn test-compile -pl ruoyi-book-admin}
 * 通过类路径校验。PRD-B-007 期业务方真正注入时这俩 import 不再"假引用"。</p>
 *
 * <p>本期不在业务代码 src/main 里 import，避免业务被骨架"假实现"污染。</p>
 *
 * <p>🆕 2026-06-04 用户拍板: OCR 模块并入 ruoyi-admin-common (org.dromara.admincommon.ocr.*),
 * 不再独立成 ruoyi-ocr 模块。本测试类同步从 ruoyi-book/test 挪到 ruoyi-book-admin/test
 * (真实业务方所在模块, 已依赖 admin-common, ocr 通过传递性间接获得)。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006 G9, 2026-06-04 OCR 并入 common 后 v2)
 */
public class ImportTest {

    /**
     * 占位字段 — 仅为验证类型可见性（编译期检查）。
     * 真正注入由 PRD-B-007 业务方负责。
     */
    private IAiTextGenerator aiTextGenerator;
    private IOcrProvider ocrProvider;

    /**
     * 占位方法 — 仅为验证 POJO 字段可用。
     */
    public void usageHint() {
        AiRequest aiReq = new AiRequest();
        aiReq.setAlias(AiModelAlias.CLAUDE_FAST);

        OcrRequest ocrReq = new OcrRequest();
        ocrReq.setFileType(OcrRequest.FileType.PDF);

        // 故意不调任何方法 — B-006 期不实现，B-007 期填实现。
        // 类型可见即 G9 通过。
        if (aiTextGenerator != null) {
            aiTextGenerator.name();
        }
        if (ocrProvider != null) {
            ocrProvider.name();
        }
    }

}

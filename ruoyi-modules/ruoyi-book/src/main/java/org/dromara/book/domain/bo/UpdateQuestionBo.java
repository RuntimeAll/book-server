package org.dromara.book.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * PRD-C-015 批4·缺口10 —— teacher 侧「覆盖原行」入参 BO（POST /teacher/question/update）。
 *
 * <p>用于举一反三「重生后再入库 = 覆盖原行」（AI 改了 DNA / 重生题面后，把已入库的题更新而非新写一行）。
 * 字段全部复用 {@link CreateQuestionBo}（题面三要素 / 直列 / 血缘 / 打标 / DNA），仅多一个 {@code id}
 * 定位要覆盖的题（雪花大整数，必填）。
 *
 * <p>🔴 归属仍由后端校验：只许改自己的题（owner = 登录老师；create_user 不改、update_by/time 刷新）。
 * createBy/createUser/status 服务端强制不动。
 *
 * @author backend-dev (PRD-C-015 批4)
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UpdateQuestionBo extends CreateQuestionBo {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 要覆盖的题目 ID（雪花），必填 → 据它 UPDATE biz_question 并重写 text/knowledge/free_tag/ai。 */
    @NotNull(message = "id 不能为空（覆盖更新须指定要更新哪道题）")
    private Long id;
}

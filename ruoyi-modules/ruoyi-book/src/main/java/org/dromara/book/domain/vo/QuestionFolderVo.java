package org.dromara.book.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 题目收藏夹 VO（GET /teacher/center/q-folder/tree）。
 *
 * <p>字段口径对齐前端 FavoriteFolder 契约（book-ui/src/api/question/index.ts）：
 * {@code id / name / pid / count / children / sort / createTime}。
 *
 * <p>count = 该夹下 biz_question_favorite（folder_id=本夹 id）的收藏题数。
 * createTime 只读展示（前端展示用，雪花/自增 id 无影响，时间正常序列化）。
 *
 * @author backend-dev
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionFolderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 收藏夹 id */
    private Long id;

    /** 收藏夹名称 */
    private String name;

    /** 父夹 id（0=根） */
    private Long pid;

    /** 该夹下已收藏题数（关联 biz_question_favorite.folder_id 计数） */
    private Integer count;

    /** 排序 */
    private Integer sort;

    /** 创建时间（只读展示） */
    private Date createTime;

    /** 子夹（无子夹时不输出，NON_NULL 兜底） */
    private List<QuestionFolderVo> children;
}

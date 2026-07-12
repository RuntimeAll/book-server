package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 专项书实体（复用 biz_shelf_book，book_type='special'，PRD-003 C 位自建薄实体）。
 *
 * <p>🔴 命名纪律（跨线契约 §3）：A 位 PRD-002 的 Shelf* 实体层尚未合入 master 时，C 位自建
 * {@code Special*} 薄实体/Mapper 直读写 biz_shelf_* 三表；类名一律 {@code Special*} 绝不占
 * {@code Shelf*}，集成段 A 的实体层合入后由调度中心收敛重复。本实体只承 book_type='special' 语义。
 *
 * <p>主键 = 雪花号（应用 {@code IdUtil.getSnowflakeNextId()} 生成，非自增）。
 * 审计列 create_dept/create_by/create_time/update_by/update_time 由 BaseEntity + MetaObjectHandler 自动填。
 *
 * @author codeplace-C PRD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_shelf_book")
public class SpecialBook extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 雪花主键（应用生成） */
    @TableId(value = "id")
    private Long id;

    /** 书型：本卡固定 'special' */
    private String bookType;

    /** 专项标题（卷面可见，禁内部词） */
    private String title;

    /** 学科（对齐 biz_ingest_job.subject_id 口径） */
    private String subjectId;

    /** 年级 */
    private String grade;

    /** 教材版本 */
    private String edition;

    /** 归属老师 user_id（= create_by，pick/编辑做归属校验） */
    private Long ownerId;

    /** 状态：'0' 正常 / '1' 归档 */
    private String status;

    /** 编排风格元数据 JSON（导出主题等） */
    private String styleMetaJson;

    /** 录入直出书溯源 job id（本卡不用） */
    private Long sourceJobId;

    private String remark;
}

package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 托管录题作业入参（POST /teacher/ingest/job/hosted）—— PRD-001 D14 / §10 契约。
 *
 * <p>skill 侧确定性切割（split.py 校验全绿/黄）后，把 <b>pre-split items</b> 整批交托管：
 * BE 建 job(lane='hosted') + 逐条落 biz_ingest_job_item，job 状态<b>直达 DONE 待审</b>
 * （不走 toolkit 异步拆题 worker），复用现有审核页 /ingest/review/:jobId 逐题看/改/勾选入库。
 *
 * <p>🔴 零 DDL：lane 复用现列填 'hosted'；items 字段全落 biz_ingest_job_item 现列。
 * <p>🔴 幂等：同 {@code fileHash + teacherId + lane='hosted'} 去重（fileHash 存 job.remark），
 *    重复提交返回既有 jobId、不再建作业。
 * <p>🔴 bookTitle 可收但 BE 不存储（录入直出书由 skill 侧调 A 线 /teacher/shelf/import，跨线契约 §3）。
 *
 * @author backend-dev (PRD-001 批1)
 */
@Data
public class IngestJobHostedBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 源文件名（留存展示） */
    private String sourceFileName;

    /** 绑定章节节点 biz_subject.id（整批粗挂），必填 */
    private String subjectId;

    /** 幂等键：源文件 md5（probe 报告 file_hash）；同 teacher+lane 去重 */
    private String fileHash;

    /** 直出书书名（BE 不存储，交接 A 线建书用） */
    private String bookTitle;

    /** pre-split 题列表，必填非空 */
    private List<HostedItem> items;

    /**
     * 一道 pre-split 题（字段全落 biz_ingest_job_item 现列，零 DDL）。
     * figuresJson/kpAnchorJson/dnaJson 为可选 JSON 文本（列类型 json，须合法 JSON 或省略）。
     */
    @Data
    public static class HostedItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 题序（1..N，= 审核页/入库 sort） */
        private Integer seq;
        /** 题干（Markdown+$LaTeX$） */
        private String stemText;
        /** 题型 1选择/2填空/5解答（缺省 5） */
        private Integer questionType;
        /** 选项数组 JSON（选择题；审核页读此渲染选项字母） */
        private String optionsJson;
        /** 答案 */
        private String answerText;
        /** 解析 */
        private String analyzeText;
        /** 含图 0/1 */
        private Integer hasFigure;
        /** 难度 1-3 */
        private Integer difficulty;
        /** 裁图归属 JSON（可选） */
        private String figuresJson;
        /** KG 锚定 JSON（可选） */
        private String kpAnchorJson;
        /** 10维DNA JSON（可选） */
        private String dnaJson;
    }
}

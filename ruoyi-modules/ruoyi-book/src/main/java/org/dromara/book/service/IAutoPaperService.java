package org.dromara.book.service;

import org.dromara.book.domain.bo.AutoGenerateBo;
import org.dromara.book.domain.vo.AutoGeneratePaperVo;

/**
 * PRD-C-001 — AI 组卷确定性后端 Service 接口。
 *
 * <p>分层架构后段：入参为 LLM 前段（Dify）锁定真实 subjectId 的结构化大纲，
 * 本 Service 零 LLM，纯代码确定性查库 + L1/L2/L3 fallback + 去重 + 按题型组装。
 *
 * @author backend-dev
 */
public interface IAutoPaperService {

    /**
     * 按大纲确定性组卷（POST /teacher/paper/auto-generate）。
     *
     * @param bo 组卷大纲入参
     * @return 组装好的试卷 + coverage + tips + gaps + notes
     */
    AutoGeneratePaperVo autoGenerate(AutoGenerateBo bo);
}

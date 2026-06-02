package org.dromara.book.service;

import org.dromara.book.domain.bo.QuestionFolderBo;
import org.dromara.book.domain.vo.QuestionFolderVo;

import java.util.List;

/**
 * 题目收藏夹 Service 接口（PRD-A-005 收尾 B-收藏夹 CRUD）。
 *
 * <p>范围一律限当前登录 user_id（LoginHelper.getUserId()），绝不信任前端传的归属字段。
 *
 * @author backend-dev
 */
public interface IQuestionFolderService {

    /**
     * 收藏夹列表（GET /teacher/center/q-folder/tree）。
     *
     * <p>查 biz_question_folder（user_id=登录用户，按 sort / create_time 排序），
     * 每夹带 count（关联 biz_question_favorite 该 folder_id 的收藏数）。
     * 始终首位带虚拟默认夹 {id:0,name:"我的试题"}（folder_id=0 的散收藏），兼容前端兜底契约。
     *
     * @return 收藏夹列表（含默认夹 + 自建夹）
     */
    List<QuestionFolderVo> folderTree();

    /**
     * 新建收藏夹。name 必填 + 可选 pid；user_id=登录用户，create_time=now。
     *
     * @param bo name + pid?
     * @return 新建夹 id
     */
    Long createFolder(QuestionFolderBo bo);

    /**
     * 改名（仅名称可改）。先校验该 folder 的 user_id=登录用户（防越权改他人夹），只更新 name。
     *
     * @param bo id + name
     */
    void renameFolder(QuestionFolderBo bo);

    /**
     * 删除收藏夹。校验 user_id=登录用户 → 删 folder；
     * 该夹下 biz_question_favorite.folder_id 重置为 0（归默认夹，不丢用户收藏的题）。
     *
     * @param bo id
     */
    void deleteFolder(QuestionFolderBo bo);
}

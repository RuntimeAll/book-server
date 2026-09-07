package org.dromara.book.service.paper;

import org.dromara.book.domain.bo.PaperQuestionInputBo;
import org.dromara.book.domain.bo.SelectionReferenceBo;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.constant.SystemConstants;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SelectionRules {
    public static final int MAX_PAPER_QUESTIONS = 500;
    public static final int MAX_BATCH_SIZE = 100;
    public static final int DEFAULT_SUGGEST_TIME = 120;
    public static final String OFFICIAL_OWNER_ID = SystemConstants.SUPER_ADMIN_ID.toString();

    public static boolean canManagePaper(String ownerId, Long userId) {
        if (ownerId == null || userId == null) {
            return false;
        }
        if (OFFICIAL_OWNER_ID.equals(ownerId)) {
            return SystemConstants.SUPER_ADMIN_ID.equals(userId);
        }
        return userId.toString().equals(ownerId);
    }

    /** 官方普通卷是唯一允许超管切换公开状态的试卷范围。 */
    public static boolean isOfficialOrdinaryPaper(String ownerId, String paperKind) {
        return OFFICIAL_OWNER_ID.equals(ownerId) && !"2".equals(paperKind);
    }

    public static boolean canChangeVisibility(String ownerId, String paperKind, Long userId) {
        return SystemConstants.SUPER_ADMIN_ID.equals(userId)
            && isOfficialOrdinaryPaper(ownerId, paperKind);
    }

    private SelectionRules() {
    }

    public static String entryKey(SelectionReferenceBo ref) {
        if (ref == null || ref.getQuestionId() == null || ref.getQuestionId() <= 0) {
            throw new ServiceException("题目ID无效", 400);
        }
        if ((ref.getSourceBookId() == null) != (ref.getSourceItemId() == null)) {
            throw new ServiceException("书籍和内容项来源必须成对提供", 400);
        }
        if (ref.getSourceItemId() != null && (ref.getSourceItemId() <= 0 || ref.getSourceBookId() <= 0)) {
            throw new ServiceException("来源ID无效", 400);
        }
        return ref.getSourceItemId() == null ? "q:" + ref.getQuestionId() : "shelf:" + ref.getSourceItemId();
    }

    public static String namespace(String value) {
        String namespace = value == null ? "default" : value;
        if (!namespace.matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,63}")) {
            throw new ServiceException("篮子命名空间格式无效", 400);
        }
        return namespace;
    }

    public static void validateTitle(String name) {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new ServiceException("试卷名称长度需1-200字符", 400);
        }
    }

    public static void validateTime(Integer minutes) {
        if (minutes != null && (minutes < 1 || minutes > 1440)) {
            throw new ServiceException("答题时间需1-1440分钟", 400);
        }
    }

    public static void validateRequestId(String value, boolean required) {
        if (value == null && !required) {
            return;
        }
        try {
            if (value == null || !UUID.fromString(value).toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new ServiceException("requestId必须为UUID", 400);
        }
    }

    public static void validateRows(List<? extends PaperQuestionInputBo> rows) {
        if (rows == null || rows.isEmpty() || rows.size() > MAX_PAPER_QUESTIONS) {
            throw new ServiceException("试卷题数需1-500题", 400);
        }
        Set<String> keys = new HashSet<>();
        Set<Integer> sorts = new HashSet<>();
        for (PaperQuestionInputBo row : rows) {
            if (!keys.add(entryKey(row))) {
                throw new ServiceException("试卷不能包含重复实例", 400);
            }
            if (row.getSort() == null || row.getSort() < 1 || row.getSort() > MAX_PAPER_QUESTIONS
                || !sorts.add(row.getSort())) {
                throw new ServiceException("题目排序需1-500且不能重复", 400);
            }
            BigDecimal score = row.getScore();
            if (score != null && (score.signum() < 0 || score.compareTo(new BigDecimal("999.99")) > 0
                || score.scale() > 2)) {
                throw new ServiceException("单题分值需0-999.99，最多2位小数", 400);
            }
        }
    }
}

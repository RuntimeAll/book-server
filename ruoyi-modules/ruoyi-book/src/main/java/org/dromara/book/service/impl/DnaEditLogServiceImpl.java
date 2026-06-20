package org.dromara.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.DnaEditLogBo;
import org.dromara.book.domain.entity.BizDnaEditLog;
import org.dromara.book.domain.vo.DnaEditLogVo;
import org.dromara.book.mapper.BizDnaEditLogMapper;
import org.dromara.book.service.IDnaEditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DNA 编辑经验层留痕 Service 实现（PRD-C-100 B4，只写 + 可选 list）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>record 纯 insert，teacher_id 由 Controller 从登录态注入，不信前端</li>
 *   <li>recent 按 teacher_id 过滤 + created_at 倒序 + last(LIMIT n)，limit 兜底 50</li>
 *   <li>created_at 走 MP {@code @TableField(fill = INSERT)} + DB default 双保险</li>
 * </ul>
 *
 * @author PRD-C-100 B4
 */
@Service
@RequiredArgsConstructor
public class DnaEditLogServiceImpl implements IDnaEditLogService {

    private static final int DEFAULT_LIMIT = 50;

    private final BizDnaEditLogMapper bizDnaEditLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void record(Long teacherId, DnaEditLogBo bo) {
        if (teacherId == null || bo == null) {
            // 入参不合法静默返回，不抛错
            return;
        }
        BizDnaEditLog entity = new BizDnaEditLog();
        entity.setTeacherId(teacherId);
        entity.setTargetId(bo.getTargetId());
        entity.setEditKind(bo.getEditKind());
        entity.setDim(bo.getDim());
        entity.setBeforeVal(bo.getBefore());
        entity.setAfterVal(bo.getAfter());
        entity.setCorrectionPrompt(bo.getCorrectionPrompt());
        entity.setCommitted(Boolean.TRUE.equals(bo.getCommitted()) ? 1 : 0);
        entity.setCreatedAt(new Date());
        bizDnaEditLogMapper.insert(entity);
    }

    @Override
    public List<DnaEditLogVo> recent(Long teacherId, Integer limit) {
        if (teacherId == null) {
            return Collections.emptyList();
        }
        int safeLimit = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;
        List<BizDnaEditLog> rows = bizDnaEditLogMapper.selectList(
            new LambdaQueryWrapper<BizDnaEditLog>()
                .eq(BizDnaEditLog::getTeacherId, teacherId)
                .orderByDesc(BizDnaEditLog::getCreatedAt)
                .orderByDesc(BizDnaEditLog::getId)
                .last("LIMIT " + safeLimit)
        );
        return rows.stream().map(this::toVo).collect(Collectors.toList());
    }

    private DnaEditLogVo toVo(BizDnaEditLog entity) {
        DnaEditLogVo vo = new DnaEditLogVo();
        vo.setId(entity.getId());
        vo.setTargetId(entity.getTargetId());
        vo.setEditKind(entity.getEditKind());
        vo.setDim(entity.getDim());
        vo.setBeforeVal(entity.getBeforeVal());
        vo.setAfterVal(entity.getAfterVal());
        vo.setCorrectionPrompt(entity.getCorrectionPrompt());
        vo.setCommitted(entity.getCommitted());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}

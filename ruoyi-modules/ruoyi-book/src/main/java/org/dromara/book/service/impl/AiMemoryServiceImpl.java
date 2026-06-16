package org.dromara.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.AiMemoryBo;
import org.dromara.book.domain.entity.BizTeacherAiMemory;
import org.dromara.book.domain.vo.AiMemoryVo;
import org.dromara.book.mapper.BizTeacherAiMemoryMapper;
import org.dromara.book.service.IAiMemoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 老师全局 AI 记忆 Service 实现（PRD-C-100 B4）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>LambdaQueryWrapper 优先（脚手架自带，避免裸 SQL）— 表简单不需要 mapper.xml</li>
 *   <li>写/删/翻转一律先 selectById 取实体，校验 {@code teacher_id == 登录老师}，越权静默返 null/不改</li>
 *   <li>list 按 teacher_id 过滤，memType 可空，enabledOnly=true 只取 enabled=1，按 mem_type, id 排序</li>
 *   <li>create_time/update_time 走 MP {@code @TableField(fill=...)} + DB default 双保险</li>
 * </ul>
 *
 * @author PRD-C-100 B4
 */
@Service
@RequiredArgsConstructor
public class AiMemoryServiceImpl implements IAiMemoryService {

    private static final String DEFAULT_SOURCE = "手填";

    private final BizTeacherAiMemoryMapper bizTeacherAiMemoryMapper;

    @Override
    public List<AiMemoryVo> list(Long teacherId, String memType, Boolean enabledOnly) {
        if (teacherId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<BizTeacherAiMemory> wrapper = new LambdaQueryWrapper<BizTeacherAiMemory>()
            .eq(BizTeacherAiMemory::getTeacherId, teacherId);
        if (memType != null && !memType.isEmpty()) {
            wrapper.eq(BizTeacherAiMemory::getMemType, memType);
        }
        if (Boolean.TRUE.equals(enabledOnly)) {
            wrapper.eq(BizTeacherAiMemory::getEnabled, 1);
        }
        wrapper.orderByAsc(BizTeacherAiMemory::getMemType)
               .orderByAsc(BizTeacherAiMemory::getId);
        return bizTeacherAiMemoryMapper.selectList(wrapper).stream()
            .map(this::toVo)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiMemoryVo add(Long teacherId, AiMemoryBo bo) {
        if (teacherId == null || bo == null) {
            return null;
        }
        BizTeacherAiMemory entity = new BizTeacherAiMemory();
        entity.setTeacherId(teacherId);
        entity.setMemType(bo.getMemType());
        entity.setMemKey(bo.getMemKey());
        entity.setMemValue(bo.getMemValue());
        // source 缺省「手填」（Python 自动写传「自动」）
        String source = bo.getSource();
        entity.setSource(source != null && !source.isEmpty() ? source : DEFAULT_SOURCE);
        entity.setEnabled(1);
        entity.setConfidence(bo.getConfidence());
        entity.setRemark(bo.getRemark());
        Date now = new Date();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        bizTeacherAiMemoryMapper.insert(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiMemoryVo update(Long teacherId, Long id, AiMemoryBo bo) {
        BizTeacherAiMemory existing = loadOwned(teacherId, id);
        if (existing == null || bo == null) {
            // 越权 / 不存在 → null 不改
            return null;
        }
        existing.setMemType(bo.getMemType());
        existing.setMemKey(bo.getMemKey());
        existing.setMemValue(bo.getMemValue());
        if (bo.getSource() != null && !bo.getSource().isEmpty()) {
            existing.setSource(bo.getSource());
        }
        existing.setConfidence(bo.getConfidence());
        existing.setRemark(bo.getRemark());
        existing.setUpdateTime(new Date());
        bizTeacherAiMemoryMapper.updateById(existing);
        return toVo(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long teacherId, Long id) {
        BizTeacherAiMemory existing = loadOwned(teacherId, id);
        if (existing == null) {
            // 越权 / 不存在 → 不删
            return;
        }
        bizTeacherAiMemoryMapper.deleteById(existing.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiMemoryVo toggle(Long teacherId, Long id) {
        BizTeacherAiMemory existing = loadOwned(teacherId, id);
        if (existing == null) {
            // 越权 / 不存在 → null
            return null;
        }
        // 翻转 enabled（1↔0），null 容忍按启用处理
        int cur = existing.getEnabled() != null ? existing.getEnabled() : 1;
        existing.setEnabled(cur == 1 ? 0 : 1);
        existing.setUpdateTime(new Date());
        bizTeacherAiMemoryMapper.updateById(existing);
        return toVo(existing);
    }

    /**
     * 取实体并校验归属 —— teacher_id 不匹配返 null（越权防护）。
     */
    private BizTeacherAiMemory loadOwned(Long teacherId, Long id) {
        if (teacherId == null || id == null) {
            return null;
        }
        BizTeacherAiMemory existing = bizTeacherAiMemoryMapper.selectById(id);
        if (existing == null || !teacherId.equals(existing.getTeacherId())) {
            return null;
        }
        return existing;
    }

    private AiMemoryVo toVo(BizTeacherAiMemory entity) {
        AiMemoryVo vo = new AiMemoryVo();
        vo.setId(entity.getId());
        vo.setMemType(entity.getMemType());
        vo.setMemKey(entity.getMemKey());
        vo.setMemValue(entity.getMemValue());
        vo.setSource(entity.getSource());
        vo.setEnabled(entity.getEnabled());
        vo.setConfidence(entity.getConfidence());
        vo.setRemark(entity.getRemark());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}

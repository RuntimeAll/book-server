package org.dromara.scheduling.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.scheduling.domain.BizTsBase;
import org.dromara.scheduling.mapper.BizTsBaseMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 主线 C · V-1 全局基点持久化
 *
 * <p>2 端点：
 * <ul>
 *   <li>POST /schedule/base   {address} → geocode → upsert biz_ts_base</li>
 *   <li>GET  /schedule/base   → 当前 user_id 的基点（不存在返 exists:false）</li>
 * </ul>
 *
 * <p>MVP 简化：user_id 固定 1L（C-5 卡接 Sa-Token 后改）。
 */
@RestController
@RequestMapping("/schedule/base")
@RequiredArgsConstructor
public class ScheduleBaseController {

    private static final Long MVP_FIXED_USER_ID = 1L;

    private final BizTsBaseMapper baseMapper;
    private final ScheduleAmapController amapController;

    @SaIgnore
    @PostMapping
    public Map<String, Object> setBase(@RequestBody Map<String, String> body) throws Exception {
        String address = body == null ? null : body.get("address");
        if (address == null || address.isBlank()) {
            return Map.of("ok", false, "error", "address 不能为空");
        }

        Map<String, Object> geo = amapController.geocode(Map.of("address", address));
        if (geo.containsKey("error")) {
            return Map.of(
                "ok", false,
                "error", "geocode 失败: " + geo.get("error"),
                "address", address
            );
        }

        Long userId = MVP_FIXED_USER_ID;
        BizTsBase existing = baseMapper.selectOne(
            new LambdaQueryWrapper<BizTsBase>().eq(BizTsBase::getUserId, userId)
        );
        Date now = new Date();
        BizTsBase entity = existing != null ? existing : new BizTsBase();
        entity.setUserId(userId);
        entity.setAddressText(address);
        entity.setLng(new BigDecimal((String) geo.get("lng")));
        entity.setLat(new BigDecimal((String) geo.get("lat")));
        entity.setFormattedAddress((String) geo.get("formatted_address"));
        entity.setAddressLevel((String) geo.get("level"));
        entity.setUpdateTime(now);
        if (existing == null) {
            entity.setCreateBy(userId);
            entity.setCreateTime(now);
            baseMapper.insert(entity);
        } else {
            baseMapper.updateById(entity);
        }

        Map<String, Object> addr = new LinkedHashMap<>();
        addr.put("text", entity.getAddressText());
        addr.put("lng", entity.getLng().toPlainString());
        addr.put("lat", entity.getLat().toPlainString());
        addr.put("formatted", entity.getFormattedAddress());
        addr.put("level", entity.getAddressLevel());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("userId", userId);
        out.put("address", addr);
        return out;
    }

    @SaIgnore
    @GetMapping
    public Map<String, Object> getBase() {
        BizTsBase entity = baseMapper.selectOne(
            new LambdaQueryWrapper<BizTsBase>().eq(BizTsBase::getUserId, MVP_FIXED_USER_ID)
        );
        if (entity == null) {
            return Map.of("ok", true, "exists", false);
        }
        Map<String, Object> addr = new LinkedHashMap<>();
        addr.put("text", entity.getAddressText());
        addr.put("lng", entity.getLng().toPlainString());
        addr.put("lat", entity.getLat().toPlainString());
        addr.put("formatted", entity.getFormattedAddress());
        addr.put("level", entity.getAddressLevel());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("exists", true);
        out.put("userId", entity.getUserId());
        out.put("address", addr);
        return out;
    }
}

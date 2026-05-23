package org.dromara.scheduling.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 主线 C 老师排课 Agent · 健康检查端点（骨架 Step 1）
 *
 * <p>路径前缀 {@code /schedule/*}，跟 misikt {@code /teacher/*} + admin {@code /admin/*} 业务隔离。
 *
 * <p>本端点用于：
 * <ul>
 *   <li>骨架联调 — FE 启动后 GET /schedule/health 确认 BE 跑起来了</li>
 *   <li>部署 livenessProbe / readinessProbe（k8s 期可用）</li>
 *   <li>分支识别 — branch 字段标 master-ai，避免跟 misikt master 混</li>
 * </ul>
 */
@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleHealthController {

    @Value("${server.port:9999}")
    private Integer port;

    @SaIgnore
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("module", "scheduling");
        m.put("branch", "master-ai");
        m.put("port", port);
        m.put("now", LocalDateTime.now().toString());
        m.put("message", "主线 C · 老师排课 Agent 后端骨架就绪");
        return m;
    }
}

package org.dromara.book.controller;

import org.dromara.common.core.domain.R;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * misikt envelope 全局响应转换 advice。
 *
 * <p>scope：只作用于 URI 前缀 {@code /teacher/} 的端点（含本模块所有 /teacher/...
 * Controller + 异常处理器对 /teacher/... 抛错的兜底响应）；其他端点（如 /auth/login、
 * /system/...、/demo/...）保持 RuoYi 原 {@code R<T>{code:200, msg, data}} 风格不变。
 *
 * <p>转换规则：
 * <pre>
 * RuoYi R{code:200, msg:"操作成功", data:T}
 *     → misikt {code:1,   message:"成功",  response:T}
 *
 * RuoYi R{code:500, msg:"xxx",   data:null}
 *     → misikt {code:500, message:"xxx",  response:null}
 *
 * RuoYi R{code:401, msg:"未登录", data:null}
 *     → misikt {code:401, message:"未登录", response:null}
 *
 * 裸返回 T（非 R 包装的）
 *     → misikt {code:1,   message:"成功",  response:T}
 * </pre>
 *
 * <p>code 映射约定：仅 200 → 1（misikt 风格），其他 4xx/5xx 保持原 code 透传，
 * 让 FE 拦截器（request.ts）按现有的 code !== 1 分支处理。
 *
 * <p>注意：basePackages 限 {@code org.dromara.book.controller} 让本 advice 装配到 IoC，
 * URI 前缀过滤保证响应转换不影响 RuoYi 自带端点（advice 装配的物理位置 vs 实际作用范围
 * 用 URI 双保险，因为 GlobalExceptionHandler 在 ruoyi-common-web 包，对 book controller
 * 抛错时它的输出仍要走本 advice 出口转 envelope）。
 *
 * @author backend-dev
 */
@RestControllerAdvice(basePackages = "org.dromara.book.controller")
public class MisiktEnvelopeAdvice implements ResponseBodyAdvice<Object> {

    /** misikt 端点 URI 前缀 — 命中才转 envelope */
    private static final String MISIKT_PREFIX = "/teacher/";

    /** misikt 成功业务码 */
    private static final int MISIKT_CODE_SUCCESS = 1;

    /** misikt 成功 message */
    private static final String MISIKT_MSG_SUCCESS = "成功";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 进入流程 — 是否实际转换看 beforeBodyWrite 的 URI 前缀过滤
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        // 仅 /teacher/ 前缀转 envelope
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return body;
        }
        String uri = servletRequest.getServletRequest().getRequestURI();
        if (uri == null || !uri.startsWith(MISIKT_PREFIX)) {
            return body;
        }

        // 非 JSON 响应（如二进制流 / String 直返）不转换，避免破坏
        if (selectedContentType != null
            && !MediaType.APPLICATION_JSON.includes(selectedContentType)) {
            // String 类型的 JSON 响应被 StringHttpMessageConverter 处理 — 这种端点本模块没有，跳过即可
            return body;
        }

        return toEnvelope(body);
    }

    /**
     * 把 RuoYi R 或裸返回转成 misikt envelope。
     */
    private Map<String, Object> toEnvelope(Object body) {
        Map<String, Object> envelope = new LinkedHashMap<>(3);
        if (body instanceof R<?> r) {
            int ruoyiCode = r.getCode();
            // 200 = RuoYi 成功 → 1 = misikt 成功；其他码透传
            envelope.put("code", ruoyiCode == R.SUCCESS ? MISIKT_CODE_SUCCESS : ruoyiCode);
            envelope.put("message", r.getMsg() == null ? MISIKT_MSG_SUCCESS : r.getMsg());
            envelope.put("response", r.getData());
        } else {
            // 裸返回（Controller 不走 R）— 默认认为成功
            envelope.put("code", MISIKT_CODE_SUCCESS);
            envelope.put("message", MISIKT_MSG_SUCCESS);
            envelope.put("response", body);
        }
        return envelope;
    }
}

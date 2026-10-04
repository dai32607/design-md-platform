package com.designmd.platform.controller;

import com.designmd.platform.common.Result;
import com.designmd.platform.service.AgentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Agent 接口：AI 生成设计规范 + 自动入库闭环。
 * POST /api/agent/generate-spec 需要登录（SecurityConfig 未放行，落入 anyRequest().authenticated()）。
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    public record GenerateSpecRequest(
            @NotBlank(message = "prompt 不能为空") String prompt,
            String brandName,
            List<String> colors
    ) {
    }

    @PostMapping("/generate-spec")
    public Result<Map<String, Object>> generateSpec(
            @RequestBody @Valid GenerateSpecRequest request,
            Authentication authentication) {
        Long userId = currentUserId(authentication);
        return Result.ok(agentService.generateSpec(request.prompt(), request.brandName(), request.colors(), userId));
    }

    private Long currentUserId(Authentication authentication) {
        Object details = authentication == null ? null : authentication.getDetails();
        if (details instanceof Number) {
            return ((Number) details).longValue();
        }
        return null;
    }
}

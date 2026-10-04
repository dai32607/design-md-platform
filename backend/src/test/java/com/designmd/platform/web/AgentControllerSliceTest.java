package com.designmd.platform.web;

import com.designmd.platform.common.BizException;
import com.designmd.platform.config.JwtAuthenticationFilter;
import com.designmd.platform.config.SecurityConfig;
import com.designmd.platform.controller.AgentController;
import com.designmd.platform.service.AgentService;
import com.designmd.platform.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AgentController 接口切片测试（AI 生成设计规范入口）。
 * <p>覆盖点：登录鉴权要求、{@code @Valid} 参数校验（prompt 不能为空）、
 * 正常调用时 userId 与 token 注入、服务层业务异常（如非法色值）映射为 HTTP 400。
 */
@WebMvcTest(AgentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
class AgentControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private AgentService agentService;

    @Test
    @DisplayName("POST /api/agent/generate-spec 匿名访问返回 401 需登录")
    void generateSpecRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/agent/generate-spec")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"极简科技风品牌\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("POST /api/agent/generate-spec prompt 空白：参数校验失败返回 400")
    void blankPromptIsRejectedBeforeService() throws Exception {
        mockMvc.perform(post("/api/agent/generate-spec")
                        .header("Authorization", bearer(9L, "alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("prompt 不能为空"));
    }

    @Test
    @DisplayName("POST /api/agent/generate-spec 正常调用：userId 来自 token，返回落库结果")
    void generateSpecPassesUserIdFromToken() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("brandId", 100L);
        data.put("fileId", 200L);
        data.put("tokenCount", 12);
        data.put("mock", true);
        when(agentService.generateSpec(anyString(), any(), any(), any())).thenReturn(data);

        mockMvc.perform(post("/api/agent/generate-spec")
                        .header("Authorization", bearer(9L, "alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"极简科技风品牌\",\"brandName\":\"Aurora\",\"colors\":[\"#2563EB\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.brandId").value(100))
                .andExpect(jsonPath("$.data.tokenCount").value(12));

        verify(agentService).generateSpec("极简科技风品牌", "Aurora", List.of("#2563EB"), 9L);
    }

    @Test
    @DisplayName("POST /api/agent/generate-spec 非法色值：服务层业务异常映射为 400")
    void invalidColorMappedTo400() throws Exception {
        when(agentService.generateSpec(anyString(), any(), any(), any()))
                .thenThrow(new BizException(400, "非法颜色格式: red"));

        mockMvc.perform(post("/api/agent/generate-spec")
                        .header("Authorization", bearer(9L, "alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"极简科技风品牌\",\"colors\":[\"red\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("非法颜色格式: red"));
    }

    private String bearer(Long userId, String username, String role) {
        return "Bearer " + jwtUtil.generateToken(userId, username, role);
    }
}

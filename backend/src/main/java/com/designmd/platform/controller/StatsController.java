package com.designmd.platform.controller;

import com.designmd.platform.common.Result;
import com.designmd.platform.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 数据统计接口（仅 ADMIN 角色可访问，由 SecurityConfig 统一鉴权）
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /**
     * 汇总指标：品牌总数 / Token 总数 / 用户总数 / DESIGN.md 文件数
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(statsService.overview());
    }

    /**
     * 各品牌 Token 数量（brandName + count，按数量降序）
     */
    @GetMapping("/brand-tokens")
    public Result<List<Map<String, Object>>> brandTokens() {
        return Result.ok(statsService.brandTokens());
    }

    /**
     * Token 分类分布（category + count）
     */
    @GetMapping("/category-dist")
    public Result<List<Map<String, Object>>> categoryDist() {
        return Result.ok(statsService.categoryDist());
    }

    /**
     * 品牌与用户创建趋势（按天聚合，date + brandCount + userCount）
     */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend() {
        return Result.ok(statsService.trend());
    }
}

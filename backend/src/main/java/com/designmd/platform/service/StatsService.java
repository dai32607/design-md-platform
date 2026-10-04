package com.designmd.platform.service;

import java.util.List;
import java.util.Map;

/**
 * 数据统计服务
 */
public interface StatsService {

    /**
     * 汇总指标：品牌总数 / Token 总数 / 用户总数 / DESIGN.md 文件数
     */
    Map<String, Object> overview();

    /**
     * 各品牌 Token 数量（按数量降序）
     */
    List<Map<String, Object>> brandTokens();

    /**
     * Token 分类分布（category + count）
     */
    List<Map<String, Object>> categoryDist();

    /**
     * 品牌与用户创建趋势（按天聚合，date + brandCount + userCount）
     */
    List<Map<String, Object>> trend();
}

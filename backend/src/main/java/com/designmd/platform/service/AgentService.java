package com.designmd.platform.service;

import java.util.List;
import java.util.Map;

public interface AgentService {

    /**
     * AI 生成设计规范闭环：
     * 输入品牌需求描述 -> LLM 生成 DESIGN.md -> 创建品牌 -> 保存规范文件 -> Token 解析入库
     *
     * @param colors 用户指定色板（可为 null/空；非法格式会报错）
     * @return 包含 brandId / brandName / fileId / specText / tokenCount / colorsUsed / mock
     */
    Map<String, Object> generateSpec(String prompt, String brandName, List<String> colors, Long userId);
}

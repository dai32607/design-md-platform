package com.designmd.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.designmd.platform.entity.Brand;
import com.designmd.platform.entity.DesignFile;
import com.designmd.platform.entity.DesignToken;
import com.designmd.platform.entity.User;
import com.designmd.platform.mapper.BrandMapper;
import com.designmd.platform.mapper.DesignFileMapper;
import com.designmd.platform.mapper.DesignTokenMapper;
import com.designmd.platform.mapper.UserMapper;
import com.designmd.platform.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final BrandMapper brandMapper;
    private final DesignTokenMapper designTokenMapper;
    private final DesignFileMapper designFileMapper;
    private final UserMapper userMapper;

    @Override
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("brandCount", safeCount(brandMapper.selectCount(null)));
        data.put("tokenCount", safeCount(designTokenMapper.selectCount(null)));
        data.put("userCount", safeCount(userMapper.selectCount(null)));
        data.put("fileCount", safeCount(designFileMapper.selectCount(null)));
        return data;
    }

    @Override
    public List<Map<String, Object>> brandTokens() {
        QueryWrapper<DesignToken> qw = new QueryWrapper<>();
        qw.select("brand_id", "count(*) AS cnt").groupBy("brand_id");
        List<Map<String, Object>> rows = designTokenMapper.selectMaps(qw);

        Map<Long, String> nameMap = brandMapper.selectList(null).stream()
                .collect(Collectors.toMap(
                        Brand::getId,
                        b -> b.getName() != null && !b.getName().isEmpty()
                                ? b.getName() : String.valueOf(b.getId()),
                        (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long brandId = toLong(get(row, "brand_id"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("brandName", nameMap.getOrDefault(brandId, "未知品牌(#" + brandId + ")"));
            item.put("count", toLong(get(row, "cnt")));
            result.add(item);
        }
        result.sort((a, b) -> Long.compare(toLong(b.get("count")), toLong(a.get("count"))));
        return result;
    }

    @Override
    public List<Map<String, Object>> categoryDist() {
        QueryWrapper<DesignToken> qw = new QueryWrapper<>();
        qw.select("category", "count(*) AS cnt").groupBy("category");
        List<Map<String, Object>> rows = designTokenMapper.selectMaps(qw);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object category = get(row, "category");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", category == null || String.valueOf(category).isEmpty()
                    ? "未分类" : String.valueOf(category));
            item.put("count", toLong(get(row, "cnt")));
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> trend() {
        QueryWrapper<Brand> bqw = new QueryWrapper<>();
        bqw.select("DATE_FORMAT(create_time, '%Y-%m-%d') AS d", "count(*) AS cnt").groupBy("d");
        Map<String, Long> brandByDay = groupByDay(brandMapper.selectMaps(bqw));

        QueryWrapper<User> uqw = new QueryWrapper<>();
        uqw.select("DATE_FORMAT(create_time, '%Y-%m-%d') AS d", "count(*) AS cnt").groupBy("d");
        Map<String, Long> userByDay = groupByDay(userMapper.selectMaps(uqw));

        TreeMap<String, Long> allDays = new TreeMap<>();
        brandByDay.keySet().forEach(d -> allDays.put(d, 0L));
        userByDay.keySet().forEach(d -> allDays.put(d, 0L));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Long> e : allDays.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", e.getKey());
            item.put("brandCount", brandByDay.getOrDefault(e.getKey(), 0L));
            item.put("userCount", userByDay.getOrDefault(e.getKey(), 0L));
            result.add(item);
        }
        return result;
    }

    private Map<String, Long> groupByDay(List<Map<String, Object>> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object d = get(row, "d");
            if (d != null) {
                map.put(String.valueOf(d), toLong(get(row, "cnt")));
            }
        }
        return map;
    }

    /**
     * 大小写无关的 Map 取值：MySQL 列标签为小写、H2 为大写（如 cnt vs CNT），
     * 兼容两种数据库方言，修复 demo(H2) 演示库统计接口返回全 0 的问题。
     */
    private Object get(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    private long safeCount(Long count) {
        return count == null ? 0L : count;
    }

    private long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        return Long.parseLong(String.valueOf(o));
    }
}

package com.designmd.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.designmd.platform.common.PageResult;
import com.designmd.platform.entity.Brand;
import com.designmd.platform.entity.DesignFile;
import com.designmd.platform.entity.DesignToken;
import com.designmd.platform.mapper.BrandMapper;
import com.designmd.platform.mapper.DesignFileMapper;
import com.designmd.platform.mapper.DesignTokenMapper;
import com.designmd.platform.service.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private final BrandMapper brandMapper;
    private final DesignTokenMapper designTokenMapper;
    private final DesignFileMapper designFileMapper;

    @Override
    public PageResult<Brand> pageList(long page, long pageSize) {
        return pageList(page, pageSize, null, null, false, null);
    }

    @Override
    public PageResult<Brand> pageList(long page, long pageSize, String keyword, String source, boolean mine, Long userId) {
        Page<Brand> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Brand> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = escapeLikeKeyword(keyword.trim());
            wrapper.and(w -> w
                    .like(Brand::getName, kw)
                    .or()
                    .like(Brand::getDescription, kw)
            );
        }
        if (source != null && !source.trim().isEmpty()) {
            wrapper.eq(Brand::getSource, source.trim());
        }
        if (mine) {
            wrapper.eq(Brand::getCreatorId, userId);
        }
        wrapper.orderByAsc(Brand::getId);
        Page<Brand> result = brandMapper.selectPage(p, wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    /** LIKE 通配符转义：\ % _ 前加反斜杠（配合 MySQL LIKE 默认 ESCAPE '\'） */
    private String escapeLikeKeyword(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    @Override
    public Brand getById(Long id) {
        Brand brand = brandMapper.selectById(id);
        if (brand == null) {
            throw new IllegalArgumentException("品牌不存在: " + id);
        }
        return brand;
    }

    @Override
    public Brand updateLogoUrl(Long id, String logoUrl) {
        Brand brand = getById(id);
        brand.setLogoUrl(logoUrl);
        brandMapper.updateById(brand);
        return brand;
    }

    /**
     * 生成统一的 Logo 展示地址：指向本地读图接口，v 参数携带版本时间戳用于规避浏览器缓存旧图。
     */
    private String buildLogoUrl(Long id, LocalDateTime time) {
        long version = time == null
                ? System.currentTimeMillis()
                : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return "/api/brands/" + id + "/logo?v=" + version;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Brand updateLogo(Long id, byte[] data, String mime) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Logo 图片内容为空");
        }
        Brand brand = getById(id);
        LocalDateTime now = LocalDateTime.now();
        brand.setLogoData(data);
        brand.setLogoMime((mime == null || mime.trim().isEmpty()) ? "image/png" : mime.trim());
        brand.setLogoUpdateTime(now);
        // logoUrl 统一指向本地读图接口，页面从此不再依赖 uploads 目录与外网图片
        brand.setLogoUrl(buildLogoUrl(id, now));
        brandMapper.updateById(brand);
        return brand;
    }

    @Override
    public Brand createBrand(Brand brand) {
        if (brand.getName() == null || brand.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("品牌名称不能为空");
        }
        if (brand.getSource() == null || brand.getSource().trim().isEmpty()) {
            brand.setSource("custom");
        }
        brand.setName(brand.getName().trim());
        brand.setId(null);
        brand.setCreateTime(null);
        brandMapper.insert(brand);
        return brand;
    }

    @Override
    public Brand updateBrand(Long id, Brand brand) {
        Brand exist = getById(id);
        if (brand.getName() == null || brand.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("品牌名称不能为空");
        }
        exist.setName(brand.getName().trim());
        if (brand.getLogoUrl() != null) {
            // 已入库二进制头像的品牌，logoUrl 统一由后端维护（指向本地读图接口），
            // 防止被手工改成外网地址后离线演示再次破图
            boolean hasStoredLogo = exist.getLogoData() != null && exist.getLogoData().length > 0;
            if (!hasStoredLogo) {
                exist.setLogoUrl(brand.getLogoUrl());
            }
        }
        if (brand.getWebsite() != null) {
            exist.setWebsite(brand.getWebsite());
        }
        if (brand.getDescription() != null) {
            exist.setDescription(brand.getDescription());
        }
        brandMapper.updateById(exist);
        return exist;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBrand(Long id) {
        getById(id);
        designTokenMapper.delete(new LambdaQueryWrapper<DesignToken>()
                .eq(DesignToken::getBrandId, id));
        designFileMapper.delete(new LambdaQueryWrapper<DesignFile>()
                .eq(DesignFile::getBrandId, id));
        brandMapper.deleteById(id);
    }
}

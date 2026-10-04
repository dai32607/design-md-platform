package com.designmd.platform.service;

import com.designmd.platform.common.PageResult;
import com.designmd.platform.entity.Brand;

public interface BrandService {

    PageResult<Brand> pageList(long page, long pageSize);

    /**
     * 品牌分页列表（支持关键词搜索 + 来源筛选 + 只看我创建的）
     *
     * @param keyword  名称/描述模糊关键词，空串视为不传
     * @param source   品牌来源（system/custom），空视为不传
     * @param mine     仅返回当前用户创建的品牌（userId 为 null 时应由调用方保证已校验登录）
     * @param userId   当前登录用户 id（mine=false 时可为 null）
     */
    PageResult<Brand> pageList(long page, long pageSize, String keyword, String source, boolean mine, Long userId);

    Brand getById(Long id);

    Brand updateLogoUrl(Long id, String logoUrl);

    /**
     * 更新品牌 Logo：图片二进制写入数据库，并把 logoUrl 统一指向本地读图接口
     * /api/brands/{id}/logo?v=&lt;版本&gt;（离线演示不依赖 uploads 与外网）。
     *
     * @param id   品牌 id
     * @param data 图片二进制内容
     * @param mime 图片 MIME 类型，缺省按 image/png 处理
     */
    Brand updateLogo(Long id, byte[] data, String mime);

    Brand createBrand(Brand brand);

    Brand updateBrand(Long id, Brand brand);

    void deleteBrand(Long id);
}

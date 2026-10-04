package com.designmd.platform.controller;

import com.designmd.platform.common.Result;
import com.designmd.platform.entity.Brand;
import com.designmd.platform.service.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 品牌管理接口（仅 ADMIN 角色可访问，由 SecurityConfig 统一鉴权）
 */
@RestController
@RequestMapping("/api/admin/brands")
@RequiredArgsConstructor
public class AdminBrandController {

    private final BrandService brandService;

    @PostMapping
    public Result<Brand> create(@RequestBody Brand brand) {
        return Result.ok(brandService.createBrand(brand));
    }

    @PutMapping("/{id}")
    public Result<Brand> update(@PathVariable Long id, @RequestBody Brand brand) {
        return Result.ok(brandService.updateBrand(id, brand));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        brandService.deleteBrand(id);
        return Result.ok();
    }
}

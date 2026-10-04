package com.designmd.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.designmd.platform.common.PageResult;
import com.designmd.platform.entity.Brand;
import com.designmd.platform.mapper.BrandMapper;
import com.designmd.platform.mapper.DesignFileMapper;
import com.designmd.platform.mapper.DesignTokenMapper;
import com.designmd.platform.service.impl.BrandServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BrandService 单元测试（Mockito 模拟 Mapper，不连数据库）。
 * <p>覆盖点：品牌创建校验与 source 归属、Logo 二进制入库与展示地址生成、
 * 预置头像保护、品牌删除的级联清理、分页查询。
 */
@ExtendWith(MockitoExtension.class)
class BrandServiceImplTest {

    @Mock
    private BrandMapper brandMapper;

    @Mock
    private DesignTokenMapper designTokenMapper;

    @Mock
    private DesignFileMapper designFileMapper;

    @InjectMocks
    private BrandServiceImpl brandService;

    private Brand brand(Long id, String name, String source, Long creatorId) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setName(name);
        brand.setSource(source);
        brand.setCreatorId(creatorId);
        return brand;
    }

    @Test
    @DisplayName("createBrand：名称为空时抛 IllegalArgumentException")
    void createBrandRequiresName() {
        assertThatThrownBy(() -> brandService.createBrand(new Brand()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("品牌名称不能为空");
        assertThatThrownBy(() -> brandService.createBrand(brand(null, "   ", null, 9L)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(brandMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createBrand：名称去空格、缺省 source 归为 custom、忽略传入主键与创建时间")
    void createBrandNormalizesFields() {
        Brand input = brand(123L, "  Aurora  ", null, 9L);
        input.setCreateTime(LocalDateTime.now());

        Brand created = brandService.createBrand(input);

        assertThat(created.getName()).isEqualTo("Aurora");
        assertThat(created.getSource()).isEqualTo("custom");
        assertThat(created.getId()).isNull();
        assertThat(created.getCreateTime()).isNull();
        verify(brandMapper).insert(created);
    }

    @Test
    @DisplayName("createBrand：显式指定 source 时保留原值（管理端可建 system 品牌）")
    void createBrandKeepsExplicitSource() {
        Brand created = brandService.createBrand(brand(null, "Aurora", "system", 1L));

        assertThat(created.getSource()).isEqualTo("system");
    }

    @Test
    @DisplayName("updateLogo：图片内容为空时抛 IllegalArgumentException")
    void updateLogoRequiresData() {
        assertThatThrownBy(() -> brandService.updateLogo(5L, null, "image/png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Logo 图片内容为空");
        assertThatThrownBy(() -> brandService.updateLogo(5L, new byte[0], "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(brandMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateLogo：二进制入库并把 logoUrl 指向本地读图接口")
    void updateLogoStoresBinary() {
        when(brandMapper.selectById(5L)).thenReturn(brand(5L, "Aurora", "custom", 9L));
        byte[] data = new byte[] {1, 2, 3};

        Brand updated = brandService.updateLogo(5L, data, "image/jpeg");

        assertThat(updated.getLogoData()).isEqualTo(data);
        assertThat(updated.getLogoMime()).isEqualTo("image/jpeg");
        assertThat(updated.getLogoUpdateTime()).isNotNull();
        assertThat(updated.getLogoUrl()).startsWith("/api/brands/5/logo?v=");
        verify(brandMapper).updateById(updated);
    }

    @Test
    @DisplayName("updateLogo：MIME 为空时默认为 image/png")
    void updateLogoDefaultsToPngMime() {
        when(brandMapper.selectById(5L)).thenReturn(brand(5L, "Aurora", "custom", 9L));

        Brand updated = brandService.updateLogo(5L, new byte[] {1}, "  ");

        assertThat(updated.getLogoMime()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("getById：品牌不存在时抛 IllegalArgumentException")
    void getByIdNotFound() {
        when(brandMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> brandService.getById(9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("品牌不存在: 9");
    }

    @Test
    @DisplayName("updateBrand：已入库二进制头像时不允许改回外链 logoUrl")
    void updateBrandProtectsStoredLogo() {
        Brand exist = brand(4L, "Aurora", "custom", 9L);
        exist.setLogoData(new byte[] {9, 9});
        exist.setLogoUrl("/api/brands/4/logo?v=1");
        when(brandMapper.selectById(4L)).thenReturn(exist);

        Brand patch = new Brand();
        patch.setName("Aurora 2");
        patch.setLogoUrl("https://cdn.example.com/a.png");
        patch.setWebsite("https://aurora.example.com");
        patch.setDescription("极简科技风");

        Brand updated = brandService.updateBrand(4L, patch);

        assertThat(updated.getName()).isEqualTo("Aurora 2");
        assertThat(updated.getLogoUrl()).isEqualTo("/api/brands/4/logo?v=1");
        assertThat(updated.getWebsite()).isEqualTo("https://aurora.example.com");
        assertThat(updated.getDescription()).isEqualTo("极简科技风");
        verify(brandMapper).updateById(exist);
    }

    @Test
    @DisplayName("updateBrand：未入库二进制头像时允许更新 logoUrl")
    void updateBrandAllowsLogoUrlWithoutStoredLogo() {
        when(brandMapper.selectById(4L)).thenReturn(brand(4L, "Aurora", "custom", 9L));

        Brand patch = new Brand();
        patch.setName("Aurora");
        patch.setLogoUrl("https://cdn.example.com/a.png");

        Brand updated = brandService.updateBrand(4L, patch);

        assertThat(updated.getLogoUrl()).isEqualTo("https://cdn.example.com/a.png");
    }

    @Test
    @DisplayName("updateBrand：名称为空时抛 IllegalArgumentException")
    void updateBrandRequiresName() {
        when(brandMapper.selectById(4L)).thenReturn(brand(4L, "Aurora", "custom", 9L));

        assertThatThrownBy(() -> brandService.updateBrand(4L, new Brand()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("品牌名称不能为空");
        verify(brandMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("deleteBrand：级联清理该品牌的 Token 与文件后删除品牌")
    void deleteBrandCascades() {
        when(brandMapper.selectById(3L)).thenReturn(brand(3L, "Aurora", "custom", 9L));

        brandService.deleteBrand(3L);

        verify(designTokenMapper).delete(any());
        verify(designFileMapper).delete(any());
        verify(brandMapper).deleteById(3L);
    }

    @Test
    @DisplayName("deleteBrand：品牌不存在时抛 IllegalArgumentException 且不执行任何删除")
    void deleteBrandNotFound() {
        when(brandMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> brandService.deleteBrand(9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("品牌不存在: 9");
        verify(designTokenMapper, never()).delete(any());
        verify(designFileMapper, never()).delete(any());
        verify(brandMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("pageList：服务端分页返回总数与当前页数据")
    void pageListReturnsPagedResult() {
        Page<Brand> page = new Page<>(1, 10);
        page.setTotal(24);
        page.setRecords(List.of(brand(1L, "Apple", "system", null), brand(2L, "Aurora", "custom", 9L)));
        when(brandMapper.selectPage(Mockito.<Page<Brand>>any(), any())).thenReturn(page);

        PageResult<Brand> result = brandService.pageList(1, 10, "aurora", "custom", true, 9L);

        assertThat(result.getTotal()).isEqualTo(24);
        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getName()).isEqualTo("Apple");
    }
}

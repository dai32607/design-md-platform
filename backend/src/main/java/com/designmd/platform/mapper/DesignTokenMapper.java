package com.designmd.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.designmd.platform.entity.DesignToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DesignTokenMapper extends BaseMapper<DesignToken> {

    @Select("SELECT * FROM design_token WHERE brand_id = #{brandId} AND category = #{category}")
    List<DesignToken> selectByBrandIdAndCategory(@Param("brandId") Long brandId, @Param("category") String category);

    @Select("SELECT * FROM design_token WHERE brand_id = #{brandId}")
    List<DesignToken> selectByBrandId(@Param("brandId") Long brandId);
}

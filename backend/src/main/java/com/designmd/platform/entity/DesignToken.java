package com.designmd.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("design_token")
public class DesignToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long brandId;

    private String category;

    private String tokenName;

    private String tokenValue;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

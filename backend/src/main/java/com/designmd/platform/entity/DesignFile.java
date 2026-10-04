package com.designmd.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("design_file")
public class DesignFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long brandId;

    private String title;

    private String content;

    private String sourceUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

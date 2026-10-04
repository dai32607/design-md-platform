package com.designmd.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand")
public class Brand {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /**
     * Logo 展示地址：统一指向本地读图接口 /api/brands/{id}/logo?v=<版本>，
     * 离线演示包不依赖 uploads 目录、不依赖外网。
     */
    private String logoUrl;

    /**
     * Logo 图片二进制数据（存库），不参与 JSON 序列化，避免列表/详情接口返回超大 base64。
     */
    @JsonIgnore
    private byte[] logoData;

    /** Logo 图片 MIME 类型，如 image/png */
    private String logoMime;

    /** Logo 二进制的最后更新时间，用于读图接口的 ETag / Last-Modified 与 304 协商缓存 */
    private LocalDateTime logoUpdateTime;

    private String source;

    private Long creatorId;

    private String website;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

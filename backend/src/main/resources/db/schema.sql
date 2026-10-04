CREATE DATABASE IF NOT EXISTS design_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE design_db;

CREATE TABLE IF NOT EXISTS brand (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '品牌名称',
    logo_url VARCHAR(500) COMMENT 'Logo 展示地址（统一指向本地读图接口 /api/brands/{id}/logo?v=版本）',
    logo_data MEDIUMBLOB COMMENT 'Logo 图片二进制（存库，离线演示不依赖 uploads 目录与外网）',
    logo_mime VARCHAR(100) COMMENT 'Logo MIME 类型，如 image/png',
    logo_update_time DATETIME NULL COMMENT 'Logo 二进制最后更新时间（ETag/Last-Modified 依据）',
    source VARCHAR(20) NOT NULL DEFAULT 'custom' COMMENT '品牌来源: system=预置品牌 / custom=自定义品牌',
    website VARCHAR(500) COMMENT '官网地址',
    description TEXT COMMENT '品牌描述',
    creator_id BIGINT NULL COMMENT '创建用户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌表';

CREATE TABLE IF NOT EXISTS design_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id BIGINT NOT NULL COMMENT '品牌ID',
    category VARCHAR(50) NOT NULL COMMENT '分类: COLORS/TYPOGRAPHY/SPACING/COMPONENTS',
    token_name VARCHAR(200) NOT NULL COMMENT 'Token名称',
    token_value VARCHAR(500) NOT NULL COMMENT 'Token值',
    description VARCHAR(500) COMMENT '描述说明',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_brand_id (brand_id),
    INDEX idx_brand_category (brand_id, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设计Token表';

CREATE TABLE IF NOT EXISTS design_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id BIGINT NOT NULL COMMENT '品牌ID',
    title VARCHAR(200) NOT NULL COMMENT '文件标题',
    content LONGTEXT COMMENT 'Markdown内容',
    source_url VARCHAR(500) COMMENT '来源URL',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_brand_id (brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DESIGN.md文件表';

CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密密码',
    nickname VARCHAR(50) COMMENT '昵称',
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色: USER=普通用户 / ADMIN=管理员',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

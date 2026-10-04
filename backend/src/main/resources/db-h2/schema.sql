-- H2 演示库建表脚本（由 MySQL design_db 结构适配生成）
CREATE TABLE IF NOT EXISTS brand (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    logo_url VARCHAR(500),
    logo_data BLOB,
    logo_mime VARCHAR(100),
    logo_update_time TIMESTAMP,
    source VARCHAR(20) NOT NULL DEFAULT 'custom',
    website VARCHAR(500),
    description CLOB,
    creator_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS design_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id BIGINT NOT NULL,
    category VARCHAR(50) NOT NULL,
    token_name VARCHAR(200) NOT NULL,
    token_value VARCHAR(500) NOT NULL,
    description VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_token_brand_id ON design_token (brand_id);
CREATE INDEX IF NOT EXISTS idx_token_brand_category ON design_token (brand_id, category);

CREATE TABLE IF NOT EXISTS design_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content CLOB,
    source_url VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_file_brand_id ON design_file (brand_id);

CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    nickname VARCHAR(50),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_username UNIQUE (username)
);

-- 兼容 MySQL 的 DATE_FORMAT 函数（供统计接口使用）
CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR 'com.designmd.platform.demo.H2CompatFunctions.dateFormat';

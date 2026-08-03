-- 用户表（MySQL/H2 兼容）
CREATE TABLE IF NOT EXISTS sys_user (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username         VARCHAR(64)  NOT NULL UNIQUE,
    password         VARCHAR(128) NOT NULL,
    salt             VARCHAR(64)  NOT NULL,
    roles            VARCHAR(128),
    status           INT          DEFAULT 1,
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    bucket           VARCHAR(128),
    minio_access_key VARCHAR(64),
    minio_secret_key VARCHAR(128)
);

-- 兼容已存在的旧库：仅当列不存在时添加（H2/MariaDB 支持 IF NOT EXISTS）
-- bucket            该用户独占的 MinIO 桶名，如 user-1
-- minio_access_key  应用签发给该用户的 S3 access key（/s3/* 代理验签用）
-- minio_secret_key  对应 secret（明文存：SigV4 校验需作 HMAC key，与 MinIO 自身存储方式一致）
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS bucket VARCHAR(128);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS minio_access_key VARCHAR(64);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS minio_secret_key VARCHAR(128);


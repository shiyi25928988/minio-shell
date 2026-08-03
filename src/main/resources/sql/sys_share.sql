-- 文件分享记录表（MySQL/H2 兼容）
-- 每条记录对应一个分享令牌：凭 token 可公开下载指定对象（可加密码/有效期/次数限制）
CREATE TABLE IF NOT EXISTS sys_share (
    id             BIGINT        AUTO_INCREMENT PRIMARY KEY,
    token          VARCHAR(64)   NOT NULL UNIQUE,
    bucket         VARCHAR(128)  NOT NULL,
    object_name    VARCHAR(1024) NOT NULL,
    filename       VARCHAR(255),
    size           BIGINT,
    creator_id     BIGINT        NOT NULL,
    expire_time    DATETIME,
    password_hash  VARCHAR(128),
    password_salt  VARCHAR(64),
    max_count      INT,
    download_count INT           DEFAULT 0,
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_share_creator ON sys_share (creator_id);

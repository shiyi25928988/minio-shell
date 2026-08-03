package yi.shi.plinth.db.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String salt;
    /** 角色列表，逗号分隔，如 "user,admin" */
    private String roles;
    /** 1=正常 0=禁用 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 该用户独占的 MinIO 桶名，如 user-1 */
    private String bucket;
    /** 应用签发给该用户的 S3 access key（/s3/* 代理验签用） */
    private String minioAccessKey;
    /** 对应 secret（明文存：SigV4 校验需作 HMAC key） */
    private String minioSecretKey;
}

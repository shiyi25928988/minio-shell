package yi.shi.plinth.db.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件分享记录：凭 token 公开下载指定 MinIO 对象（可加密码/有效期/次数限制）。
 */
@Data
public class Share {
    private Long id;
    private String token;
    private String bucket;
    private String objectName;
    private String filename;
    private Long size;
    private Long creatorId;
    private LocalDateTime expireTime;
    private String passwordHash;
    private String passwordSalt;
    private Integer maxCount;
    private Integer downloadCount;
    private LocalDateTime createTime;
}

package yi.shi.plinth.share.dto;

import lombok.Data;

/**
 * 分享信息（创建返回 / 列表项 / 访问校验返回）。
 */
@Data
public class ShareInfo {
    private String token;
    /** 相对访问路径，前端拼接 origin 后即可复制 */
    private String url;
    private String filename;
    private long size;
    /** 过期时间 ISO，null=永久 */
    private String expireTime;
    private boolean hasPassword;
    /** null=无限 */
    private Integer maxCount;
    private int downloadCount;
    private String createTime;
}

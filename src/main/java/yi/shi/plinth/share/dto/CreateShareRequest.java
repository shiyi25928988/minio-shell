package yi.shi.plinth.share.dto;

import lombok.Data;

/**
 * 创建分享请求（JSON body）。
 */
@Data
public class CreateShareRequest {
    /** 必填：对象路径 */
    private String path;
    /** 可选：admin 指定他人桶 */
    private String bucket;
    /** 可选：过期天数（&gt;0 生效，null/0=永久） */
    private Integer expireDays;
    /** 可选：访问密码 */
    private String password;
    /** 可选：最大下载次数（&gt;0 生效，null/0=无限） */
    private Integer maxCount;
}

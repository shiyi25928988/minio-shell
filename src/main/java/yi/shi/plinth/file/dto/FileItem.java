package yi.shi.plinth.file.dto;

import lombok.Data;

/**
 * 文件浏览器列表项：既可能是对象（文件）也可能是前缀（文件夹）。
 *
 * @param name         桶内完整路径（对象名或前缀，前缀以 '/' 结尾）
 * @param display      末段显示名（去掉前缀路径与结尾 '/'）
 * @param size         字节数（文件夹为 0）
 * @param lastModified 最后修改时间 ISO 字符串（文件夹为空）
 * @param dir          是否为文件夹
 */
@Data
public class FileItem {
    private String name;
    private String display;
    private long size;
    private String lastModified;
    private boolean dir;
}

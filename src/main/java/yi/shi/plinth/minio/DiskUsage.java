package yi.shi.plinth.minio;

/**
 * MinIO 集群磁盘用量汇总。
 *
 * <p>由 {@link MinioService#getDiskUsage()} 通过 Admin API {@code getServerInfo} 聚合所有磁盘得出，
 * 用于在文件浏览器顶部展示"磁盘剩余空间"。
 *
 * @param totalBytes         所有磁盘总容量之和（字节）
 * @param usedBytes          所有磁盘已用空间之和（字节）
 * @param availableBytes     所有磁盘可用空间之和（字节）
 * @param totalDisks         磁盘总数
 * @param onlineDisks        在线磁盘数
 * @param utilizationPercent 已用占比（%，保留两位小数）
 */
public record DiskUsage(long totalBytes, long usedBytes, long availableBytes,
                        int totalDisks, int onlineDisks, double utilizationPercent) {
}

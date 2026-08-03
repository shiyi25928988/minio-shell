package yi.shi.plinth.db.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import yi.shi.plinth.db.entity.Share;

import java.util.List;

@Mapper
public interface ShareMapper {

    @Insert("INSERT INTO sys_share (token, bucket, object_name, filename, size, creator_id, expire_time, "
            + "password_hash, password_salt, max_count, download_count, create_time) "
            + "VALUES (#{token}, #{bucket}, #{objectName}, #{filename}, #{size}, #{creatorId}, #{expireTime}, "
            + "#{passwordHash}, #{passwordSalt}, #{maxCount}, #{downloadCount}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Share share);

    @Select("SELECT * FROM sys_share WHERE token = #{token}")
    Share findByToken(@Param("token") String token);

    @Select("SELECT * FROM sys_share WHERE creator_id = #{creatorId} ORDER BY id DESC")
    List<Share> listByCreator(@Param("creatorId") Long creatorId);

    @Delete("DELETE FROM sys_share WHERE token = #{token} AND creator_id = #{creatorId}")
    int deleteByTokenAndCreator(@Param("token") String token, @Param("creatorId") Long creatorId);

    /**
     * 原子地消费一次下载：仅当未过期且未超次数时 download_count+1。
     * 返回受影响行数：1=允许下载，0=已过期/超次数/不存在（由调用方区分）。
     */
    @Update("UPDATE sys_share SET download_count = download_count + 1 WHERE token = #{token} "
            + "AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP) "
            + "AND (max_count IS NULL OR download_count < max_count)")
    int incrementDownloadCount(@Param("token") String token);
}

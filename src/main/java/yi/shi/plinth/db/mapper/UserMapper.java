package yi.shi.plinth.db.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import yi.shi.plinth.db.entity.User;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    User findById(@Param("id") Long id);

    @Select("SELECT * FROM sys_user WHERE minio_access_key = #{accessKey}")
    User findByAccessKey(@Param("accessKey") String accessKey);

    @Insert("INSERT INTO sys_user (username, password, salt, roles, status, create_time, update_time, "
            + "bucket, minio_access_key, minio_secret_key) "
            + "VALUES (#{username}, #{password}, #{salt}, #{roles}, #{status}, #{createTime}, #{updateTime}, "
            + "#{bucket}, #{minioAccessKey}, #{minioSecretKey})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE sys_user SET password=#{password}, salt=#{salt}, roles=#{roles}, "
            + "status=#{status}, update_time=#{updateTime}, "
            + "bucket=#{bucket}, minio_access_key=#{minioAccessKey}, minio_secret_key=#{minioSecretKey} "
            + "WHERE id=#{id}")
    int update(User user);

    @Update("UPDATE sys_user SET bucket=#{bucket}, minio_access_key=#{minioAccessKey}, "
            + "minio_secret_key=#{minioSecretKey}, update_time=#{updateTime} WHERE id=#{id}")
    int updateMinioCredentials(User user);

    @Delete("DELETE FROM sys_user WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT * FROM sys_user ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<User> findPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM sys_user")
    long count();
}

package yi.shi.db.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import yi.shi.db.entity.Account;

@Mapper
public interface AccountMapper {

    @Select("SELECT id, name FROM account WHERE id = #{id}")
    Account getById(@Param("id") Long id);

    @Insert("INSERT INTO account (id, name) VALUES (#{id}, #{name})")
    int insert(@Param("id") Long id, @Param("name") String name);
}

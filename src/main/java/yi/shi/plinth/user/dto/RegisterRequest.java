package yi.shi.plinth.user.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    /** 可选，默认角色 "user" */
    private String roles;
}

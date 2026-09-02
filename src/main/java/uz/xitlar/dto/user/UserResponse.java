package uz.xitlar.dto.user;

import lombok.Builder;
import lombok.Getter;
import uz.xitlar.enums.Role;

@Builder
@Getter
public class UserResponse {
    private Integer id;
    private String firstName;
    private String lastName;
    private String username;
    private Role role;
}

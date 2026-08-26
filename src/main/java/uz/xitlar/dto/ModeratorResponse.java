package uz.xitlar.dto;

import lombok.Builder;
import lombok.Getter;
import uz.xitlar.enums.Role;

@Builder
@Getter
public class ModeratorResponse {
    private Integer id;
    private String firstName;
    private String lastName;
    private String username;
    private Role role;
}

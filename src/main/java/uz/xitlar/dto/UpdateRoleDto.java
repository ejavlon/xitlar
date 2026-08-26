package uz.xitlar.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.xitlar.enums.Role;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class UpdateRoleDto {
    @NotNull(message = "Role must not be null")
    private Role role;
}

package uz.xitlar.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ResetPasswordDto {
    @NotBlank(message = "New password must not be blank")
    private String newPassword;
}

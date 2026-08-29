package uz.xitlar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(min = 4, max = 100, message = "New password must be between 4 and 100 characters")
    private String newPassword;
}

package uz.xitlar.dto.artist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.xitlar.enums.Genre;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ArtistCreateDto {

    @NotBlank(message = "Artist name must not be blank")
    private String name;

    @NotNull(message = "Artist genre must not be null")
    private Genre genre;

}

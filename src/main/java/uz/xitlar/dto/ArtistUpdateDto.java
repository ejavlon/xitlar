package uz.xitlar.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.xitlar.enums.Genre;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ArtistUpdateDto {

    @Pattern(regexp = ".*\\S.*", message = "Artist name must not be blank")
    private String name;

    private Genre genre;

}

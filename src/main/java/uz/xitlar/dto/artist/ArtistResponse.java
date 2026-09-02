package uz.xitlar.dto.artist;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.xitlar.dto.image.ImageResponse;
import uz.xitlar.enums.Genre;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ArtistResponse {

    private Integer id;
    private String name;
    private Integer countOfTrack;
    private Genre genre;
    private Integer voteCount;
    private Double averageRating;
    private ImageResponse image;
    private Integer userRating; // current user's rating (1-5), null if not voted

}

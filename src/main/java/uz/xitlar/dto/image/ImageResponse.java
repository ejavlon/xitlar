package uz.xitlar.dto.image;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageResponse {
    private Integer id;
    private String originalName;
    private String contentType;
    private Long size;
    private String url;
}

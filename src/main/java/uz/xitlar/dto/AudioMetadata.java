package uz.xitlar.dto;

import lombok.Builder;
import lombok.Data;
import uz.xitlar.enums.AudioFormat;

@Data
@Builder
public class AudioMetadata {
    private String storedName;
    private String originalFileName;
    private Long size;
    private String contentType;
    private Integer duration;
    private Integer bitrate;
    private Integer sampleRate;
    private AudioFormat format;
}

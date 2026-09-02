package uz.xitlar.dto.music;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import uz.xitlar.enums.UploadStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BulkMusicUploadItemResponse {

    String fileName;

    UploadStatus status;

    Integer musicId;

    String title;

    String error;
}

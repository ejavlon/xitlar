package uz.xitlar.dto.music;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BulkMusicUploadResponse {

    int total;

    int successCount;

    int duplicateCount;

    int failedCount;

    List<BulkMusicUploadItemResponse> results;
}

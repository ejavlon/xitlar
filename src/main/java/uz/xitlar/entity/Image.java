package uz.xitlar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "images")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Image extends BaseEntity {

    @Column(name = "original_name", nullable = false)
    String originalName;

    @Column(name = "stored_name", nullable = false, unique = true)
    String storedName;

    @Column(name = "content_type", nullable = false)
    String contentType;

    @Column(nullable = false)
    Long size;

    @Column(name = "directory_path", nullable = false)
    String directoryPath;

    @Column(nullable = false)
    String url;
}


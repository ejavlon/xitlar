package uz.xitlar.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.ImageResponse;
import uz.xitlar.dto.ResponseApi;
import uz.xitlar.entity.Image;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.exception.FileStorageException;
import uz.xitlar.exception.UnsupportedFileTypeException;
import uz.xitlar.repository.ImageRepository;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ImageStorageService {

    private final ImageRepository imageRepository;
    private final Path imageStorageDir;
    private final List<String> allowedContentTypes = Arrays.asList("image/jpeg", "image/png", "image/webp");

    public ImageStorageService(
            ImageRepository imageRepository,
            @Value("${app.storage.path}") String storagePath
    ) {
        this.imageRepository = imageRepository;
        this.imageStorageDir = Paths.get(storagePath).resolve("images").normalize().toAbsolutePath();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(imageStorageDir);
        } catch (IOException e) {
            throw new FileStorageException("Could not create storage directory for images: " + imageStorageDir, e);
        }
    }

    @Transactional
    public ResponseApi<ImageResponse> uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Failed to store empty file");
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedContentTypes.contains(contentType.toLowerCase())) {
            throw new UnsupportedFileTypeException("Unsupported file type. Only JPEG, PNG, and WebP are allowed.");
        }

        // Extract safe extension based on content type
        String extension;
        if (contentType.equalsIgnoreCase("image/png")) {
            extension = "png";
        } else if (contentType.equalsIgnoreCase("image/webp")) {
            extension = "webp";
        } else {
            extension = "jpg";
        }

        String uuid = UUID.randomUUID().toString();
        String storedName = uuid + "." + extension;

        // Sanitize original filename (keep only metadata, do not use for path creation)
        String originalFilename = file.getOriginalFilename();
        String sanitizedOriginalName = originalFilename != null ? Paths.get(originalFilename).getFileName().toString() : "unknown";

        Path targetLocation = imageStorageDir.resolve(storedName).normalize();
        if (!targetLocation.startsWith(imageStorageDir)) {
            throw new FileStorageException("Path traversal attempt detected");
        }

        // Store physically first
        try {
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new FileStorageException("Failed to store file on filesystem: " + storedName, e);
        }

        // Persist to database
        try {
            Image image = Image.builder()
                    .originalName(sanitizedOriginalName)
                    .storedName(storedName)
                    .contentType(contentType)
                    .size(file.getSize())
                    .directoryPath("images")
                    .url("images/" + storedName)
                    .build();

            Image saved = imageRepository.save(image);
            return ResponseApi.<ImageResponse>builder()
                    .success(true)
                    .message("Image successfully uploaded")
                    .data(toResponse(saved))
                    .build();
        } catch (Exception e) {
            // Database failed, delete the orphaned physical file to keep consistency
            try {
                Files.deleteIfExists(targetLocation);
            } catch (IOException ioException) {
                log.error("Failed to delete orphaned physical file after database failure: {}", targetLocation, ioException);
            }
            throw new FileStorageException("Failed to save image metadata to database", e);
        }
    }

    public Resource loadImageAsResource(Integer id) {
        Image image = imageRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Image not found with ID: " + id));

        try {
            Path filePath = imageStorageDir.resolve(image.getStoredName()).normalize();
            if (!filePath.startsWith(imageStorageDir)) {
                throw new SecurityException("Unauthorized path access");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new DataNotFoundException("Image file not found on filesystem: " + image.getStoredName());
            }
        } catch (MalformedURLException e) {
            throw new FileStorageException("Malformed URL for image path: " + image.getStoredName(), e);
        }
    }

    public Image getImageEntityOrThrow(Integer id) {
        return imageRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Image not found with ID: " + id));
    }

    @Transactional
    public ResponseApi<Void> deleteImage(Integer id) {
        Image image = imageRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Image not found with ID: " + id));

        // Delete from database first
        imageRepository.delete(image);

        // Delete from filesystem
        deletePhysicalFile(image);

        return ResponseApi.<Void>builder()
                .success(true)
                .message("Image successfully deleted")
                .build();
    }

    public void deletePhysicalFile(Image image) {
        if (image == null || image.getStoredName() == null) {
            return;
        }
        Path filePath = imageStorageDir.resolve(image.getStoredName()).normalize();
        if (!filePath.startsWith(imageStorageDir)) {
            throw new SecurityException("Unauthorized path access");
        }
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Failed to delete physical file: {}", filePath, e);
        }
    }

    public ImageResponse toResponse(Image image) {
        return ImageResponse.builder()
                .id(image.getId())
                .originalName(image.getOriginalName())
                .contentType(image.getContentType())
                .size(image.getSize())
                .url("/api/v1/images/" + image.getId())
                .build();
    }
}

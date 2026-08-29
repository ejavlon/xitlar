package uz.xitlar.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import uz.xitlar.dto.ImageResponse;
import uz.xitlar.dto.ResponseApi;
import uz.xitlar.entity.Image;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.exception.FileStorageException;
import uz.xitlar.exception.UnsupportedFileTypeException;
import uz.xitlar.repository.ImageRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ImageStorageServiceTest {

    @Autowired
    private ImageStorageService imageStorageService;

    @Autowired
    private ImageRepository imageRepository;

    @Value("${app.storage.path}")
    private String storagePath;

    private Path imageStorageDir;

    @BeforeEach
    void setUp() {
        this.imageStorageDir = Paths.get(storagePath).resolve("images").normalize().toAbsolutePath();
    }

    @Test
    void testUploadImage_Success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-image.png",
                "image/png",
                "dummy image content".getBytes()
        );

        ResponseApi<ImageResponse> response = imageStorageService.uploadImage(file);

        assertTrue(response.getSuccess());
        assertNotNull(response.getData());
        assertNotNull(response.getData().getId());
        assertEquals("test-image.png", response.getData().getOriginalName());
        assertEquals("image/png", response.getData().getContentType());
        assertEquals("/api/v1/images/" + response.getData().getId(), response.getData().getUrl());

        // Verify physical file exists
        Optional<Image> imageOpt = imageRepository.findById(response.getData().getId());
        assertTrue(imageOpt.isPresent());
        Image image = imageOpt.get();
        Path filePath = imageStorageDir.resolve(image.getStoredName());
        assertTrue(Files.exists(filePath));

        // Clean up file
        Files.deleteIfExists(filePath);
    }

    @Test
    void testUploadImage_EmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertThrows(FileStorageException.class, () ->
                imageStorageService.uploadImage(emptyFile)
        );
    }

    @Test
    void testUploadImage_UnsupportedFileType() {
        MockMultipartFile txtFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "some text".getBytes()
        );

        assertThrows(UnsupportedFileTypeException.class, () ->
                imageStorageService.uploadImage(txtFile)
        );
    }

    @Test
    void testLoadImageAsResource_Success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        ResponseApi<ImageResponse> response = imageStorageService.uploadImage(file);
        Integer id = response.getData().getId();

        Resource resource = imageStorageService.loadImageAsResource(id);
        assertNotNull(resource);
        assertTrue(resource.exists());

        // Clean up
        Optional<Image> imageOpt = imageRepository.findById(id);
        if (imageOpt.isPresent()) {
            Files.deleteIfExists(imageStorageDir.resolve(imageOpt.get().getStoredName()));
        }
    }

    @Test
    void testLoadImageAsResource_NotFound() {
        assertThrows(DataNotFoundException.class, () ->
                imageStorageService.loadImageAsResource(9999)
        );
    }

    @Test
    void testDeleteImage_Success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "delete-me.webp",
                "image/webp",
                "webp content".getBytes()
        );

        ResponseApi<ImageResponse> response = imageStorageService.uploadImage(file);
        Integer id = response.getData().getId();

        Optional<Image> imageOptBefore = imageRepository.findById(id);
        assertTrue(imageOptBefore.isPresent());
        Path filePath = imageStorageDir.resolve(imageOptBefore.get().getStoredName());
        assertTrue(Files.exists(filePath));

        // Delete
        ResponseApi<Void> deleteResponse = imageStorageService.deleteImage(id);
        assertTrue(deleteResponse.getSuccess());

        // Verify DB is deleted
        Optional<Image> imageOptAfter = imageRepository.findById(id);
        assertFalse(imageOptAfter.isPresent());

        // Verify physical file is deleted
        assertFalse(Files.exists(filePath));
    }
}

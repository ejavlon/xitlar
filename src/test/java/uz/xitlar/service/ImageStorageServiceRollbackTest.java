package uz.xitlar.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import uz.xitlar.exception.FileStorageException;
import uz.xitlar.repository.ImageRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class ImageStorageServiceRollbackTest {

    @Autowired
    private ImageStorageService imageStorageService;

    @MockitoBean
    private ImageRepository imageRepository;

    @Value("${app.storage.path}")
    private String storagePath;

    private Path imageStorageDir;

    @BeforeEach
    void setUp() {
        this.imageStorageDir = Paths.get(storagePath).resolve("images").normalize().toAbsolutePath();
    }

    @Test
    void testUploadImage_DbFailureRollback() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "rollback-test.png",
                "image/png",
                "dummy rollback test content".getBytes()
        );

        // Force repository to throw exception on save
        when(imageRepository.save(any())).thenThrow(new RuntimeException("Database connection lost"));

        // Verify that FileStorageException is thrown
        assertThrows(FileStorageException.class, () ->
                imageStorageService.uploadImage(file)
        );

        // Since it's mock, we cannot easily retrieve the generated UUID storedName from the database.
        // However, we can verify that the directory contains no files matching our test content or no newly created files.
        // Even simpler: since the file name is uuid-based, we can inspect the imageStorageDir.
        // If there was an orphaned file, it would remain in the directory.
        // Let's assert that there are no files in the directory containing "dummy rollback test content".
        long count = Files.walk(imageStorageDir)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    try {
                        String content = Files.readString(path);
                        return content.contains("dummy rollback test content");
                    } catch (IOException e) {
                        return false;
                    }
                })
                .count();

        // Count should be 0 because the file was deleted on rollback
        org.junit.jupiter.api.Assertions.assertEquals(0, count);
    }
}

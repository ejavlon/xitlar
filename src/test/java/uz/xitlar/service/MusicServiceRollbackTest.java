package uz.xitlar.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uz.xitlar.dto.MusicCreateDto;
import uz.xitlar.repository.MusicRepository;
import uz.xitlar.util.AudioTestHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class MusicServiceRollbackTest {

    @Autowired
    private MusicService musicService;

    @MockitoBean
    private MusicRepository musicRepository;

    @Value("${app.storage.path}")
    private String storagePath;

    private Path audioStorageDir;

    @BeforeEach
    void setUp() {
        this.audioStorageDir = Paths.get(storagePath).resolve("audio").normalize().toAbsolutePath();
    }

    @Test
    void testCreateMusic_DbFailureRollback_CleansUpNewPhysicalFile() throws IOException {
        byte[] mp3Bytes = AudioTestHelper.createMinimalValidMp3();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "rollback-test.mp3",
                "audio/mpeg",
                mp3Bytes
        );

        MusicCreateDto dto = MusicCreateDto.builder()
                .title("Rollback Test Song")
                .build();

        // Simulate database persistence failure
        when(musicRepository.save(any())).thenThrow(new RuntimeException("Database error on commit"));

        assertThrows(RuntimeException.class, () ->
                musicService.createMusic(dto, file)
        );

        // Verify that no orphaned file with the test bytes exists in audioStorageDir
        long count = Files.walk(audioStorageDir)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    try {
                        return Files.size(path) == mp3Bytes.length;
                    } catch (IOException e) {
                        return false;
                    }
                })
                .count();

        assertEquals(0, count, "New physical file must be deleted upon transaction rollback");
    }
}

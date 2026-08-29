package uz.xitlar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.dto.music.MusicCreateDto;
import uz.xitlar.dto.music.MusicResponse;
import uz.xitlar.entity.Music;
import uz.xitlar.exception.GlobalExceptionHandler;
import uz.xitlar.repository.MusicRepository;
import uz.xitlar.service.AudioProcessingService;
import uz.xitlar.service.MusicService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class MusicControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MusicService musicService;

    @InjectMocks
    private MusicController musicController;

    @TempDir
    Path tempFolder;

    private Path sampleAudioPath;

    @BeforeEach
    void setUp() throws IOException {
        mockMvc = MockMvcBuilders.standaloneSetup(musicController)
                .setMessageConverters(
                        new org.springframework.http.converter.ByteArrayHttpMessageConverter(),
                        new org.springframework.http.converter.StringHttpMessageConverter(),
                        new org.springframework.http.converter.ResourceHttpMessageConverter(),
                        new org.springframework.http.converter.ResourceRegionHttpMessageConverter(),
                        new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter()
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleAudioPath = tempFolder.resolve("test-track.mp3");
        // Create a 2048-byte dummy file
        byte[] audioBytes = new byte[2048];
        for (int i = 0; i < audioBytes.length; i++) {
            audioBytes[i] = (byte) (i % 128);
        }
        Files.write(sampleAudioPath, audioBytes);
    }

    @Test
    void streamAudio_NoRange_Returns200AndFullLength() throws Exception {
        org.springframework.core.io.Resource resource = new org.springframework.core.io.ByteArrayResource(Files.readAllBytes(sampleAudioPath));
        org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> response = org.springframework.http.ResponseEntity.ok()
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentLength(2048)
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .body(resource);

        when(musicService.streamAudio(eq(1), any(HttpHeaders.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/musics/1/audio"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "2048"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/mpeg"))
                .andExpect(content().bytes(Files.readAllBytes(sampleAudioPath)));
    }

    @Test
    void streamAudio_ValidRange_Returns206PartialContent() throws Exception {
        byte[] allBytes = Files.readAllBytes(sampleAudioPath);
        byte[] expectedSlice = java.util.Arrays.copyOfRange(allBytes, 0, 1024);
        org.springframework.core.io.Resource partialResource = new org.springframework.core.io.ByteArrayResource(expectedSlice);

        org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> response = org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes 0-1023/2048")
                .contentLength(1024)
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .body(partialResource);

        when(musicService.streamAudio(eq(1), any(HttpHeaders.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/musics/1/audio")
                        .header(HttpHeaders.RANGE, "bytes=0-1023"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-1023/2048"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "1024"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/mpeg"))
                .andExpect(content().bytes(expectedSlice));
    }

    @Test
    void streamAudio_UnsatisfiableRange_Returns416() throws Exception {
        org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> response = org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes */2048")
                .build();

        when(musicService.streamAudio(eq(1), any(HttpHeaders.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/musics/1/audio")
                        .header(HttpHeaders.RANGE, "bytes=99999999-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */2048"));
    }

    @Test
    void createMusic_Validation_BlankTitle_Returns400() throws Exception {
        MusicCreateDto createDto = MusicCreateDto.builder().title("").build();
        MockMultipartFile dataPart = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(createDto));
        MockMultipartFile filePart = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "test".getBytes());

        mockMvc.perform(multipart("/api/v1/musics")
                        .file(dataPart)
                        .file(filePart))
                .andExpect(status().isBadRequest());
    }
}

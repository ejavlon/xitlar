package uz.xitlar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uz.xitlar.dto.LyricsCreateDto;
import uz.xitlar.dto.LyricsResponse;
import uz.xitlar.dto.LyricsUpdateDto;
import uz.xitlar.dto.ResponseApi;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.exception.DuplicateEntityException;
import uz.xitlar.exception.GlobalExceptionHandler;
import uz.xitlar.service.LyricsService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class LyricsControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private LyricsService lyricsService;

    @InjectMocks
    private LyricsController lyricsController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(lyricsController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void create_Success_Returns201() throws Exception {
        LyricsCreateDto createDto = LyricsCreateDto.builder()
                .musicId(1)
                .text("Test text")
                .language("uz")
                .isSynced(false)
                .build();

        LyricsResponse responseDto = LyricsResponse.builder()
                .id(1)
                .musicId(1)
                .text("Test text")
                .language("uz")
                .isSynced(false)
                .musicTitle("Sample Song")
                .build();

        when(lyricsService.createLyrics(any(LyricsCreateDto.class)))
                .thenReturn(ResponseApi.<LyricsResponse>builder().success(true).data(responseDto).build());

        mockMvc.perform(post("/api/v1/lyrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.musicTitle").value("Sample Song"));
    }

    @Test
    void create_Duplicate_Returns409() throws Exception {
        LyricsCreateDto createDto = LyricsCreateDto.builder()
                .musicId(1)
                .text("Test text")
                .language("uz")
                .isSynced(false)
                .build();

        when(lyricsService.createLyrics(any(LyricsCreateDto.class)))
                .thenThrow(new DuplicateEntityException("Lyrics already exists for this music"));

        mockMvc.perform(post("/api/v1/lyrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isConflict());
    }

    @Test
    void create_BlankText_Returns400() throws Exception {
        LyricsCreateDto createDto = LyricsCreateDto.builder()
                .musicId(1)
                .text("")
                .language("uz")
                .isSynced(false)
                .build();

        mockMvc.perform(post("/api/v1/lyrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_InvalidLanguage_Returns400() throws Exception {
        LyricsCreateDto createDto = LyricsCreateDto.builder()
                .musicId(1)
                .text("Test")
                .language("fr")
                .isSynced(false)
                .build();

        mockMvc.perform(post("/api/v1/lyrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_MissingMusicId_Returns400() throws Exception {
        LyricsCreateDto createDto = LyricsCreateDto.builder()
                .text("Test")
                .language("uz")
                .isSynced(false)
                .build();

        mockMvc.perform(post("/api/v1/lyrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_ZeroMusicId_Returns400() throws Exception {
        LyricsCreateDto createDto = LyricsCreateDto.builder()
                .musicId(0)
                .text("Test")
                .language("uz")
                .isSynced(false)
                .build();

        mockMvc.perform(post("/api/v1/lyrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_NegativeMusicId_Returns400() throws Exception {
        LyricsCreateDto createDto = LyricsCreateDto.builder()
                .musicId(-1)
                .text("Test")
                .language("uz")
                .isSynced(false)
                .build();

        mockMvc.perform(post("/api/v1/lyrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_Success_Returns200() throws Exception {
        LyricsUpdateDto updateDto = LyricsUpdateDto.builder()
                .text("Updated text")
                .language("en")
                .isSynced(false)
                .build();

        LyricsResponse responseDto = LyricsResponse.builder()
                .id(1)
                .musicId(1)
                .text("Updated text")
                .language("en")
                .isSynced(false)
                .build();

        when(lyricsService.updateLyrics(eq(1), any(LyricsUpdateDto.class)))
                .thenReturn(ResponseApi.<LyricsResponse>builder().success(true).data(responseDto).build());

        mockMvc.perform(put("/api/v1/lyrics/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.text").value("Updated text"));
    }

    @Test
    void update_NotFound_Returns404() throws Exception {
        LyricsUpdateDto updateDto = LyricsUpdateDto.builder()
                .text("Updated text")
                .language("en")
                .isSynced(false)
                .build();

        when(lyricsService.updateLyrics(eq(999), any(LyricsUpdateDto.class)))
                .thenThrow(new DataNotFoundException("Lyrics not found"));

        mockMvc.perform(put("/api/v1/lyrics/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_BlankText_Returns400() throws Exception {
        LyricsUpdateDto updateDto = LyricsUpdateDto.builder()
                .text("")
                .language("en")
                .isSynced(false)
                .build();

        mockMvc.perform(put("/api/v1/lyrics/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_Success() throws Exception {
        LyricsResponse responseDto = LyricsResponse.builder()
                .id(1)
                .musicId(1)
                .text("Test text")
                .musicTitle("Song")
                .build();

        when(lyricsService.getLyricsById(1))
                .thenReturn(ResponseApi.<LyricsResponse>builder().success(true).data(responseDto).build());

        mockMvc.perform(get("/api/v1/lyrics/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.musicTitle").value("Song"));
    }

    @Test
    void getById_NotFound() throws Exception {
        when(lyricsService.getLyricsById(999))
                .thenThrow(new DataNotFoundException("Not found"));

        mockMvc.perform(get("/api/v1/lyrics/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByMusicId_Success() throws Exception {
        LyricsResponse responseDto = LyricsResponse.builder()
                .id(1)
                .musicId(10)
                .text("Test text")
                .build();

        when(lyricsService.getLyricsByMusicId(10))
                .thenReturn(ResponseApi.<LyricsResponse>builder().success(true).data(responseDto).build());

        mockMvc.perform(get("/api/v1/lyrics/music/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.musicId").value(10));
    }

    @Test
    void getByMusicId_NotFound() throws Exception {
        when(lyricsService.getLyricsByMusicId(999))
                .thenThrow(new DataNotFoundException("Not found"));

        mockMvc.perform(get("/api/v1/lyrics/music/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_Success() throws Exception {
        when(lyricsService.deleteLyrics(1))
                .thenReturn(ResponseApi.<Void>builder().success(true).message("Deleted").build());

        mockMvc.perform(delete("/api/v1/lyrics/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void delete_NotFound() throws Exception {
        when(lyricsService.deleteLyrics(999))
                .thenThrow(new DataNotFoundException("Not found"));

        mockMvc.perform(delete("/api/v1/lyrics/999"))
                .andExpect(status().isNotFound());
    }
}

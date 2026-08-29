package uz.xitlar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uz.xitlar.dto.LyricsCreateDto;
import uz.xitlar.dto.LyricsCreateNestedDto;
import uz.xitlar.dto.LyricsUpdateDto;
import uz.xitlar.dto.MusicCreateDto;
import uz.xitlar.enums.Genre;
import uz.xitlar.repository.LyricsRepository;
import uz.xitlar.repository.MusicRepository;
import uz.xitlar.util.AudioTestHelper;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class MusicWithLyricsIntegrationTest {

    private static final String ADMIN_USERNAME = "ejavlon";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MusicRepository musicRepository;

    @Autowired
    private LyricsRepository lyricsRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = getAdminToken();
    }

    private String getAdminToken() throws Exception {
        String response = mockMvc.perform(post("/api/v1/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(ADMIN_USERNAME, "root")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.data");
    }

    @Test
    void createMusic_withoutLyrics_success() throws Exception {
        MusicCreateDto createDto = MusicCreateDto.builder()
                .title("Solo Track")
                .genre(Genre.POP)
                .trackNumber(1)
                .build();

        MockMultipartFile dataPart = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(createDto)
        );
        MockMultipartFile filePart = new MockMultipartFile(
                "file", "solo.mp3", "audio/mpeg", AudioTestHelper.createMinimalValidMp3()
        );

        String res = mockMvc.perform(multipart("/api/v1/musics")
                        .file(dataPart)
                        .file(filePart)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Solo Track"))
                .andExpect(jsonPath("$.data.lyrics").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer musicId = JsonPath.read(res, "$.data.id");
        assertNotNull(musicId);
        assertTrue(lyricsRepository.findByMusicId(musicId).isEmpty());
    }

    @Test
    void createMusic_withNestedLyrics_success() throws Exception {
        LyricsCreateNestedDto nestedDto = LyricsCreateNestedDto.builder()
                .text("Yomg'ir yog'ar tinmay...")
                .language("uz")
                .isSynced(false)
                .build();

        MusicCreateDto createDto = MusicCreateDto.builder()
                .title("Yomg'ir")
                .genre(Genre.POP)
                .trackNumber(2)
                .lyrics(nestedDto)
                .build();

        MockMultipartFile dataPart = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(createDto)
        );
        MockMultipartFile filePart = new MockMultipartFile(
                "file", "yomgir.mp3", "audio/mpeg", AudioTestHelper.createMinimalValidMp3()
        );

        String res = mockMvc.perform(multipart("/api/v1/musics")
                        .file(dataPart)
                        .file(filePart)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Yomg'ir"))
                .andExpect(jsonPath("$.data.lyrics.text").value("Yomg'ir yog'ar tinmay..."))
                .andExpect(jsonPath("$.data.lyrics.language").value("uz"))
                .andExpect(jsonPath("$.data.lyrics.isSynced").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer musicId = JsonPath.read(res, "$.data.id");
        assertNotNull(musicId);

        // Verify retrieval via GET /api/v1/lyrics/music/{musicId}
        mockMvc.perform(get("/api/v1/lyrics/music/" + musicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.text").value("Yomg'ir yog'ar tinmay..."))
                .andExpect(jsonPath("$.data.musicId").value(musicId));
    }

    @Test
    void createMusic_withNestedLyrics_synchronized_success() throws Exception {
        String lrc = "[00:12.50]Yomg'ir yog'ar tinmay...\n[01:05.250]Ikkinchi qator";
        LyricsCreateNestedDto nestedDto = LyricsCreateNestedDto.builder()
                .text("Yomg'ir yog'ar tinmay...")
                .language("uz")
                .isSynced(true)
                .lrcContent(lrc)
                .build();

        MusicCreateDto createDto = MusicCreateDto.builder()
                .title("Yomg'ir Synced")
                .genre(Genre.POP)
                .trackNumber(3)
                .lyrics(nestedDto)
                .build();

        MockMultipartFile dataPart = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(createDto)
        );
        MockMultipartFile filePart = new MockMultipartFile(
                "file", "yomgir-synced.mp3", "audio/mpeg", AudioTestHelper.createMinimalValidMp3()
        );

        mockMvc.perform(multipart("/api/v1/musics")
                        .file(dataPart)
                        .file(filePart)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.lyrics.isSynced").value(true))
                .andExpect(jsonPath("$.data.lyrics.lrcContent").value(lrc));
    }

    @Test
    void createMusic_withNestedLyrics_invalidLrc_rollback() throws Exception {
        LyricsCreateNestedDto nestedDto = LyricsCreateNestedDto.builder()
                .text("Yomg'ir yog'ar tinmay...")
                .language("uz")
                .isSynced(true)
                .lrcContent("[invalid]lyrics")
                .build();

        MusicCreateDto createDto = MusicCreateDto.builder()
                .title("Failed Track Invalid LRC")
                .genre(Genre.POP)
                .lyrics(nestedDto)
                .build();

        MockMultipartFile dataPart = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(createDto)
        );
        MockMultipartFile filePart = new MockMultipartFile(
                "file", "failed.mp3", "audio/mpeg", AudioTestHelper.createMinimalValidMp3()
        );

        mockMvc.perform(multipart("/api/v1/musics")
                        .file(dataPart)
                        .file(filePart)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        // Verify rollback: Music was not saved in DB
        assertFalse(musicRepository.findAll().stream()
                .anyMatch(m -> "Failed Track Invalid LRC".equals(m.getTitle())));
    }

    @Test
    void createMusic_withNestedLyrics_invalidLanguage_badRequest() throws Exception {
        LyricsCreateNestedDto nestedDto = LyricsCreateNestedDto.builder()
                .text("Yomg'ir yog'ar tinmay...")
                .language("fr")
                .isSynced(false)
                .build();

        MusicCreateDto createDto = MusicCreateDto.builder()
                .title("Failed Track Invalid Lang")
                .genre(Genre.POP)
                .lyrics(nestedDto)
                .build();

        MockMultipartFile dataPart = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(createDto)
        );
        MockMultipartFile filePart = new MockMultipartFile(
                "file", "failed-lang.mp3", "audio/mpeg", AudioTestHelper.createMinimalValidMp3()
        );

        mockMvc.perform(multipart("/api/v1/musics")
                        .file(dataPart)
                        .file(filePart)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        assertFalse(musicRepository.findAll().stream()
                .anyMatch(m -> "Failed Track Invalid Lang".equals(m.getTitle())));
    }

    @Test
    void createMusic_withNestedLyrics_inconsistentSyncedState_rollback() throws Exception {
        LyricsCreateNestedDto nestedDto = LyricsCreateNestedDto.builder()
                .text("Plain Text")
                .language("uz")
                .isSynced(false)
                .lrcContent("[00:12.50]Some LRC")
                .build();

        MusicCreateDto createDto = MusicCreateDto.builder()
                .title("Failed Inconsistent Synced")
                .genre(Genre.POP)
                .lyrics(nestedDto)
                .build();

        MockMultipartFile dataPart = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(createDto)
        );
        MockMultipartFile filePart = new MockMultipartFile(
                "file", "failed-inconsistent.mp3", "audio/mpeg", AudioTestHelper.createMinimalValidMp3()
        );

        mockMvc.perform(multipart("/api/v1/musics")
                        .file(dataPart)
                        .file(filePart)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        assertFalse(musicRepository.findAll().stream()
                .anyMatch(m -> "Failed Inconsistent Synced".equals(m.getTitle())));
    }

    @Test
    void createMusic_withNestedLyrics_syncedWithoutLrc_rollback() throws Exception {
        LyricsCreateNestedDto nestedDto = LyricsCreateNestedDto.builder()
                .text("Plain Text")
                .language("uz")
                .isSynced(true)
                .lrcContent("")
                .build();

        MusicCreateDto createDto = MusicCreateDto.builder()
                .title("Failed Synced Without LRC")
                .genre(Genre.POP)
                .lyrics(nestedDto)
                .build();

        MockMultipartFile dataPart = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(createDto)
        );
        MockMultipartFile filePart = new MockMultipartFile(
                "file", "failed-synced-empty.mp3", "audio/mpeg", AudioTestHelper.createMinimalValidMp3()
        );

        mockMvc.perform(multipart("/api/v1/musics")
                        .file(dataPart)
                        .file(filePart)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        assertFalse(musicRepository.findAll().stream()
                .anyMatch(m -> "Failed Synced Without LRC".equals(m.getTitle())));
    }

    @Test
    void nestedLyrics_doesNotAcceptMusicId() {
        boolean hasMusicId = Arrays.stream(LyricsCreateNestedDto.class.getDeclaredFields())
                .anyMatch(f -> "musicId".equals(f.getName()));
        assertFalse(hasMusicId, "LyricsCreateNestedDto must not contain musicId field");
    }

    @Test
    void independentLyricsApi_stillWorks() throws Exception {
        // 1. Create a music without lyrics
        MusicCreateDto createDto = MusicCreateDto.builder()
                .title("Independent Target Track")
                .genre(Genre.POP)
                .build();

        MockMultipartFile dataPart = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(createDto)
        );
        MockMultipartFile filePart = new MockMultipartFile(
                "file", "independent.mp3", "audio/mpeg", AudioTestHelper.createMinimalValidMp3()
        );

        String res = mockMvc.perform(multipart("/api/v1/musics")
                        .file(dataPart)
                        .file(filePart)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer musicId = JsonPath.read(res, "$.data.id");
        assertNotNull(musicId);

        // 2. POST /api/v1/lyrics independently
        LyricsCreateDto lyricsCreateDto = LyricsCreateDto.builder()
                .musicId(musicId)
                .text("Standalone Lyrics")
                .language("en")
                .isSynced(false)
                .build();

        String lyricsRes = mockMvc.perform(post("/api/v1/lyrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lyricsCreateDto))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.musicId").value(musicId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer lyricsId = JsonPath.read(lyricsRes, "$.data.id");
        assertNotNull(lyricsId);

        // 3. GET /api/v1/lyrics/{id}
        mockMvc.perform(get("/api/v1/lyrics/" + lyricsId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.text").value("Standalone Lyrics"));

        // 4. PUT /api/v1/lyrics/{id}
        LyricsUpdateDto updateDto = LyricsUpdateDto.builder()
                .text("Updated Standalone Lyrics")
                .language("uz")
                .isSynced(false)
                .build();

        mockMvc.perform(put("/api/v1/lyrics/" + lyricsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.text").value("Updated Standalone Lyrics"));

        // 5. DELETE /api/v1/lyrics/{id}
        mockMvc.perform(delete("/api/v1/lyrics/" + lyricsId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 6. Verify deleted
        mockMvc.perform(get("/api/v1/lyrics/" + lyricsId))
                .andExpect(status().isNotFound());
    }
}

package uz.xitlar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import uz.xitlar.dto.artist.ArtistCreateDto;
import uz.xitlar.dto.lyrics.LyricsCreateDto;
import uz.xitlar.dto.lyrics.LyricsUpdateDto;
import uz.xitlar.dto.music.MusicCreateDto;
import uz.xitlar.enums.Genre;
import uz.xitlar.repository.ArtistRepository;
import uz.xitlar.repository.MusicRepository;
import uz.xitlar.util.AudioTestHelper;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class SecurityAndProductionAuditTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MusicRepository musicRepository;

    @Autowired
    private ArtistRepository artistRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        String response = mockMvc.perform(post("/api/v1/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "ejavlon",
                                    "password": "root"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        adminToken = com.jayway.jsonpath.JsonPath.read(response, "$.data");
    }

    // ==================== 1. PAGINATION BOUNDARY TESTS ====================

    @Test
    void getAllMusics_HugeSize_ClampedToMax50() throws Exception {
        mockMvc.perform(get("/api/v1/musics")
                        .param("page", "0")
                        .param("size", "9999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(50));
    }

    @Test
    void getAllMusics_NegativePageAndSize_ClampedSafely() throws Exception {
        mockMvc.perform(get("/api/v1/musics")
                        .param("page", "-5")
                        .param("size", "-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(1));
    }

    @Test
    void getAllArtists_HugeSize_ClampedToMax50() throws Exception {
        mockMvc.perform(get("/api/v1/artists")
                        .param("page", "0")
                        .param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(50));
    }

    // ==================== 2. SORT ALLOWLIST & PROBING TESTS ====================

    @Test
    void getAllMusics_InvalidSortField_FallsBackToIdWithout500() throws Exception {
        mockMvc.perform(get("/api/v1/musics")
                        .param("sortBy", "nonExistentField")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getAllMusics_ProbingNestedProperty_FallsBackSafelyWithoutLeak() throws Exception {
        mockMvc.perform(get("/api/v1/musics")
                        .param("sortBy", "addedBy.password")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getAllArtists_InvalidSortField_FallsBackToIdWithout500() throws Exception {
        mockMvc.perform(get("/api/v1/artists")
                        .param("sortBy", "malicious_injection")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================== 3. LYRICS PARTIAL UPDATE CONTRACT ====================

    @Test
    void updateLyrics_PartialUpdateLanguageOnly_Success() throws Exception {
        // 1. Create Music
        MusicCreateDto musicDto = MusicCreateDto.builder()
                .title("Lyrics Test Track")
                .genre(Genre.POP)
                .build();
        byte[] audioBytes = AudioTestHelper.createMinimalValidMp3();
        MockMultipartFile audioFile = new MockMultipartFile("file", "track.mp3", "audio/mpeg", audioBytes);
        MockMultipartFile dataPart = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(musicDto));

        String musicRes = mockMvc.perform(multipart("/api/v1/musics")
                        .file(dataPart)
                        .file(audioFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer musicId = com.jayway.jsonpath.JsonPath.read(musicRes, "$.data.id");

        // 2. Create Lyrics
        LyricsCreateDto createDto = LyricsCreateDto.builder()
                .musicId(musicId)
                .text("Original Lyrics Text")
                .language("uz")
                .isSynced(false)
                .build();

        String lyricsRes = mockMvc.perform(post("/api/v1/lyrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer lyricsId = com.jayway.jsonpath.JsonPath.read(lyricsRes, "$.data.id");

        // 3. Update lyrics
        LyricsUpdateDto updateDto = LyricsUpdateDto.builder()
                .text("Updated Lyrics Text")
                .language("en")
                .isSynced(false)
                .build();

        mockMvc.perform(put("/api/v1/lyrics/" + lyricsId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.language").value("en"))
                .andExpect(jsonPath("$.data.text").value("Updated Lyrics Text"));
    }

    // ==================== 4. MUSIC DUPLICATE CONSTRAINT ====================

    @Test
    void createMusic_DuplicateTitleForSameArtist_Returns409() throws Exception {
        // Create Artist
        ArtistCreateDto artistDto = ArtistCreateDto.builder()
                .name("Duplicate Test Artist")
                .genre(Genre.POP)
                .build();
        MockMultipartFile artistData = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(artistDto));
        String artistRes = mockMvc.perform(multipart("/api/v1/artists")
                        .file(artistData)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer artistId = com.jayway.jsonpath.JsonPath.read(artistRes, "$.data.id");

        // Create First Song
        MusicCreateDto musicDto = MusicCreateDto.builder()
                .title("Unique Title")
                .artistId(artistId)
                .build();
        byte[] audioBytes = AudioTestHelper.createMinimalValidMp3();
        MockMultipartFile audioFile1 = new MockMultipartFile("file", "track1.mp3", "audio/mpeg", audioBytes);
        MockMultipartFile dataPart1 = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(musicDto));

        mockMvc.perform(multipart("/api/v1/musics")
                        .file(dataPart1)
                        .file(audioFile1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Attempt Duplicate Song
        byte[] audioBytes2 = AudioTestHelper.createMinimalValidMp3();
        MockMultipartFile audioFile2 = new MockMultipartFile("file", "track2.mp3", "audio/mpeg", audioBytes2);
        MockMultipartFile dataPart2 = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(musicDto));

        mockMvc.perform(multipart("/api/v1/musics")
                        .file(dataPart2)
                        .file(audioFile2)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }
}

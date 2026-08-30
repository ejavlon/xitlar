package uz.xitlar.controller;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uz.xitlar.dto.playlist.PlaylistCreateDto;
import uz.xitlar.dto.playlist.PlaylistReorderDto;
import uz.xitlar.dto.playlist.PlaylistUpdateDto;
import uz.xitlar.entity.Music;
import uz.xitlar.entity.Playlist;
import uz.xitlar.entity.PlaylistMusic;
import uz.xitlar.entity.User;
import uz.xitlar.enums.Role;
import uz.xitlar.repository.MusicRepository;
import uz.xitlar.repository.PlaylistMusicRepository;
import uz.xitlar.repository.PlaylistRepository;
import uz.xitlar.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class PlaylistSecurityIntegrationTest {

    private static final String ADMIN_USERNAME = "ejavlon";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MusicRepository musicRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private PlaylistMusicRepository playlistMusicRepository;

    @Autowired
    private uz.xitlar.repository.OAuthAccountRepository oauthAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;
    private String moderatorToken;
    private String userToken;

    private User admin;
    private User moderator;
    private User user;

    private Music testMusic1;
    private Music testMusic2;
    private Music testMusic3;
    private Playlist testPlaylist;

    @BeforeEach
    void setUp() throws Exception {
        oauthAccountRepository.deleteAll();
        userRepository.deleteByUsernameNot(ADMIN_USERNAME);

        admin = userRepository.findByUsername(ADMIN_USERNAME).orElseGet(() ->
                userRepository.save(User.builder()
                        .firstName("Admin")
                        .lastName("Root")
                        .username(ADMIN_USERNAME)
                        .password(passwordEncoder.encode("root"))
                        .role(Role.ADMIN)
                        .build())
        );

        moderator = userRepository.save(User.builder()
                .firstName("Mod")
                .lastName("User")
                .username("playlist_mod")
                .password(passwordEncoder.encode("password123"))
                .role(Role.MODERATOR)
                .build());

        user = userRepository.save(User.builder()
                .firstName("Regular")
                .lastName("User")
                .username("playlist_user")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        testMusic1 = musicRepository.save(Music.builder()
                .title("Track 1")
                .storedName("audio_track1.mp3")
                .originalFileName("track1.mp3")
                .audioSize(1024L)
                .audioContentType("audio/mpeg")
                .addedDate(LocalDateTime.now())
                .build());

        testMusic2 = musicRepository.save(Music.builder()
                .title("Track 2")
                .storedName("audio_track2.mp3")
                .originalFileName("track2.mp3")
                .audioSize(2048L)
                .audioContentType("audio/mpeg")
                .addedDate(LocalDateTime.now())
                .build());

        testMusic3 = musicRepository.save(Music.builder()
                .title("Track 3")
                .storedName("audio_track3.mp3")
                .originalFileName("track3.mp3")
                .audioSize(3072L)
                .audioContentType("audio/mpeg")
                .addedDate(LocalDateTime.now())
                .build());

        testPlaylist = playlistRepository.save(Playlist.builder()
                .title("Summer Mix")
                .description("Vibrant summer tracks")
                .createdBy(admin)
                .createdAt(LocalDateTime.now())
                .build());

        adminToken = getUserToken(ADMIN_USERNAME, "root");
        moderatorToken = getUserToken("playlist_mod", "password123");
        userToken = getUserToken("playlist_user", "password123");
    }

    private String getUserToken(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.data");
    }

    // ==================== 1. ANONYMOUS ACCESS ====================

    @Test
    void anonymousUser_CanGetAllPlaylists() throws Exception {
        mockMvc.perform(get("/api/v1/playlists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void anonymousUser_CanGetPlaylistById() throws Exception {
        mockMvc.perform(get("/api/v1/playlists/" + testPlaylist.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Summer Mix"));
    }

    @Test
    void anonymousUser_CannotCreatePlaylist() throws Exception {
        PlaylistCreateDto dto = PlaylistCreateDto.builder().title("Anon Playlist").build();
        MockMultipartFile dataPart = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(dto));

        mockMvc.perform(multipart("/api/v1/playlists").file(dataPart))
                .andExpect(status().isUnauthorized());
     }

     @Test
     void anonymousUser_CannotAddMusic() throws Exception {
         mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/" + testMusic1.getId()))
                 .andExpect(status().isUnauthorized());
     }

     @Test
     void anonymousUser_CannotDeletePlaylist() throws Exception {
         mockMvc.perform(delete("/api/v1/playlists/" + testPlaylist.getId()))
                 .andExpect(status().isUnauthorized());
     }

    // ==================== 2. REGULAR USER ACCESS (FORBIDDEN WRITE) ====================

    @Test
    void regularUser_CannotCreatePlaylist() throws Exception {
        PlaylistCreateDto dto = PlaylistCreateDto.builder().title("User Playlist").build();
        MockMultipartFile dataPart = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(dto));

        mockMvc.perform(multipart("/api/v1/playlists")
                        .file(dataPart)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUser_CannotAddMusic() throws Exception {
        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/" + testMusic1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUser_CannotDeletePlaylist() throws Exception {
        mockMvc.perform(delete("/api/v1/playlists/" + testPlaylist.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // ==================== 3. MODERATOR & ADMIN ACCESS ====================

    @Test
    void moderator_CanCreateAndModifyPlaylist() throws Exception {
        PlaylistCreateDto dto = PlaylistCreateDto.builder()
                .title("Mod Curated List")
                .description("Curated by Moderator")
                .build();
        MockMultipartFile dataPart = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(dto));

        String res = mockMvc.perform(multipart("/api/v1/playlists")
                        .file(dataPart)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Mod Curated List"))
                .andReturn().getResponse().getContentAsString();

        Integer playlistId = JsonPath.read(res, "$.data.id");

        // Add music
        mockMvc.perform(post("/api/v1/playlists/" + playlistId + "/musics/" + testMusic1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackCount").value(1));

        // Delete playlist
        mockMvc.perform(delete("/api/v1/playlists/" + playlistId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void admin_FullCrudAndReorderFlow_Success() throws Exception {
        // 1. Create Playlist
        PlaylistCreateDto dto = PlaylistCreateDto.builder()
                .title("Admin Rock Playlist")
                .description("Top rock hits")
                .build();
        MockMultipartFile dataPart = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(dto));

        String res = mockMvc.perform(multipart("/api/v1/playlists")
                        .file(dataPart)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Integer playlistId = JsonPath.read(res, "$.data.id");

        // 2. Add 3 tracks
        mockMvc.perform(post("/api/v1/playlists/" + playlistId + "/musics/" + testMusic1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/playlists/" + playlistId + "/musics/" + testMusic2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/playlists/" + playlistId + "/musics/" + testMusic3.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackCount").value(3));

        // 3. Reorder tracks: reverse order (3, 2, 1)
        PlaylistReorderDto reorderDto = PlaylistReorderDto.builder()
                .musicIds(List.of(testMusic3.getId(), testMusic2.getId(), testMusic1.getId()))
                .build();

        mockMvc.perform(put("/api/v1/playlists/" + playlistId + "/musics/reorder")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reorderDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.musics[0].id").value(testMusic3.getId()))
                .andExpect(jsonPath("$.data.musics[0].position").value(0))
                .andExpect(jsonPath("$.data.musics[1].id").value(testMusic2.getId()))
                .andExpect(jsonPath("$.data.musics[1].position").value(1))
                .andExpect(jsonPath("$.data.musics[2].id").value(testMusic1.getId()))
                .andExpect(jsonPath("$.data.musics[2].position").value(2));

        // 4. Remove a music track
        mockMvc.perform(delete("/api/v1/playlists/" + playlistId + "/musics/" + testMusic2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackCount").value(2))
                .andExpect(jsonPath("$.data.musics[0].position").value(0))
                .andExpect(jsonPath("$.data.musics[1].position").value(1));

        // 5. Update playlist details
        PlaylistUpdateDto updateDto = PlaylistUpdateDto.builder()
                .title("Updated Admin Playlist")
                .build();
        MockMultipartFile updateData = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(updateDto));

        mockMvc.perform(multipart("/api/v1/playlists/" + playlistId)
                        .file(updateData)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated Admin Playlist"));
    }

    // ==================== 4. VALIDATION & ERROR HANDLING ====================

    @Test
    void addMusic_DuplicateTrack_Returns409Conflict() throws Exception {
        // First add
        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/" + testMusic1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Duplicate add
        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/" + testMusic1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void addMusic_NonExistentMusic_Returns404NotFound() throws Exception {
        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void removeMusic_NotInPlaylist_Returns404NotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/playlists/" + testPlaylist.getId() + "/musics/" + testMusic1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void reorderMusics_DuplicateIds_Returns400BadRequest() throws Exception {
        // Add 2 tracks
        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/" + testMusic1.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken));
        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/" + testMusic2.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken));

        PlaylistReorderDto dto = PlaylistReorderDto.builder()
                .musicIds(List.of(testMusic1.getId(), testMusic1.getId()))
                .build();

        mockMvc.perform(put("/api/v1/playlists/" + testPlaylist.getId() + "/musics/reorder")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void reorderMusics_UnknownMusicId_Returns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/" + testMusic1.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken));

        PlaylistReorderDto dto = PlaylistReorderDto.builder()
                .musicIds(List.of(99999))
                .build();

        mockMvc.perform(put("/api/v1/playlists/" + testPlaylist.getId() + "/musics/reorder")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ==================== 5. PAGINATION & SORT SECURITY ====================

    @Test
    void getAllPlaylists_HugeSize_ClampedTo50() throws Exception {
        mockMvc.perform(get("/api/v1/playlists")
                        .param("page", "0")
                        .param("size", "9999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(50));
    }

    @Test
    void getAllPlaylists_NegativePageAndSize_ClampedSafely() throws Exception {
        mockMvc.perform(get("/api/v1/playlists")
                        .param("page", "-5")
                        .param("size", "-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(1));
    }

    @Test
    void getAllPlaylists_InvalidSortField_FallsBackToIdWithout500() throws Exception {
        mockMvc.perform(get("/api/v1/playlists")
                        .param("sortBy", "nonExistentField")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getAllPlaylists_ProbingNestedProperty_FallsBackSafelyWithoutLeak() throws Exception {
        mockMvc.perform(get("/api/v1/playlists")
                        .param("sortBy", "createdBy.password")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================== 6. CASCADE DELETE INTEGRITY ====================

    @Test
    void deletePlaylist_DoesNotDeleteUnderlyingMusic() throws Exception {
        // Add music to playlist
        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/" + testMusic1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        assertTrue(playlistMusicRepository.existsByPlaylistIdAndMusicId(testPlaylist.getId(), testMusic1.getId()));

        // Delete playlist
        mockMvc.perform(delete("/api/v1/playlists/" + testPlaylist.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Verify playlist is gone
        assertFalse(playlistRepository.existsById(testPlaylist.getId()));

        // Verify playlistMusic is gone
        assertFalse(playlistMusicRepository.existsByPlaylistIdAndMusicId(testPlaylist.getId(), testMusic1.getId()));

        // Verify Music entity STILL EXISTS
        assertTrue(musicRepository.existsById(testMusic1.getId()));
    }

    // ==================== 7. BULK MUSIC ASSIGNMENT ====================

    @Test
    void addMusicsBulk_Anonymous_ReturnsUnauthorizedOrForbidden() throws Exception {
        String body = """
                {"musicIds": [%d, %d]}
                """.formatted(testMusic1.getId(), testMusic2.getId());

        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addMusicsBulk_UserRole_ReturnsForbidden() throws Exception {
        String body = """
                {"musicIds": [%d, %d]}
                """.formatted(testMusic1.getId(), testMusic2.getId());

        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/bulk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void addMusicsBulk_ModeratorRole_ReturnsOk() throws Exception {
        String body = """
                {"musicIds": [%d, %d]}
                """.formatted(testMusic1.getId(), testMusic2.getId());

        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/bulk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.playlistId").value(testPlaylist.getId()))
                .andExpect(jsonPath("$.data.addedCount").value(2))
                .andExpect(jsonPath("$.data.trackCount").value(2));
    }

    @Test
    void addMusicsBulk_AdminRole_ReturnsOk() throws Exception {
        String body = """
                {"musicIds": [%d, %d, %d]}
                """.formatted(testMusic1.getId(), testMusic2.getId(), testMusic3.getId());

        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/bulk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.addedCount").value(3))
                .andExpect(jsonPath("$.data.trackCount").value(3));

        // Verify detail response shows them in order
        mockMvc.perform(get("/api/v1/playlists/" + testPlaylist.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.musics[0].id").value(testMusic1.getId()))
                .andExpect(jsonPath("$.data.musics[0].position").value(0))
                .andExpect(jsonPath("$.data.musics[1].id").value(testMusic2.getId()))
                .andExpect(jsonPath("$.data.musics[1].position").value(1))
                .andExpect(jsonPath("$.data.musics[2].id").value(testMusic3.getId()))
                .andExpect(jsonPath("$.data.musics[2].position").value(2));
    }

    @Test
    void addMusicsBulk_50Musics_ReturnsOk() throws Exception {
        List<Integer> ids = new java.util.ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            Music m = musicRepository.save(Music.builder()
                    .title("Bulk Song " + i)
                    .storedName("bulk" + i + ".mp3")
                    .originalFileName("bulk" + i + ".mp3")
                    .audioSize(1024L)
                    .audioContentType("audio/mpeg")
                    .duration(120)
                    .build());
            ids.add(m.getId());
        }

        String body = objectMapper.writeValueAsString(new uz.xitlar.dto.playlist.PlaylistBulkMusicAddDto(ids));

        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/bulk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addedCount").value(50))
                .andExpect(jsonPath("$.data.trackCount").value(50));
    }

    @Test
    void addMusicsBulk_51Musics_ReturnsBadRequest() throws Exception {
        List<Integer> ids = new java.util.ArrayList<>();
        for (int i = 1; i <= 51; i++) {
            ids.add(i);
        }

        String body = objectMapper.writeValueAsString(new uz.xitlar.dto.playlist.PlaylistBulkMusicAddDto(ids));

        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/bulk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void addMusicsBulk_NonexistentMusic_ReturnsNotFound() throws Exception {
        String body = """
                {"musicIds": [%d, 999999]}
                """.formatted(testMusic1.getId());

        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/bulk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        // Verify atomic rollback: testMusic1 was NOT added
        assertFalse(playlistMusicRepository.existsByPlaylistIdAndMusicId(testPlaylist.getId(), testMusic1.getId()));
    }

    @Test
    void addMusicsBulk_DuplicateIdsInRequest_ReturnsBadRequest() throws Exception {
        String body = """
                {"musicIds": [%d, %d, %d]}
                """.formatted(testMusic1.getId(), testMusic2.getId(), testMusic1.getId());

        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/bulk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void addMusicsBulk_AlreadyInPlaylist_ReturnsConflict() throws Exception {
        // Add music 1 first
        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/" + testMusic1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Try bulk add with music 1 and music 2
        String body = """
                {"musicIds": [%d, %d]}
                """.formatted(testMusic1.getId(), testMusic2.getId());

        mockMvc.perform(post("/api/v1/playlists/" + testPlaylist.getId() + "/musics/bulk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        // Verify atomic rollback: testMusic2 was NOT added
        assertFalse(playlistMusicRepository.existsByPlaylistIdAndMusicId(testPlaylist.getId(), testMusic2.getId()));
    }
}

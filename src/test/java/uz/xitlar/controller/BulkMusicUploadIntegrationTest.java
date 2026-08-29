package uz.xitlar.controller;

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
import uz.xitlar.entity.Artist;
import uz.xitlar.entity.User;
import uz.xitlar.enums.Role;
import uz.xitlar.repository.ArtistRepository;
import uz.xitlar.repository.MusicRepository;
import uz.xitlar.repository.UserRepository;
import uz.xitlar.util.AudioTestHelper;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class BulkMusicUploadIntegrationTest {

    private static final String ADMIN_USERNAME = "ejavlon";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private MusicRepository musicRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private String adminToken;
    private String userToken;
    private String moderatorToken;

    @BeforeEach
    void setUp() throws Exception {
        transactionTemplate.executeWithoutResult(status -> {
            musicRepository.deleteAll();
            artistRepository.deleteAll();
            musicRepository.flush();
            artistRepository.flush();
            userRepository.findByUsername("test_bulk_user").ifPresent(userRepository::delete);
            userRepository.findByUsername("test_bulk_mod").ifPresent(userRepository::delete);
            userRepository.flush();

            userRepository.save(User.builder()
                    .firstName("Regular")
                    .lastName("User")
                    .username("test_bulk_user")
                    .password(passwordEncoder.encode("password123"))
                    .role(Role.USER)
                    .build());

            userRepository.save(User.builder()
                    .firstName("Mod")
                    .lastName("User")
                    .username("test_bulk_mod")
                    .password(passwordEncoder.encode("password123"))
                    .role(Role.MODERATOR)
                    .build());
            userRepository.flush();
        });

        adminToken = getUserToken(ADMIN_USERNAME, "root");
        userToken = getUserToken("test_bulk_user", "password123");
        moderatorToken = getUserToken("test_bulk_mod", "password123");
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            musicRepository.deleteAll();
            artistRepository.deleteAll();
            userRepository.findByUsername("test_bulk_user").ifPresent(userRepository::delete);
            userRepository.findByUsername("test_bulk_mod").ifPresent(userRepository::delete);
            userRepository.flush();
        });
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

    @Test
    void anonymousUser_CannotBulkUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "test.mp3", "audio/mpeg", AudioTestHelper.createMinimalValidMp3());

        mockMvc.perform(multipart("/api/v1/musics/bulk")
                        .file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUser_CannotBulkUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "test.mp3", "audio/mpeg", AudioTestHelper.createMinimalValidMp3());

        mockMvc.perform(multipart("/api/v1/musics/bulk")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderatorUser_CanBulkUpload_Success() throws Exception {
        byte[] mp3Bytes = AudioTestHelper.createMinimalValidMp3WithPayload("integration-mod-payload".getBytes());
        MockMultipartFile file1 = new MockMultipartFile("files", "mod_song1.mp3", "audio/mpeg", mp3Bytes);

        mockMvc.perform(multipart("/api/v1/musics/bulk")
                        .file(file1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.successCount").value(1));
    }

    @Test
    void adminUser_CanBulkUpload_Success() throws Exception {
        byte[] mp3Bytes1 = AudioTestHelper.createMinimalValidMp3WithPayload("integration-admin-payload-1".getBytes());
        byte[] mp3Bytes2 = AudioTestHelper.createMinimalValidMp3WithPayload("integration-admin-payload-2".getBytes());
        MockMultipartFile file1 = new MockMultipartFile("files", "admin_song1.mp3", "audio/mpeg", mp3Bytes1);
        MockMultipartFile file2 = new MockMultipartFile("files", "admin_song2.mp3", "audio/mpeg", mp3Bytes2);

        mockMvc.perform(multipart("/api/v1/musics/bulk")
                        .file(file1)
                        .file(file2)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.duplicateCount").value(0));
    }

    @Test
    void bulkUpload_DuplicateDetection_SecondUploadReturnsDuplicate() throws Exception {
        byte[] identicalMp3 = AudioTestHelper.createMinimalValidMp3WithPayload("duplicate-integration-marker".getBytes());
        MockMultipartFile file = new MockMultipartFile("files", "original.mp3", "audio/mpeg", identicalMp3);

        // First upload -> SUCCESS
        mockMvc.perform(multipart("/api/v1/musics/bulk")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1));

        // Second upload with same content -> DUPLICATE
        MockMultipartFile duplicateFile = new MockMultipartFile("files", "renamed_copy.mp3", "audio/mpeg", identicalMp3);
        mockMvc.perform(multipart("/api/v1/musics/bulk")
                        .file(duplicateFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.duplicateCount").value(1))
                .andExpect(jsonPath("$.data.results[0].status").value("DUPLICATE"));
    }

    @Test
    void bulkUpload_WithMetadataAndArtistRelation_RetrievableViaGet() throws Exception {
        Artist artist = transactionTemplate.execute(status -> artistRepository.save(Artist.builder()
                .name("Integration Artist")
                .genre(uz.xitlar.enums.Genre.POP)
                .build()));

        byte[] mp3Bytes = AudioTestHelper.createMinimalValidMp3WithPayload("song-with-artist".getBytes());
        MockMultipartFile file = new MockMultipartFile("files", "art_song.mp3", "audio/mpeg", mp3Bytes);

        String metadataJson = """
                [
                  {
                    "fileName": "art_song.mp3",
                    "title": "Configured Title",
                    "artistId": %d,
                    "genre": "POP",
                    "trackNumber": 1
                  }
                ]
                """.formatted(artist.getId());

        MockMultipartFile metaPart = new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE, metadataJson.getBytes());

        String uploadResponse = mockMvc.perform(multipart("/api/v1/musics/bulk")
                        .file(file)
                        .file(metaPart)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer createdMusicId = JsonPath.read(uploadResponse, "$.data.results[0].musicId");

        // Verify newly uploaded music is accessible via GET /api/v1/musics/{id}
        mockMvc.perform(get("/api/v1/musics/" + createdMusicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Configured Title"))
                .andExpect(jsonPath("$.data.artist.id").value(artist.getId()))
                .andExpect(jsonPath("$.data.artist.name").value("Integration Artist"))
                .andExpect(jsonPath("$.data.genre").value("POP"));
    }

    @Test
    void uploadBulk_51Files_ReturnsBadRequest() throws Exception {
        var requestBuilder = multipart("/api/v1/musics/bulk")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);

        for (int i = 1; i <= 51; i++) {
            requestBuilder.file(new MockMultipartFile("files", "file" + i + ".mp3", "audio/mpeg", AudioTestHelper.createMinimalValidMp3()));
        }

        mockMvc.perform(requestBuilder)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("50")));
    }
}

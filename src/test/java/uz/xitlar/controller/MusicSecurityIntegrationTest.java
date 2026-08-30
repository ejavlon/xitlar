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
import uz.xitlar.entity.User;
import uz.xitlar.enums.Role;
import uz.xitlar.repository.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class MusicSecurityIntegrationTest {

    private static final String ADMIN_USERNAME = "ejavlon";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String userToken;
    private String moderatorToken;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteByUsernameNot(ADMIN_USERNAME);

        userRepository.save(User.builder()
                .firstName("Regular")
                .lastName("User")
                .username("test_regular_user")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        userRepository.save(User.builder()
                .firstName("Mod")
                .lastName("User")
                .username("test_mod_user")
                .password(passwordEncoder.encode("password123"))
                .role(Role.MODERATOR)
                .build());

        adminToken = getUserToken(ADMIN_USERNAME, "root");
        userToken = getUserToken("test_regular_user", "password123");
        moderatorToken = getUserToken("test_mod_user", "password123");
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
    void anonymousUser_CanGetMusics() throws Exception {
        mockMvc.perform(get("/api/v1/musics"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousUser_CannotPostMusic() throws Exception {
        MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes());
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", new byte[10]);

        mockMvc.perform(multipart("/api/v1/musics")
                        .file(data)
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUser_CannotPostMusic() throws Exception {
        MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes());
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", new byte[10]);

        mockMvc.perform(multipart("/api/v1/musics")
                        .file(data)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUser_CannotDeleteMusic() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/musics/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUser_CannotDeleteMusic() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/musics/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderatorUser_CanAccessPostMusicEndpoint() throws Exception {
        MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, "{\"title\": \"\"}".getBytes());
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", new byte[10]);

        // Passing blank title reaches validation (400), confirming authorization succeeded for MODERATOR
        mockMvc.perform(multipart("/api/v1/musics")
                        .file(data)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminUser_CanAccessPostMusicEndpoint() throws Exception {
        MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, "{\"title\": \"\"}".getBytes());
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", new byte[10]);

        // Passing invalid title causes validation failure (400) proving authorization succeeded for ADMIN
        mockMvc.perform(multipart("/api/v1/musics")
                        .file(data)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousUser_CannotPutMusic() throws Exception {
        MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes());

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/musics/1")
                        .file(data))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUser_CannotPutMusic() throws Exception {
        MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes());

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/musics/1")
                        .file(data)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUser_CanAccessDeleteMusicEndpoint() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/musics/9999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void moderatorUser_CanAccessDeleteMusicEndpoint() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/musics/9999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isNotFound());
    }
}

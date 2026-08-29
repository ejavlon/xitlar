package uz.xitlar.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uz.xitlar.entity.User;
import uz.xitlar.enums.Role;
import uz.xitlar.repository.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class LyricsSecurityIntegrationTest {

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
                .username("test_regular_lyrics_user")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        userRepository.save(User.builder()
                .firstName("Mod")
                .lastName("User")
                .username("test_mod_lyrics_user")
                .password(passwordEncoder.encode("password123"))
                .role(Role.MODERATOR)
                .build());

        adminToken = getUserToken(ADMIN_USERNAME, "root");
        userToken = getUserToken("test_regular_lyrics_user", "password123");
        moderatorToken = getUserToken("test_mod_lyrics_user", "password123");
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
    void anonymousUser_CanGetLyricsById() throws Exception {
        mockMvc.perform(get("/api/v1/lyrics/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousUser_CanGetLyricsByMusicId() throws Exception {
        mockMvc.perform(get("/api/v1/lyrics/music/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousUser_CannotPostLyrics() throws Exception {
        mockMvc.perform(post("/api/v1/lyrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUser_CannotPutLyrics() throws Exception {
        mockMvc.perform(put("/api/v1/lyrics/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUser_CannotDeleteLyrics() throws Exception {
        mockMvc.perform(delete("/api/v1/lyrics/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUser_CannotPostLyrics() throws Exception {
        mockMvc.perform(post("/api/v1/lyrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUser_CannotPutLyrics() throws Exception {
        mockMvc.perform(put("/api/v1/lyrics/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUser_CannotDeleteLyrics() throws Exception {
        mockMvc.perform(delete("/api/v1/lyrics/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderatorUser_CanAccessPostLyricsEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/lyrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminUser_CanAccessPostLyricsEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/lyrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moderatorUser_CanAccessPutLyricsEndpoint() throws Exception {
        mockMvc.perform(put("/api/v1/lyrics/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminUser_CanAccessDeleteLyricsEndpoint() throws Exception {
        mockMvc.perform(delete("/api/v1/lyrics/9999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}

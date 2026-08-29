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
import uz.xitlar.entity.Comment;
import uz.xitlar.entity.Music;
import uz.xitlar.entity.User;
import uz.xitlar.enums.Role;
import uz.xitlar.repository.CommentRepository;
import uz.xitlar.repository.MusicRepository;
import uz.xitlar.repository.UserRepository;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class CommentSecurityIntegrationTest {

    private static final String ADMIN_USERNAME = "ejavlon";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MusicRepository musicRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private uz.xitlar.repository.OAuthAccountRepository oauthAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String user1Token;
    private String user2Token;
    private String moderatorToken;

    private User user1;
    private User user2;
    private User moderator;
    private User admin;
    private Music testMusic;

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

        user1 = userRepository.save(User.builder()
                .firstName("User")
                .lastName("One")
                .username("comment_user_1")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        user2 = userRepository.save(User.builder()
                .firstName("User")
                .lastName("Two")
                .username("comment_user_2")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        moderator = userRepository.save(User.builder()
                .firstName("Mod")
                .lastName("User")
                .username("comment_mod_user")
                .password(passwordEncoder.encode("password123"))
                .role(Role.MODERATOR)
                .build());

        testMusic = musicRepository.save(Music.builder()
                .title("Test Track")
                .storedName("test_audio_123.mp3")
                .originalFileName("test.mp3")
                .audioSize(2048L)
                .audioContentType("audio/mpeg")
                .addedDate(LocalDateTime.now())
                .build());

        adminToken = getUserToken(ADMIN_USERNAME, "root");
        user1Token = getUserToken("comment_user_1", "password123");
        user2Token = getUserToken("comment_user_2", "password123");
        moderatorToken = getUserToken("comment_mod_user", "password123");
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

    private Comment createCommentForUser(User owner, String text) {
        return commentRepository.save(Comment.builder()
                .text(text)
                .music(testMusic)
                .user(owner)
                .createdAt(LocalDateTime.now())
                .build());
    }

    // ================= ANONYMOUS =================

    @Test
    void anonymousUser_CanGetComments() throws Exception {
        createCommentForUser(user1, "Public comment");

        mockMvc.perform(get("/api/v1/comments/music/" + testMusic.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].text").value("Public comment"));
    }

    @Test
    void anonymousUser_CannotPostComment() throws Exception {
        mockMvc.perform(post("/api/v1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Anon text", "musicId": %d}
                                """.formatted(testMusic.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUser_CannotPutComment() throws Exception {
        Comment comment = createCommentForUser(user1, "Initial text");

        mockMvc.perform(put("/api/v1/comments/" + comment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Hacked text"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUser_CannotDeleteComment() throws Exception {
        Comment comment = createCommentForUser(user1, "Initial text");

        mockMvc.perform(delete("/api/v1/comments/" + comment.getId()))
                .andExpect(status().isForbidden());
    }

    // ================= USER =================

    @Test
    void regularUser_PostOwnComment_Success() throws Exception {
        mockMvc.perform(post("/api/v1/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "User1 comment", "musicId": %d}
                                """.formatted(testMusic.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.text").value("User1 comment"))
                .andExpect(jsonPath("$.data.userName").value(user1.getUsername()));
    }

    @Test
    void regularUser_PutOwnComment_Success() throws Exception {
        Comment comment = createCommentForUser(user1, "User1 initial");

        mockMvc.perform(put("/api/v1/comments/" + comment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "User1 updated text"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.text").value("User1 updated text"));
    }

    @Test
    void regularUser_DeleteOwnComment_Success() throws Exception {
        Comment comment = createCommentForUser(user1, "User1 delete me");

        mockMvc.perform(delete("/api/v1/comments/" + comment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertFalse(commentRepository.existsById(comment.getId()));
    }

    @Test
    void regularUser_PutOtherUsersComment_Returns403() throws Exception {
        Comment comment = createCommentForUser(user1, "User1 comment");

        mockMvc.perform(put("/api/v1/comments/" + comment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "User2 malicious edit"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void regularUser_DeleteOtherUsersComment_Returns403() throws Exception {
        Comment comment = createCommentForUser(user1, "User1 comment");

        mockMvc.perform(delete("/api/v1/comments/" + comment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ================= MODERATOR =================

    @Test
    void moderatorUser_PutOtherUsersComment_Success() throws Exception {
        Comment comment = createCommentForUser(user1, "User1 inappropriate text");

        mockMvc.perform(put("/api/v1/comments/" + comment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Comment moderated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.text").value("Comment moderated"));
    }

    @Test
    void moderatorUser_DeleteOtherUsersComment_Success() throws Exception {
        Comment comment = createCommentForUser(user1, "User1 spam comment");

        mockMvc.perform(delete("/api/v1/comments/" + comment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertFalse(commentRepository.existsById(comment.getId()));
    }

    // ================= ADMIN =================

    @Test
    void adminUser_PutOtherUsersComment_Success() throws Exception {
        Comment comment = createCommentForUser(user1, "User1 text to edit");

        mockMvc.perform(put("/api/v1/comments/" + comment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Admin edited text"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.text").value("Admin edited text"));
    }

    @Test
    void adminUser_DeleteOtherUsersComment_Success() throws Exception {
        Comment comment = createCommentForUser(user1, "User1 text to remove");

        mockMvc.perform(delete("/api/v1/comments/" + comment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertFalse(commentRepository.existsById(comment.getId()));
    }
}

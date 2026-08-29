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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import uz.xitlar.entity.Image;
import uz.xitlar.entity.User;
import uz.xitlar.enums.Role;
import uz.xitlar.repository.ImageRepository;
import uz.xitlar.repository.UserRepository;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImageControllerTest {

    private static final String BASE_URL = "/api/v1/images";
    private static final String ADMIN_USERNAME = "ejavlon";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User regularUser;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteByUsernameNot(ADMIN_USERNAME);

        regularUser = userRepository.save(User.builder()
                .firstName("Regular")
                .lastName("User")
                .username("test_regular_user")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        adminToken = getUserToken(ADMIN_USERNAME, "root");
        userToken = getUserToken("test_regular_user", "password123");
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
    void uploadImage_Success_AsAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-controller.jpg",
                "image/jpeg",
                "controller image bytes".getBytes()
        );

        MvcResult result = mockMvc.perform(multipart(BASE_URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalName").value("test-controller.jpg"))
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.url").isNotEmpty())
                .andReturn();

        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

        // Clean up file
        Image image = imageRepository.findById(id).orElseThrow();
        Path path = Paths.get("./storage/images").resolve(image.getStoredName()).normalize();
        Files.deleteIfExists(path);
    }

    @Test
    void uploadImage_Forbidden_AsUser() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-controller.jpg",
                "image/jpeg",
                "controller image bytes".getBytes()
        );

        mockMvc.perform(multipart(BASE_URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadImage_Unauthorized_WithoutToken() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-controller.jpg",
                "image/jpeg",
                "controller image bytes".getBytes()
        );

        mockMvc.perform(multipart(BASE_URL)
                        .file(file))
                .andExpect(status().isForbidden()); // Security returns 403 Forbidden for unauthorized requests by default unless configured otherwise
    }

    @Test
    void uploadImage_BadRequest_UnsupportedType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-controller.txt",
                "text/plain",
                "text content".getBytes()
        );

        mockMvc.perform(multipart(BASE_URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unsupported file type. Only JPEG, PNG, and WebP are allowed."));
    }

    @Test
    void loadImage_Success_Public() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-load.png",
                "image/png",
                "png bytes".getBytes()
        );

        MvcResult result = mockMvc.perform(multipart(BASE_URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

        // Load publicly without auth token
        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(result1 -> {
                    byte[] content = result1.getResponse().getContentAsByteArray();
                    assertTrue(content.length > 0);
                });

        // Clean up file
        Image image = imageRepository.findById(id).orElseThrow();
        Path path = Paths.get("./storage/images").resolve(image.getStoredName()).normalize();
        Files.deleteIfExists(path);
    }

    @Test
    void deleteImage_Success_AsAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-delete.jpg",
                "image/jpeg",
                "delete bytes".getBytes()
        );

        MvcResult result = mockMvc.perform(multipart(BASE_URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        Image image = imageRepository.findById(id).orElseThrow();
        Path path = Paths.get("./storage/images").resolve(image.getStoredName()).normalize();

        assertTrue(imageRepository.existsById(id));
        assertTrue(Files.exists(path));

        // Delete as Admin
        mockMvc.perform(delete(BASE_URL + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertFalse(imageRepository.existsById(id));
        assertFalse(Files.exists(path));
    }

    @Test
    void deleteImage_Forbidden_AsUser() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-delete.jpg",
                "image/jpeg",
                "delete bytes".getBytes()
        );

        MvcResult result = mockMvc.perform(multipart(BASE_URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        Image image = imageRepository.findById(id).orElseThrow();
        Path path = Paths.get("./storage/images").resolve(image.getStoredName()).normalize();

        // Try delete as User
        mockMvc.perform(delete(BASE_URL + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // Clean up
        Files.deleteIfExists(path);
    }
}

package uz.xitlar.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uz.xitlar.entity.OAuthAccount;
import uz.xitlar.entity.User;
import uz.xitlar.enums.OAuthProvider;
import uz.xitlar.enums.Role;
import uz.xitlar.repository.OAuthAccountRepository;
import uz.xitlar.repository.UserRepository;
import uz.xitlar.service.JwtService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class OAuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OAuthAccountRepository oauthAccountRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("GET /oauth2/authorization/google initiates OAuth2 flow and redirects to Google")
    void oauth2AuthorizationEndpoint_RedirectsToGoogle() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("JWT generated for OAuth user can access protected endpoints")
    void oauthUserJwt_CanAccessProtectedEndpoints() throws Exception {
        // Create an OAuth-only user (no password)
        User oauthUser = User.builder()
                .firstName("OAuth")
                .lastName("User")
                .username("oauth_user_1")
                .email("oauth.user@gmail.com")
                .password(null)
                .role(Role.USER)
                .build();
        User savedUser = userRepository.save(oauthUser);

        OAuthAccount oauthAccount = OAuthAccount.builder()
                .user(savedUser)
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("google-sub-77777")
                .build();
        oauthAccountRepository.save(oauthAccount);

        String jwt = jwtService.generateToken(savedUser);

        // Access protected endpoint: PUT /api/v1/users/me/password
        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "currentPassword": "wrong",
                                    "newPassword": "newPassword123",
                                    "confirmPassword": "newPassword123"
                                }
                                """)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    @DisplayName("OAuth user with Role.USER cannot access admin-only endpoints (403 Forbidden)")
    void oauthUser_CannotAccessAdminEndpoints() throws Exception {
        User oauthUser = User.builder()
                .firstName("OAuth")
                .lastName("User")
                .username("oauth_normal_user")
                .email("normal.oauth@gmail.com")
                .password(null)
                .role(Role.USER)
                .build();
        User savedUser = userRepository.save(oauthUser);

        String jwt = jwtService.generateToken(savedUser);

        mockMvc.perform(get("/api/v1/moderators")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OAuth-only user with null password cannot log in via password authentication (/api/v1/sign-in)")
    void oauthUser_CannotLoginViaPasswordAuth() throws Exception {
        User oauthUser = User.builder()
                .firstName("OAuth")
                .lastName("Only")
                .username("oauth_no_password_user")
                .email("nopassword@gmail.com")
                .password(null)
                .role(Role.USER)
                .build();
        userRepository.save(oauthUser);

        mockMvc.perform(post("/api/v1/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "oauth_no_password_user",
                                    "password": "some_random_password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Existing username/password authentication continues to work normally")
    void normalUsernamePasswordLogin_WorksNormally() throws Exception {
        mockMvc.perform(post("/api/v1/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "ejavlon",
                                    "password": "root"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNotEmpty());
    }
}

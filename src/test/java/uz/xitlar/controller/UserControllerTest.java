package uz.xitlar.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uz.xitlar.repository.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    private static final String BASE_URL = "/api/v1";
    private static final String ADMIN_USERNAME = "ejavlon";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanupTestData() {
        userRepository.deleteByUsernameNot(ADMIN_USERNAME);
    }

    @Test
    void signUpRegistersNewUser() throws Exception {
        mockMvc.perform(post(BASE_URL + "/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Test",
                                  "lastName": "User",
                                  "username": "signup_user_1",
                                  "password": "password123",
                                  "confirmPassword": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Successfully registered"));
    }

    @Test
    void signUpRejectsDuplicateUsername() throws Exception {
        String body = """
                {
                  "firstName": "Test",
                  "lastName": "User",
                  "username": "signup_duplicate_user",
                  "password": "password123",
                  "confirmPassword": "password123"
                }
                """;

        mockMvc.perform(post(BASE_URL + "/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE_URL + "/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    @Test
    void signUpRejectsPasswordMismatch() throws Exception {
        mockMvc.perform(post(BASE_URL + "/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Test",
                                  "lastName": "User",
                                  "username": "signup_mismatch_user",
                                  "password": "password123",
                                  "confirmPassword": "different"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Passwords do not match"));
    }

    @Test
    void signUpRejectsBlankFields() throws Exception {
        mockMvc.perform(post(BASE_URL + "/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "",
                                  "lastName": "User",
                                  "username": "",
                                  "password": "password123",
                                  "confirmPassword": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void signInReturnsTokenForValidCredentials() throws Exception {
        mockMvc.perform(post(BASE_URL + "/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "ejavlon", "password": "root"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    @Test
    void signInRejectsWrongPassword() throws Exception {
        mockMvc.perform(post(BASE_URL + "/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "ejavlon", "password": "wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void signInRejectsMissingUsername() throws Exception {
        mockMvc.perform(post(BASE_URL + "/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "root"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("username: Username must not be blank"));
    }

    @Test
    void updateUserAsAdminReturnsUpdatedUser() throws Exception {
        createUser("crud_update_user");
        Integer id = getUserId("crud_update_user");

        mockMvc.perform(put(BASE_URL + "/users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Updated",
                                  "lastName": "Name",
                                  "username": "crud_update_user_renamed"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.firstName").value("Updated"))
                .andExpect(jsonPath("$.data.lastName").value("Name"))
                .andExpect(jsonPath("$.data.username").value("crud_update_user_renamed"));
    }

    @Test
    void updateRejectsDuplicateUsernameOfAnotherUser() throws Exception {
        createUser("crud_dup_user_a");
        createUser("crud_dup_user_b");
        Integer idA = getUserId("crud_dup_user_a");

        mockMvc.perform(put(BASE_URL + "/users/" + idA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Test",
                                  "lastName": "User",
                                  "username": "crud_dup_user_b"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    @Test
    void updateAllowsKeepingSameUsername() throws Exception {
        createUser("crud_keep_username");
        Integer id = getUserId("crud_keep_username");

        mockMvc.perform(put(BASE_URL + "/users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Same",
                                  "lastName": "Username",
                                  "username": "crud_keep_username"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateRejectsBlankFields() throws Exception {
        createUser("crud_blank_update");
        Integer id = getUserId("crud_blank_update");

        mockMvc.perform(put(BASE_URL + "/users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "", "lastName": "User", "username": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void deleteUserRemovesUserAndSecondDeleteReturns404() throws Exception {
        createUser("crud_delete_user");
        Integer id = getUserId("crud_delete_user");

        mockMvc.perform(delete(BASE_URL + "/users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Successfully deleted"));

        mockMvc.perform(delete(BASE_URL + "/users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void adminCannotDeleteOwnAccount() throws Exception {
        Integer ownId = getUserId("ejavlon");

        mockMvc.perform(delete(BASE_URL + "/users/" + ownId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void updateWithoutAuthenticationIsRejected() throws Exception {
        mockMvc.perform(put(BASE_URL + "/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "X", "lastName": "Y", "username": "z"}
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void deleteWithoutAuthenticationIsRejected() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/users/1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void userChangesOwnPasswordAndOldPasswordStopsWorking() throws Exception {
        createUser("pw_self_user");
        String token = getUserToken("pw_self_user", "password123");

        mockMvc.perform(put(BASE_URL + "/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "password123",
                                  "newPassword": "newpass456",
                                  "confirmPassword": "newpass456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password successfully changed"));

        mockMvc.perform(post(BASE_URL + "/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "pw_self_user", "password": "password123"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(BASE_URL + "/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "pw_self_user", "password": "newpass456"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void changeOwnPasswordRejectsWrongCurrentPassword() throws Exception {
        createUser("pw_wrong_current");
        String token = getUserToken("pw_wrong_current", "password123");

        mockMvc.perform(put(BASE_URL + "/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "incorrect",
                                  "newPassword": "newpass456",
                                  "confirmPassword": "newpass456"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    void changeOwnPasswordRejectsConfirmationMismatch() throws Exception {
        createUser("pw_conf_mismatch");
        String token = getUserToken("pw_conf_mismatch", "password123");

        mockMvc.perform(put(BASE_URL + "/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "password123",
                                  "newPassword": "newpass456",
                                  "confirmPassword": "different789"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Passwords do not match"));
    }

    @Test
    void adminResetsUserPasswordAndNewPasswordWorks() throws Exception {
        createUser("pw_reset_target");
        Integer id = getUserId("pw_reset_target");

        mockMvc.perform(put(BASE_URL + "/users/" + id + "/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword": "resetpass789"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post(BASE_URL + "/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "pw_reset_target", "password": "resetpass789"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotUseAdminResetEndpoint() throws Exception {
        createUser("pw_forbidden");
        Integer id = getUserId("pw_forbidden");
        String token = getUserToken("pw_forbidden", "password123");

        mockMvc.perform(put(BASE_URL + "/users/" + id + "/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword": "hacked123"}
                                """))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(post(BASE_URL + "/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "pw_forbidden", "password": "hacked123"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeOwnPasswordWithoutAuthenticationIsRejected() throws Exception {
        mockMvc.perform(put(BASE_URL + "/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "x",
                                  "newPassword": "y",
                                  "confirmPassword": "y"
                                }
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void adminPromotesUserToAdminAndNewAdminGainsAccess() throws Exception {
        createUser("role_promoted");
        Integer id = getUserId("role_promoted");

        mockMvc.perform(put(BASE_URL + "/users/" + id + "/role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        String promotedToken = getUserToken("role_promoted", "password123");

        mockMvc.perform(get("/nonexistent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + promotedToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCannotChangeOwnRole() throws Exception {
        Integer ownId = getUserId("ejavlon");

        mockMvc.perform(put(BASE_URL + "/users/" + ownId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "USER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Admin cannot change own role"));
    }

    @Test
    void updateRoleRejectsInvalidRoleValue() throws Exception {
        createUser("role_invalid_value");
        Integer id = getUserId("role_invalid_value");

        mockMvc.perform(put(BASE_URL + "/users/" + id + "/role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "SUPERUSER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void updateRoleRejectsMissingRole() throws Exception {
        createUser("role_missing");
        Integer id = getUserId("role_missing");

        mockMvc.perform(put(BASE_URL + "/users/" + id + "/role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("role: Role must not be null"));
    }

    @Test
    void updateRoleWithoutAuthenticationIsRejected() throws Exception {
        mockMvc.perform(put(BASE_URL + "/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "ADMIN"}
                                """))
                .andExpect(status().is4xxClientError());
    }

    private void createUser(String username) throws Exception {
        mockMvc.perform(post(BASE_URL + "/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Test",
                                  "lastName": "User",
                                  "username": "%s",
                                  "password": "password123",
                                  "confirmPassword": "password123"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk());
    }

    private Integer getUserId(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow()
                .getId();
    }

    private String getAdminToken() throws Exception {
        return getUserToken("ejavlon", "root");
    }

    private String getUserToken(String username, String password) throws Exception {
        String response = mockMvc.perform(post(BASE_URL + "/sign-in")
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
    void protectedEndpointWithInvalidTokenReturns4xxNot5xx() throws Exception {
        mockMvc.perform(get("/nonexistent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void validTokenGrantsAccessToProtectedEndpoint() throws Exception {
        String signInResponse = mockMvc.perform(post(BASE_URL + "/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "ejavlon", "password": "root"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = JsonPath.read(signInResponse, "$.data");

        mockMvc.perform(get("/nonexistent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}

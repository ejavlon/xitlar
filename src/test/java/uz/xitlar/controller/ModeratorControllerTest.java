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
class ModeratorControllerTest {

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
    void adminCreatesModeratorAndModeratorSignsIn() throws Exception {
        mockMvc.perform(post(BASE_URL + "/moderators")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Mod",
                                  "lastName": "Erator",
                                  "username": "mod_created_1",
                                  "password": "modpass123",
                                  "confirmPassword": "modpass123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("mod_created_1"))
                .andExpect(jsonPath("$.data.role").value("MODERATOR"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        mockMvc.perform(post(BASE_URL + "/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "mod_created_1", "password": "modpass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    @Test
    void createModeratorRejectsDuplicateUsername() throws Exception {
        createModerator("mod_dup_user");
        Integer id = getId("mod_dup_user");

        mockMvc.perform(post(BASE_URL + "/moderators")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Other",
                                  "lastName": "Moderator",
                                  "username": "mod_dup_user",
                                  "password": "modpass123",
                                  "confirmPassword": "modpass123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Username already exists"));

        deleteModerator(id);
    }

    @Test
    void createModeratorRejectsPasswordMismatch() throws Exception {
        mockMvc.perform(post(BASE_URL + "/moderators")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Mod",
                                  "lastName": "Erator",
                                  "username": "mod_mismatch_user",
                                  "password": "modpass123",
                                  "confirmPassword": "different"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Passwords do not match"));
    }

    @Test
    void moderatorUpdatesRegularUserProfile() throws Exception {
        createModerator("mod_profile_manager");
        String token = signIn("mod_profile_manager", "modpass123");
        createUser("mod_target_regular");

        Integer id = getId("mod_target_regular");

        mockMvc.perform(put(BASE_URL + "/users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Renamed",
                                  "lastName": "ByMod",
                                  "username": "mod_target_regular"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Renamed"));
    }

    @Test
    void moderatorCannotResetUserPassword() throws Exception {
        createModerator("mod_password_manager");
        String token = signIn("mod_password_manager", "modpass123");
        createUser("mod_pw_target");

        Integer id = getId("mod_pw_target");

        mockMvc.perform(put(BASE_URL + "/users/" + id + "/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword": "resetbymod789"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderatorCanListUsers() throws Exception {
        createModerator("mod_lister");
        String token = signIn("mod_lister", "modpass123");
        createUser("mod_list_target");

        mockMvc.perform(get(BASE_URL + "/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Integer id = getId("mod_list_target");

        mockMvc.perform(get(BASE_URL + "/users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));
    }

    @Test
    void moderatorCannotViewNonUserAccountsViaUsersEndpoints() throws Exception {
        createModerator("mod_reader");
        String token = signIn("mod_reader", "modpass123");
        Integer adminId = getId("ejavlon");

        mockMvc.perform(get(BASE_URL + "/users/" + adminId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        String listResponse = mockMvc.perform(get(BASE_URL + "/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(
                ((java.util.List<?>) JsonPath.read(listResponse, "$.data[?(@.username == 'ejavlon')]")).isEmpty(),
                "Admin account must not be visible to moderators via users list");
    }

    @Test
    void moderatorCannotDeleteUser() throws Exception {
        createModerator("mod_no_delete");
        String token = signIn("mod_no_delete", "modpass123");
        createUser("mod_delete_target");
        Integer id = getId("mod_delete_target");

        mockMvc.perform(delete(BASE_URL + "/users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderatorCannotChangeAnyRoleIncludingOwn() throws Exception {
        createModerator("mod_no_role_change");
        Integer ownId = getId("mod_no_role_change");
        String token = signIn("mod_no_role_change", "modpass123");

        createUser("mod_role_target");
        Integer userId = getId("mod_role_target");

        mockMvc.perform(put(BASE_URL + "/users/" + ownId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "ADMIN"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(BASE_URL + "/users/" + userId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "MODERATOR"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderatorCannotManageModeratorAccounts() throws Exception {
        createModerator("mod_isolated");
        String token = signIn("mod_isolated", "modpass123");
        createModerator("mod_other_mod");
        Integer otherModeratorId = getId("mod_other_mod");

        mockMvc.perform(put(BASE_URL + "/users/" + otherModeratorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Hacked",
                                  "lastName": "Moderator",
                                  "username": "mod_other_mod"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(BASE_URL + "/users/" + otherModeratorId + "/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword": "hacked789"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUserCannotAccessUserManagement() throws Exception {
        createUser("reg_plain_user");
        createUser("reg_other_user");
        String token = signIn("reg_plain_user", "password123");
        Integer otherId = getId("reg_other_user");

        mockMvc.perform(get(BASE_URL + "/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(BASE_URL + "/users/" + otherId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "X",
                                  "lastName": "Y",
                                  "username": "reg_other_user"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(BASE_URL + "/users/" + otherId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(BASE_URL + "/moderators")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Evil",
                                  "lastName": "User",
                                  "username": "reg_evil_mod",
                                  "password": "evilpass1",
                                  "confirmPassword": "evilpass1"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUpdatesModeratorProfile() throws Exception {
        createModerator("mod_update_me");
        Integer id = getId("mod_update_me");

        mockMvc.perform(put(BASE_URL + "/moderators/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Updated",
                                  "lastName": "Moderator",
                                  "username": "mod_update_me"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Updated"))
                .andExpect(jsonPath("$.data.role").value("MODERATOR"));
    }

    @Test
    void adminDeletesModeratorAndSecondDeleteReturns404() throws Exception {
        createModerator("mod_delete_me");
        Integer id = getId("mod_delete_me");

        mockMvc.perform(delete(BASE_URL + "/moderators/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete(BASE_URL + "/moderators/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void adminResetsModeratorPasswordAndNewPasswordWorks() throws Exception {
        createModerator("mod_reset_me");
        Integer id = getId("mod_reset_me");

        mockMvc.perform(put(BASE_URL + "/moderators/" + id + "/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword": "newmodpass456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post(BASE_URL + "/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "mod_reset_me", "password": "newmodpass456"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void adminPromotesUserToModeratorAndPromotedManagesUsers() throws Exception {
        createUser("promote_to_mod");
        Integer id = getId("promote_to_mod");

        mockMvc.perform(put(BASE_URL + "/users/" + id + "/role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "MODERATOR"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MODERATOR"));

        String token = signIn("promote_to_mod", "password123");
        createUser("managed_by_promoted");
        Integer managedId = getId("managed_by_promoted");

        mockMvc.perform(put(BASE_URL + "/users/" + managedId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Managed",
                                  "lastName": "ByPromoted",
                                  "username": "managed_by_promoted"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void moderatorsListHidesNonModeratorAccounts() throws Exception {
        createModerator("mod_visible");
        createUser("hidden_regular");
        Integer adminId = getId("ejavlon");

        String listResponse = mockMvc.perform(get(BASE_URL + "/moderators")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(
                ((java.util.List<?>) JsonPath.read(listResponse, "$.data[?(@.username == 'mod_visible')]")).size() > 0,
                "Created moderator must appear in moderators list");
        org.junit.jupiter.api.Assertions.assertTrue(
                ((java.util.List<?>) JsonPath.read(listResponse, "$.data[?(@.username == 'hidden_regular')]")).isEmpty(),
                "Regular user must not appear in moderators list");
        org.junit.jupiter.api.Assertions.assertTrue(
                ((java.util.List<?>) JsonPath.read(listResponse, "$.data[?(@.username == 'ejavlon')]")).isEmpty(),
                "Admin must not appear in moderators list");

        mockMvc.perform(get(BASE_URL + "/moderators/" + adminId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken()))
                .andExpect(status().isNotFound());
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

    private void createModerator(String username) throws Exception {
        mockMvc.perform(post(BASE_URL + "/moderators")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Mod",
                                  "lastName": "Erator",
                                  "username": "%s",
                                  "password": "modpass123",
                                  "confirmPassword": "modpass123"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk());
    }

    private void deleteModerator(Integer id) throws Exception {
        mockMvc.perform(delete(BASE_URL + "/moderators/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken()))
                .andExpect(status().isOk());
    }

    private Integer getId(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow()
                .getId();
    }

    private String getAdminToken() throws Exception {
        return signIn("ejavlon", "root");
    }

    private String signIn(String username, String password) throws Exception {
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
}

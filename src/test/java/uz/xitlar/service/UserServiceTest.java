package uz.xitlar.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import uz.xitlar.dto.ResetPasswordDto;
import uz.xitlar.dto.UpdateRoleDto;
import uz.xitlar.dto.UpdateUserDto;
import uz.xitlar.entity.User;
import uz.xitlar.enums.Role;
import uz.xitlar.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class UserServiceTest {

    private static final String ADMIN_USERNAME = "ejavlon";

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User moderator;
    private User regularUser;
    private User targetUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteByUsernameNot(ADMIN_USERNAME);

        moderator = userRepository.save(User.builder()
                .firstName("Mod")
                .lastName("Erator")
                .username("test_mod")
                .password(passwordEncoder.encode("modpass123"))
                .role(Role.MODERATOR)
                .build());

        regularUser = userRepository.save(User.builder()
                .firstName("Regular")
                .lastName("User")
                .username("test_user")
                .password(passwordEncoder.encode("userpass123"))
                .role(Role.USER)
                .build());

        targetUser = userRepository.save(User.builder()
                .firstName("Target")
                .lastName("User")
                .username("target_user")
                .password(passwordEncoder.encode("targetpass123"))
                .role(Role.USER)
                .build());
    }

    @Test
    void moderatorCannotDeleteUser() {
        assertThrows(AccessDeniedException.class, () ->
                userService.delete(moderator.getUsername(), targetUser.getId()));
    }

    @Test
    void userCannotDeleteUser() {
        assertThrows(AccessDeniedException.class, () ->
                userService.delete(regularUser.getUsername(), targetUser.getId()));
    }

    @Test
    void moderatorCannotResetPassword() {
        ResetPasswordDto dto = new ResetPasswordDto("newpass789");
        assertThrows(AccessDeniedException.class, () ->
                userService.resetPassword(moderator.getUsername(), targetUser.getId(), dto));
    }

    @Test
    void userCannotResetPassword() {
        ResetPasswordDto dto = new ResetPasswordDto("newpass789");
        assertThrows(AccessDeniedException.class, () ->
                userService.resetPassword(regularUser.getUsername(), targetUser.getId(), dto));
    }

    @Test
    void moderatorCannotUpdateRole() {
        UpdateRoleDto dto = new UpdateRoleDto(Role.ADMIN);
        assertThrows(AccessDeniedException.class, () ->
                userService.updateRole(targetUser.getId(), moderator.getUsername(), dto));
    }

    @Test
    void userCannotUpdateRole() {
        UpdateRoleDto dto = new UpdateRoleDto(Role.ADMIN);
        assertThrows(AccessDeniedException.class, () ->
                userService.updateRole(targetUser.getId(), regularUser.getUsername(), dto));
    }

    @Test
    void userCannotListUsers() {
        assertThrows(AccessDeniedException.class, () ->
                userService.getUsers(regularUser.getUsername()));
    }

    @Test
    void userCannotGetUserById() {
        assertThrows(AccessDeniedException.class, () ->
                userService.getUser(regularUser.getUsername(), targetUser.getId()));
    }

    @Test
    void userCannotUpdateAnotherUser() {
        UpdateUserDto dto = new UpdateUserDto("Updated", "Name", "target_user");
        assertThrows(AccessDeniedException.class, () ->
                userService.update(regularUser.getUsername(), targetUser.getId(), dto));
    }

    @Test
    void adminCanPerformAllManagementOperations() {
        // Admin resets password
        ResetPasswordDto resetDto = new ResetPasswordDto("adminreset123");
        var resetRes = userService.resetPassword(ADMIN_USERNAME, targetUser.getId(), resetDto);
        assertTrue(resetRes.getSuccess());

        // Admin updates role
        UpdateRoleDto roleDto = new UpdateRoleDto(Role.MODERATOR);
        var roleRes = userService.updateRole(targetUser.getId(), ADMIN_USERNAME, roleDto);
        assertTrue(roleRes.getSuccess());
        assertEquals(Role.MODERATOR, roleRes.getData().getRole());

        // Admin deletes user
        var deleteRes = userService.delete(ADMIN_USERNAME, targetUser.getId());
        assertTrue(deleteRes.getSuccess());
        assertTrue(userRepository.findById(targetUser.getId()).isEmpty());
    }

    @Test
    void moderatorCanManageOnlyRegularUsers() {
        // Moderator can get list of regular users
        var usersRes = userService.getUsers(moderator.getUsername());
        assertTrue(usersRes.getSuccess());
        assertNotNull(usersRes.getData());
        assertTrue(usersRes.getData().stream().allMatch(u -> u.getRole() == Role.USER));

        // Moderator can get regular user
        var userRes = userService.getUser(moderator.getUsername(), targetUser.getId());
        assertTrue(userRes.getSuccess());
        assertEquals("target_user", userRes.getData().getUsername());

        // Moderator can update regular user
        UpdateUserDto updateDto = new UpdateUserDto("ModUpdated", "User", "target_user");
        var updateRes = userService.update(moderator.getUsername(), targetUser.getId(), updateDto);
        assertTrue(updateRes.getSuccess());
        assertEquals("ModUpdated", updateRes.getData().getFirstName());
    }
}

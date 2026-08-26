package uz.xitlar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.xitlar.dto.ResponseApi;
import uz.xitlar.dto.ResetPasswordDto;
import uz.xitlar.dto.SignInDto;
import uz.xitlar.dto.SignUpDto;
import uz.xitlar.dto.UpdatePasswordDto;
import uz.xitlar.dto.UpdateRoleDto;
import uz.xitlar.dto.UpdateUserDto;
import uz.xitlar.dto.UserResponse;
import uz.xitlar.entity.User;
import uz.xitlar.enums.Role;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.exception.DuplicateEntityException;
import uz.xitlar.exception.PasswordMismatchException;
import uz.xitlar.exception.SelfRoleChangeException;
import uz.xitlar.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public ResponseApi<Void> signUp(SignUpDto signUpDto){
        if (userRepository.existsByUsername(signUpDto.getUsername())) {
            throw new DuplicateEntityException("Username already exists");
        }

        if (!signUpDto.getPassword().equals(signUpDto.getConfirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match");
        }

        User user = User.builder()
                .firstName(signUpDto.getFirstName())
                .lastName(signUpDto.getLastName())
                .username(signUpDto.getUsername())
                .password(passwordEncoder.encode(signUpDto.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(user);

        return ResponseApi.<Void>builder()
                .success(true)
                .message("Successfully registered")
                .build();
    }

    public ResponseApi<String> signIn(SignInDto signInDto){
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        signInDto.getUsername(),
                        signInDto.getPassword()
                )
        );

        User user = findUserByUsernameOrThrow(signInDto.getUsername());

        return ResponseApi.<String>builder()
                .success(true)
                .message("Welcome to system")
                .data(jwtService.generateToken(user))
                .build();
    }

    public ResponseApi<UserResponse> getUser(String actorUsername, Integer id){
        User actor = findUserByUsernameOrThrow(actorUsername);
        User user = findUserByIdOrThrow(id);

        assertModeratorCanManage(actor, user);

        return ResponseApi.<UserResponse>builder()
                .success(true)
                .message("Success")
                .data(toResponse(user))
                .build();
    }

    public ResponseApi<List<UserResponse>> getUsers(String actorUsername){
        User actor = findUserByUsernameOrThrow(actorUsername);

        List<UserResponse> users = userRepository.findAll().stream()
                .filter(user -> actor.getRole() != Role.MODERATOR || user.getRole() == Role.USER)
                .map(this::toResponse)
                .toList();

        return ResponseApi.<List<UserResponse>>builder()
                .success(true)
                .message("Success")
                .data(users)
                .build();
    }

    @Transactional
    public ResponseApi<UserResponse> update(String actorUsername, Integer id, UpdateUserDto updateUserDto){
        User actor = findUserByUsernameOrThrow(actorUsername);
        User user = findUserByIdOrThrow(id);

        assertModeratorCanManage(actor, user);

        if (userRepository.existsByUsernameAndIdNot(updateUserDto.getUsername(), id)) {
            throw new DuplicateEntityException("Username already exists");
        }

        user.setFirstName(updateUserDto.getFirstName());
        user.setLastName(updateUserDto.getLastName());
        user.setUsername(updateUserDto.getUsername());

        User saved = userRepository.save(user);

        return ResponseApi.<UserResponse>builder()
                .success(true)
                .message("Successfully updated")
                .data(toResponse(saved))
                .build();
    }

    @Transactional
    public ResponseApi<Void> delete(Integer id){
        User user = findUserByIdOrThrow(id);

        userRepository.delete(user);

        return ResponseApi.<Void>builder()
                .success(true)
                .message("Successfully deleted")
                .build();
    }

    @Transactional
    public ResponseApi<Void> changeOwnPassword(String username, UpdatePasswordDto updatePasswordDto){
        User user = findUserByUsernameOrThrow(username);

        if (!passwordEncoder.matches(updatePasswordDto.getCurrentPassword(), user.getPassword())) {
            throw new PasswordMismatchException("Current password is incorrect");
        }

        if (!updatePasswordDto.getNewPassword().equals(updatePasswordDto.getConfirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match");
        }

        user.setPassword(passwordEncoder.encode(updatePasswordDto.getNewPassword()));
        userRepository.save(user);

        return ResponseApi.<Void>builder()
                .success(true)
                .message("Password successfully changed")
                .build();
    }

    @Transactional
    public ResponseApi<Void> resetPassword(String actorUsername, Integer id, ResetPasswordDto resetPasswordDto){
        User actor = findUserByUsernameOrThrow(actorUsername);
        User user = findUserByIdOrThrow(id);

        assertModeratorCanManage(actor, user);

        user.setPassword(passwordEncoder.encode(resetPasswordDto.getNewPassword()));
        userRepository.save(user);

        return ResponseApi.<Void>builder()
                .success(true)
                .message("Password successfully changed")
                .build();
    }

    @Transactional
    public ResponseApi<UserResponse> updateRole(Integer id, String actorUsername, UpdateRoleDto updateRoleDto){
        User actor = findUserByUsernameOrThrow(actorUsername);

        if (actor.getId().equals(id)) {
            throw new SelfRoleChangeException("Admin cannot change own role");
        }

        User user = findUserByIdOrThrow(id);
        user.setRole(updateRoleDto.getRole());

        User saved = userRepository.save(user);

        return ResponseApi.<UserResponse>builder()
                .success(true)
                .message("Role successfully updated")
                .data(toResponse(saved))
                .build();
    }

    public User findUserByIdOrThrow(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("User not found"));
    }

    public User findUserByUsernameOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new DataNotFoundException("User not found"));
    }

    private void assertModeratorCanManage(User actor, User target) {
        if (actor.getRole() == Role.MODERATOR && target.getRole() != Role.USER) {
            throw new AccessDeniedException("Moderators can only manage users");
        }
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }
}

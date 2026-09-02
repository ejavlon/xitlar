package uz.xitlar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.dto.user.CreateModeratorDto;
import uz.xitlar.dto.user.ModeratorResponse;
import uz.xitlar.dto.user.ResetPasswordDto;
import uz.xitlar.dto.user.UpdateModeratorDto;
import uz.xitlar.entity.User;
import uz.xitlar.enums.Role;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.exception.DuplicateEntityException;
import uz.xitlar.exception.PasswordMismatchException;
import uz.xitlar.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModeratorService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ResponseApi<ModeratorResponse> create(CreateModeratorDto createModeratorDto){
        if (userRepository.existsByUsername(createModeratorDto.getUsername())) {
            throw new DuplicateEntityException("Username already exists");
        }

        if (!createModeratorDto.getPassword().equals(createModeratorDto.getConfirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match");
        }

        User moderator = User.builder()
                .firstName(createModeratorDto.getFirstName())
                .lastName(createModeratorDto.getLastName())
                .username(createModeratorDto.getUsername())
                .password(passwordEncoder.encode(createModeratorDto.getPassword()))
                .role(Role.MODERATOR)
                .build();

        User saved = userRepository.save(moderator);

        return ResponseApi.<ModeratorResponse>builder()
                .success(true)
                .message("Moderator successfully created")
                .data(toResponse(saved))
                .build();
    }

    public ResponseApi<List<ModeratorResponse>> getModerators(){
        List<ModeratorResponse> moderators = userRepository.findByRole(Role.MODERATOR).stream()
                .map(this::toResponse)
                .toList();

        return ResponseApi.<List<ModeratorResponse>>builder()
                .success(true)
                .message("Success")
                .data(moderators)
                .build();
    }

    public ResponseApi<ModeratorResponse> getModerator(Integer id){
        User moderator = findModeratorOrThrow(id);

        return ResponseApi.<ModeratorResponse>builder()
                .success(true)
                .message("Success")
                .data(toResponse(moderator))
                .build();
    }

    @Transactional
    public ResponseApi<ModeratorResponse> update(Integer id, UpdateModeratorDto updateModeratorDto){
        User moderator = findModeratorOrThrow(id);

        if (userRepository.existsByUsernameAndIdNot(updateModeratorDto.getUsername(), id)) {
            throw new DuplicateEntityException("Username already exists");
        }

        moderator.setFirstName(updateModeratorDto.getFirstName());
        moderator.setLastName(updateModeratorDto.getLastName());
        moderator.setUsername(updateModeratorDto.getUsername());

        User saved = userRepository.save(moderator);

        return ResponseApi.<ModeratorResponse>builder()
                .success(true)
                .message("Successfully updated")
                .data(toResponse(saved))
                .build();
    }

    @Transactional
    public ResponseApi<Void> delete(Integer id){
        User moderator = findModeratorOrThrow(id);

        userRepository.delete(moderator);

        return ResponseApi.<Void>builder()
                .success(true)
                .message("Successfully deleted")
                .build();
    }

    @Transactional
    public ResponseApi<Void> resetPassword(Integer id, ResetPasswordDto resetPasswordDto){
        User moderator = findModeratorOrThrow(id);

        moderator.setPassword(passwordEncoder.encode(resetPasswordDto.getNewPassword()));
        userRepository.save(moderator);

        return ResponseApi.<Void>builder()
                .success(true)
                .message("Password successfully changed")
                .build();
    }

    private User findModeratorOrThrow(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Moderator not found"));

        if (user.getRole() != Role.MODERATOR) {
            throw new DataNotFoundException("Moderator not found");
        }

        return user;
    }

    private ModeratorResponse toResponse(User moderator) {
        return ModeratorResponse.builder()
                .id(moderator.getId())
                .firstName(moderator.getFirstName())
                .lastName(moderator.getLastName())
                .username(moderator.getUsername())
                .role(moderator.getRole())
                .build();
    }
}

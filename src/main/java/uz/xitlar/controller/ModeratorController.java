package uz.xitlar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.xitlar.dto.ResponseApi;
import uz.xitlar.dto.UpdateUserDto;
import uz.xitlar.dto.UserResponse;
import uz.xitlar.service.UserService;

import java.util.List;

@Tag(name = "Moderator Controller", description = "Moderator amallari (MODERATOR va ADMIN huquqiga ega foydalanuvchilar uchun)")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class ModeratorController {

    private final UserService userService;

    @Operation(summary = "Barcha foydalanuvchilar ro'yxati", description = "Barcha foydalanuvchilarni olish (ADMIN yoki MODERATOR)")
    @GetMapping
    public ResponseApi<List<UserResponse>> getUsers(@AuthenticationPrincipal UserDetails principal) {
        return userService.getUsers(principal.getUsername());
    }

    @Operation(summary = "Foydalanuvchi ma'lumotlarini ID bo'yicha olish", description = "Berilgan ID bo'yicha foydalanuvchini olish (ADMIN yoki MODERATOR)")
    @GetMapping("/{id}")
    public ResponseApi<UserResponse> getUser(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable Integer id) {
        return userService.getUser(principal.getUsername(), id);
    }

    @Operation(summary = "Foydalanuvchi ma'lumotlarini tahrirlash", description = "Foydalanuvchi ma'lumotlarini yangilash (ADMIN yoki MODERATOR)")
    @PutMapping("/{id}")
    public ResponseApi<UserResponse> update(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable Integer id,
                                            @Valid @RequestBody UpdateUserDto updateUserDto) {
        return userService.update(principal.getUsername(), id, updateUserDto);
    }
}

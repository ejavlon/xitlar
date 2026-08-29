package uz.xitlar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.dto.user.CreateModeratorDto;
import uz.xitlar.dto.user.ModeratorResponse;
import uz.xitlar.dto.user.ResetPasswordDto;
import uz.xitlar.dto.user.UpdateModeratorDto;
import uz.xitlar.dto.user.UpdateRoleDto;
import uz.xitlar.dto.user.UserResponse;
import uz.xitlar.service.ModeratorService;
import uz.xitlar.service.UserService;

import java.util.List;

@Tag(name = "Admin Controller", description = "Admin boshqaruv amallari (Faqat ADMIN huquqiga ega foydalanuvchilar uchun)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final ModeratorService moderatorService;

    // ======================== USER BOSHQARUVI (ADMIN ONLY) ========================

    @Operation(summary = "Foydalanuvchini o'chirish", description = "Foydalanuvchini tizimdan butunlay o'chirish (Faqat ADMIN)")
    @DeleteMapping("/users/{id}")
    public ResponseApi<Void> deleteUser(@AuthenticationPrincipal UserDetails principal,
                                        @PathVariable Integer id) {
        return userService.delete(principal.getUsername(), id);
    }

    @Operation(summary = "Foydalanuvchi parolini tiklash / o'zgartirish", description = "Admin tomonidan foydalanuvchi parolini majburiy yangilash (Faqat ADMIN)")
    @PutMapping("/users/{id}/password")
    public ResponseApi<Void> resetUserPassword(@AuthenticationPrincipal UserDetails principal,
                                               @PathVariable Integer id,
                                               @Valid @RequestBody ResetPasswordDto resetPasswordDto) {
        return userService.resetPassword(principal.getUsername(), id, resetPasswordDto);
    }

    @Operation(summary = "Foydalanuvchi rolini o'zgartirish", description = "Foydalanuvchiga ADMIN, MODERATOR yoki USER rolini berish (Faqat ADMIN)")
    @PutMapping("/users/{id}/role")
    public ResponseApi<UserResponse> updateRole(@AuthenticationPrincipal UserDetails principal,
                                                @PathVariable Integer id,
                                                @Valid @RequestBody UpdateRoleDto updateRoleDto) {
        return userService.updateRole(id, principal.getUsername(), updateRoleDto);
    }

    // ======================== MODERATOR BOSHQARUVI (ADMIN ONLY) ========================

    @Operation(summary = "Yangi moderator yaratish", description = "Tizimga yangi moderator qo'shish (Faqat ADMIN)")
    @PostMapping("/moderators")
    public ResponseApi<ModeratorResponse> createModerator(@Valid @RequestBody CreateModeratorDto createModeratorDto) {
        return moderatorService.create(createModeratorDto);
    }

    @Operation(summary = "Barcha moderatorlar ro'yxati", description = "Barcha moderatorlarni olish (Faqat ADMIN)")
    @GetMapping("/moderators")
    public ResponseApi<List<ModeratorResponse>> getModerators() {
        return moderatorService.getModerators();
    }

    @Operation(summary = "Moderatorni ID bo'yicha olish", description = "Berilgan ID bo'yicha moderator ma'lumotlarini olish (Faqat ADMIN)")
    @GetMapping("/moderators/{id}")
    public ResponseApi<ModeratorResponse> getModerator(@PathVariable Integer id) {
        return moderatorService.getModerator(id);
    }

    @Operation(summary = "Moderator ma'lumotlarini tahrirlash", description = "Moderator ma'lumotlarini yangilash (Faqat ADMIN)")
    @PutMapping("/moderators/{id}")
    public ResponseApi<ModeratorResponse> updateModerator(@PathVariable Integer id,
                                                          @Valid @RequestBody UpdateModeratorDto updateModeratorDto) {
        return moderatorService.update(id, updateModeratorDto);
    }

    @Operation(summary = "Moderatorni o'chirish", description = "Moderatorni tizimdan o'chirish (Faqat ADMIN)")
    @DeleteMapping("/moderators/{id}")
    public ResponseApi<Void> deleteModerator(@PathVariable Integer id) {
        return moderatorService.delete(id);
    }

    @Operation(summary = "Moderator parolini tiklash / o'zgartirish", description = "Admin tomonidan moderator parolini yangilash (Faqat ADMIN)")
    @PutMapping("/moderators/{id}/password")
    public ResponseApi<Void> resetModeratorPassword(@PathVariable Integer id,
                                                    @Valid @RequestBody ResetPasswordDto resetPasswordDto) {
        return moderatorService.resetPassword(id, resetPasswordDto);
    }
}

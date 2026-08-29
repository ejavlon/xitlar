package uz.xitlar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.dto.user.SignInDto;
import uz.xitlar.dto.user.SignUpDto;
import uz.xitlar.dto.user.UpdatePasswordDto;
import uz.xitlar.service.UserService;

@Tag(name = "User & Auth Controller", description = "Foydalanuvchilar, autentifikatsiya va profil amallari")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Ro'yxatdan o'tish (Sign Up)", description = "Yangi foydalanuvchi hisobini yaratish")
    @PostMapping("/sign-up")
    public ResponseApi<Void> signUp(@Valid @RequestBody SignUpDto signUpDto) {
        return userService.signUp(signUpDto);
    }

    @Operation(summary = "Tizimga kirish (Sign In)", description = "Telefon raqami va parol orqali tizimga kirib JWT token olish")
    @PostMapping("/sign-in")
    public ResponseApi<String> signIn(@Valid @RequestBody SignInDto signInDto) {
        return userService.signIn(signInDto);
    }

    @Operation(summary = "O'z parolini o'zgartirish", description = "Joriy foydalanuvchi o'z parolini yangilashi")
    @PutMapping("/users/me/password")
    public ResponseApi<Void> changeOwnPassword(@AuthenticationPrincipal UserDetails principal,
                                               @Valid @RequestBody UpdatePasswordDto updatePasswordDto) {
        return userService.changeOwnPassword(principal.getUsername(), updatePasswordDto);
    }
}

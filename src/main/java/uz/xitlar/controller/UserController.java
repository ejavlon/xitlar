package uz.xitlar.controller;

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
import uz.xitlar.dto.ResponseApi;
import uz.xitlar.dto.ResetPasswordDto;
import uz.xitlar.dto.SignInDto;
import uz.xitlar.dto.SignUpDto;
import uz.xitlar.dto.UpdatePasswordDto;
import uz.xitlar.dto.UpdateRoleDto;
import uz.xitlar.dto.UpdateUserDto;
import uz.xitlar.dto.UserResponse;
import uz.xitlar.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/hello")
    public ResponseApi<String> hello(){
        return ResponseApi.<String>builder()
                .success(true)
                .message("Application is running")
                .data("Hello, Application is running!")
                .build();
    }

    @PostMapping("/sign-up")
    public ResponseApi<Void> signUp(@Valid @RequestBody SignUpDto signUpDto){
        return userService.signUp(signUpDto);
    }

    @PostMapping("/sign-in")
    public ResponseApi<String> signIn(@Valid @RequestBody SignInDto signInDto){
        return userService.signIn(signInDto);
    }

    @GetMapping("/users")
    public ResponseApi<List<UserResponse>> getUsers(@AuthenticationPrincipal UserDetails principal){
        return userService.getUsers(principal.getUsername());
    }

    @GetMapping("/users/{id}")
    public ResponseApi<UserResponse> getUser(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable Integer id){
        return userService.getUser(principal.getUsername(), id);
    }

    @PutMapping("/users/{id}")
    public ResponseApi<UserResponse> update(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable Integer id,
                                            @Valid @RequestBody UpdateUserDto updateUserDto){
        return userService.update(principal.getUsername(), id, updateUserDto);
    }

    @DeleteMapping("/users/{id}")
    public ResponseApi<Void> delete(@PathVariable Integer id){
        return userService.delete(id);
    }

    @PutMapping("/users/me/password")
    public ResponseApi<Void> changeOwnPassword(@AuthenticationPrincipal UserDetails principal,
                                               @Valid @RequestBody UpdatePasswordDto updatePasswordDto){
        return userService.changeOwnPassword(principal.getUsername(), updatePasswordDto);
    }

    @PutMapping("/users/{id}/password")
    public ResponseApi<Void> resetPassword(@AuthenticationPrincipal UserDetails principal,
                                           @PathVariable Integer id,
                                           @Valid @RequestBody ResetPasswordDto resetPasswordDto){
        return userService.resetPassword(principal.getUsername(), id, resetPasswordDto);
    }

    @PutMapping("/users/{id}/role")
    public ResponseApi<UserResponse> updateRole(@AuthenticationPrincipal UserDetails principal,
                                                @PathVariable Integer id,
                                                @Valid @RequestBody UpdateRoleDto updateRoleDto){
        return userService.updateRole(id, principal.getUsername(), updateRoleDto);
    }
}

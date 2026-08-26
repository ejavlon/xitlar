package uz.xitlar.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.xitlar.dto.CreateModeratorDto;
import uz.xitlar.dto.ModeratorResponse;
import uz.xitlar.dto.ResponseApi;
import uz.xitlar.dto.ResetPasswordDto;
import uz.xitlar.dto.UpdateModeratorDto;
import uz.xitlar.service.ModeratorService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/moderators")
@RequiredArgsConstructor
public class ModeratorController {

    private final ModeratorService moderatorService;

    @PostMapping
    public ResponseApi<ModeratorResponse> create(@Valid @RequestBody CreateModeratorDto createModeratorDto){
        return moderatorService.create(createModeratorDto);
    }

    @GetMapping
    public ResponseApi<List<ModeratorResponse>> getModerators(){
        return moderatorService.getModerators();
    }

    @GetMapping("/{id}")
    public ResponseApi<ModeratorResponse> getModerator(@PathVariable Integer id){
        return moderatorService.getModerator(id);
    }

    @PutMapping("/{id}")
    public ResponseApi<ModeratorResponse> update(@PathVariable Integer id,
                                                 @Valid @RequestBody UpdateModeratorDto updateModeratorDto){
        return moderatorService.update(id, updateModeratorDto);
    }

    @DeleteMapping("/{id}")
    public ResponseApi<Void> delete(@PathVariable Integer id){
        return moderatorService.delete(id);
    }

    @PutMapping("/{id}/password")
    public ResponseApi<Void> resetPassword(@PathVariable Integer id,
                                           @Valid @RequestBody ResetPasswordDto resetPasswordDto){
        return moderatorService.resetPassword(id, resetPasswordDto);
    }
}

package uz.xitlar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.ImageResponse;
import uz.xitlar.dto.ResponseApi;
import uz.xitlar.entity.Image;
import uz.xitlar.service.ImageStorageService;

@Tag(name = "Image Controller", description = "Rasm fayllarini yuklash, yuklab olish va o'chirish amallari")
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageStorageService imageStorageService;

    @Operation(summary = "Yangi rasm yuklash", description = "Tizimga rasm faylini yuklash (JPEG, PNG, WebP)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseApi<ImageResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        return imageStorageService.uploadImage(file);
    }

    @Operation(summary = "Rasmni yuklab olish / ko'rish", description = "ID bo'yicha rasm faylini olish")
    @GetMapping("/{id}")
    public ResponseEntity<Resource> loadImage(@PathVariable Integer id) {
        Resource resource = imageStorageService.loadImageAsResource(id);
        Image image = imageStorageService.getImageEntityOrThrow(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .body(resource);
    }

    @Operation(summary = "Rasmni o'chirish", description = "ID bo'yicha rasmni va uning metadata ma'lumotlarini o'chirish")
    @DeleteMapping("/{id}")
    public ResponseApi<Void> deleteImage(@PathVariable Integer id) {
        return imageStorageService.deleteImage(id);
    }
}

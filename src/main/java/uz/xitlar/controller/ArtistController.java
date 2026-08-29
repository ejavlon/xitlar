package uz.xitlar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.ArtistCreateDto;
import uz.xitlar.dto.ArtistResponse;
import uz.xitlar.dto.ArtistUpdateDto;
import uz.xitlar.dto.ResponseApi;
import uz.xitlar.service.ArtistService;

@Tag(name = "Artist Controller", description = "Artistlar bilan ishlash uchun API")
@RestController
@RequestMapping("/api/v1/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final ArtistService artistService;

    @Operation(summary = "Yangi artist qo'shish", description = "Tizimga yangi artist qo'shish (faqat ADMIN yoki MODERATOR uchun)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseApi<ArtistResponse> create(
            @Valid @RequestPart("data") ArtistCreateDto dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return artistService.createArtist(dto, file);
    }

    @Operation(summary = "Artistni yangilash", description = "Artist ma'lumotlarini yangilash (faqat ADMIN yoki MODERATOR uchun)")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseApi<ArtistResponse> update(
            @PathVariable Integer id,
            @Valid @RequestPart("data") ArtistUpdateDto dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return artistService.updateArtist(id, dto, file);
    }

    @Operation(summary = "Barcha artistlarni olish", description = "Paginatsiya yordamida barcha artistlarni ro'yxatini olish")
    @GetMapping
    public ResponseApi<Page<ArtistResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        return artistService.getAllArtists(pageable);
    }

    @Operation(summary = "Artistni ID orqali olish", description = "Artistning to'liq ma'lumotlarini ID orqali olish")
    @GetMapping("/{id}")
    public ResponseApi<ArtistResponse> getById(@PathVariable Integer id) {
        return artistService.getArtistById(id);
    }

    @Operation(summary = "Artistni o'chirish", description = "Artistni tizimdan o'chirish (faqat ADMIN yoki MODERATOR uchun)")
    @DeleteMapping("/{id}")
    public ResponseApi<Void> delete(@PathVariable Integer id) {
        return artistService.deleteArtist(id);
    }
}

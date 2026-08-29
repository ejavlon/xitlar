package uz.xitlar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.MusicCreateDto;
import uz.xitlar.dto.MusicResponse;
import uz.xitlar.dto.MusicUpdateDto;
import uz.xitlar.dto.ResponseApi;
import uz.xitlar.entity.Music;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.repository.MusicRepository;
import uz.xitlar.service.AudioProcessingService;
import uz.xitlar.service.MusicService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Tag(name = "Music Controller", description = "Musiqalar bilan ishlash uchun API")
@RestController
@RequestMapping("/api/v1/musics")
@RequiredArgsConstructor
public class MusicController {

    private final MusicService musicService;
    private final MusicRepository musicRepository;
    private final AudioProcessingService audioProcessingService;

    @Operation(summary = "Yangi musiqa qo'shish", description = "Tizimga yangi musiqa qo'shish (faqat ADMIN yoki MODERATOR uchun)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseApi<MusicResponse> create(
            @Valid @RequestPart("data") MusicCreateDto dto,
            @RequestPart("file") MultipartFile file) {
        return musicService.createMusic(dto, file);
    }

    @Operation(summary = "Musiqani yangilash", description = "Musiqa ma'lumotlarini yangilash (faqat ADMIN yoki MODERATOR uchun)")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseApi<MusicResponse> update(
            @PathVariable Integer id,
            @Valid @RequestPart("data") MusicUpdateDto dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return musicService.updateMusic(id, dto, file);
    }

    @Operation(summary = "Barcha musiqalarni olish", description = "Paginatsiya yordamida barcha musiqalar ro'yxatini olish")
    @GetMapping
    public ResponseApi<Page<MusicResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        return musicService.getAllMusics(pageable);
    }

    @Operation(summary = "Musiqani ID orqali olish", description = "Musiqaning to'liq ma'lumotlarini ID orqali olish")
    @GetMapping("/{id}")
    public ResponseApi<MusicResponse> getById(@PathVariable Integer id) {
        return musicService.getMusicById(id);
    }

    @Operation(summary = "Musiqani o'chirish", description = "Musiqani tizimdan o'chirish (faqat ADMIN yoki MODERATOR uchun)")
    @DeleteMapping("/{id}")
    public ResponseApi<Void> delete(@PathVariable Integer id) {
        return musicService.deleteMusic(id);
    }

    @Operation(summary = "Audio faylni o'qish/stream", description = "Musiqa faylini HTTP Range orqali uzatish")
    @GetMapping("/{id}/audio")
    public ResponseEntity<Resource> streamAudio(
            @PathVariable Integer id,
            @RequestHeader HttpHeaders headers) {

        Music music = musicRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Music not found with ID: " + id));

        Path path = audioProcessingService.getAudioPath(music.getStoredName());
        Resource resource;
        try {
            resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new DataNotFoundException("Audio file not found or not readable");
            }
        } catch (DataNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DataNotFoundException("Audio file not found");
        }

        long contentLength;
        try {
            contentLength = resource.contentLength();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        MediaType mediaType = MediaType.parseMediaType(
                music.getAudioContentType() != null ? music.getAudioContentType() : "audio/mpeg"
        );

        List<HttpRange> httpRanges;
        try {
            httpRanges = headers.getRange();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .build();
        }

        // 1. No Range requested -> 200 OK with the COMPLETE audio file
        if (httpRanges == null || httpRanges.isEmpty()) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(contentLength)
                    .contentType(mediaType)
                    .body(resource);
        }

        // 2. Range requested
        HttpRange range = httpRanges.get(0);
        long start;
        long end;
        try {
            start = range.getRangeStart(contentLength);
            end = range.getRangeEnd(contentLength);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .build();
        }

        // Validate range bounds
        if (start >= contentLength || end >= contentLength || start > end) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .build();
        }

        long rangeLength = end - start + 1;

        try (InputStream is = resource.getInputStream()) {
            long skipped = 0;
            while (skipped < start) {
                long s = is.skip(start - skipped);
                if (s <= 0) break;
                skipped += s;
            }
            byte[] buffer = is.readNBytes((int) rangeLength);
            ByteArrayResource partialResource = new ByteArrayResource(buffer);

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, contentLength))
                    .contentLength(rangeLength)
                    .contentType(mediaType)
                    .body(partialResource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

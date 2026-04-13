package com.stellarideas.grooves.controller;

import com.stellarideas.grooves.dto.*;
import com.stellarideas.grooves.model.*;
import com.stellarideas.grooves.repository.CoverArtRepository;
import com.stellarideas.grooves.repository.MusicFileRepository;
import com.stellarideas.grooves.repository.PlaylistRepository;
import com.stellarideas.grooves.security.CurrentUser;
import com.stellarideas.grooves.service.AuditService;
import com.stellarideas.grooves.service.MusicCatalogService;
import com.stellarideas.grooves.service.MusicScannerService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/library")
public class LibraryController {

    private static final Logger logger = LoggerFactory.getLogger(LibraryController.class);
    private static final int MAX_PAGE_SIZE = 200;

    private final MusicScannerService scannerService;
    private final MusicFileRepository musicFileRepository;
    private final PlaylistRepository playlistRepository;
    private final CoverArtRepository coverArtRepository;
    private final MessageSource messageSource;
    private final AuditService auditService;
    private final MusicCatalogService catalogService;

    public LibraryController(MusicScannerService scannerService,
                             MusicFileRepository musicFileRepository,
                             PlaylistRepository playlistRepository,
                             CoverArtRepository coverArtRepository,
                             MessageSource messageSource,
                             AuditService auditService,
                             MusicCatalogService catalogService) {
        this.scannerService = scannerService;
        this.musicFileRepository = musicFileRepository;
        this.playlistRepository = playlistRepository;
        this.coverArtRepository = coverArtRepository;
        this.messageSource = messageSource;
        this.auditService = auditService;
        this.catalogService = catalogService;
    }

    private String msg(String code, Object... args) {
        return messageSource.getMessage(code, args, Locale.getDefault());
    }

    @PostMapping("/scan")
    public ResponseEntity<?> scanDirectory(@CurrentUser User user, @Valid @RequestBody ScanRequest request) {
        String path = request.getPath();
        try {
            validateScanPath(path);
            auditService.log(user.getUsername(), AuditService.Action.SCAN_DIRECTORY, path);
            ScanResult result = scannerService.scanDirectory(user, path);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", msg("scan.success"));
            body.put("filesFound", result.getSaved());
            body.put("skipped", result.getSkipped());
            body.put("errors", result.getErrors());
            if (!result.getErrorDetails().isEmpty()) {
                body.put("errorDetails", result.getErrorDetails());
            }
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Scan failed for user '{}' on path '{}': {}", user.getUsername(), path, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", msg("scan.failed", e.getMessage())));
        }
    }

    @GetMapping("/files")
    public ResponseEntity<?> getFiles(
            @CurrentUser User user,
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        size = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Genre g = null;
        if (genre != null && !genre.isBlank()) {
            try {
                g = Genre.valueOf(genre.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(msg("genre.unknown.hint", genre));
            }
        }

        Page<MusicFile> result = (g == null)
                ? musicFileRepository.findByUserId(user.getId(), PageRequest.of(page, size))
                : musicFileRepository.findByUserIdAndGenre(user.getId(), g, PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "content", result.getContent().stream().map(MusicFileDTO::from).collect(Collectors.toList()),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        ));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchFiles(
            @CurrentUser User user,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Search query must not be empty"));
        }
        size = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // Escape regex special characters to prevent injection
        String escaped = q.replaceAll("([\\\\.*+?^${}()|\\[\\]])", "\\\\$1");
        Page<MusicFile> result = musicFileRepository.searchByUserIdAndQuery(
                user.getId(), escaped, PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "content", result.getContent().stream().map(MusicFileDTO::from).collect(Collectors.toList()),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "query", q
        ));
    }

    @GetMapping("/files/{id}/stream")
    public ResponseEntity<ResourceRegion> streamFile(
            @CurrentUser User user,
            @PathVariable String id,
            @RequestHeader HttpHeaders headers) throws IOException {
        Optional<MusicFile> fileOpt = musicFileRepository.findByIdAndUserId(id, user.getId());
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Path path = Paths.get(fileOpt.get().getFilePath());
        if (!Files.exists(path) || !Files.isReadable(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(path);
        long contentLength = resource.contentLength();
        MediaType mediaType = resolveAudioMediaType(fileOpt.get().getFileName());

        List<HttpRange> ranges = headers.getRange();
        ResourceRegion region;
        HttpStatus status;
        if (ranges.isEmpty()) {
            region = new ResourceRegion(resource, 0, contentLength);
            status = HttpStatus.OK;
        } else {
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            region = new ResourceRegion(resource, start, end - start + 1);
            status = HttpStatus.PARTIAL_CONTENT;
        }
        return ResponseEntity.status(status)
                .header("Accept-Ranges", "bytes")
                .contentType(mediaType)
                .body(region);
    }

    private MediaType resolveAudioMediaType(String fileName) {
        if (fileName == null) return MediaType.APPLICATION_OCTET_STREAM;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".mp3"))  return MediaType.parseMediaType("audio/mpeg");
        if (lower.endsWith(".flac")) return MediaType.parseMediaType("audio/flac");
        if (lower.endsWith(".m4a"))  return MediaType.parseMediaType("audio/mp4");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @PatchMapping("/files/{id}/genre")
    public ResponseEntity<?> updateFileGenre(@CurrentUser User user, @PathVariable String id,
                                             @Valid @RequestBody UpdateGenreRequest request) {
        Genre genre;
        try {
            genre = Genre.valueOf(request.getGenre().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", msg("genre.unknown", request.getGenre())));
        }
        Optional<MusicFile> fileOpt = musicFileRepository.findByIdAndUserId(id, user.getId());
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        MusicFile file = fileOpt.get();
        file.setGenre(genre);
        musicFileRepository.save(file);

        // Record the correction so future scans of this artist use the corrected genre
        if (file.getArtist() != null && !file.getArtist().isBlank()) {
            catalogService.recordCorrection(file.getArtist(), genre, user.getId());
        }

        auditService.log(user.getUsername(), AuditService.Action.GENRE_UPDATE, id, genre.name());
        return ResponseEntity.ok(Map.of("message", msg("genre.updated"), "genre", genre.name()));
    }

    // ── Rating ───────────────────────────────────────────

    @PatchMapping("/files/{id}/rating")
    public ResponseEntity<?> updateFileRating(@CurrentUser User user, @PathVariable String id,
                                              @Valid @RequestBody UpdateRatingRequest request) {
        Optional<MusicFile> fileOpt = musicFileRepository.findByIdAndUserId(id, user.getId());
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        MusicFile file = fileOpt.get();
        file.setRating(request.getRating());
        musicFileRepository.save(file);
        auditService.log(user.getUsername(), AuditService.Action.RATING_UPDATE, id, String.valueOf(request.getRating()));
        return ResponseEntity.ok(Map.of("rating", file.getRating()));
    }

    // ── Bulk delete ──────────────────────────────────────

    @Transactional
    @PostMapping("/files/bulk-delete")
    public ResponseEntity<?> bulkDelete(@CurrentUser User user, @Valid @RequestBody BulkDeleteRequest request) {
        List<String> ids = request.getFileIds();
        List<MusicFile> files = musicFileRepository.findByIdInAndUserId(ids, user.getId());
        if (files.isEmpty()) {
            return ResponseEntity.ok(Map.of("deleted", 0));
        }
        musicFileRepository.deleteAll(files);
        Set<String> deletedIds = files.stream().map(MusicFile::getId).collect(Collectors.toSet());
        // Remove from playlists
        List<Playlist> playlists = playlistRepository.findByUserId(user.getId());
        List<Playlist> modified = new ArrayList<>();
        for (Playlist p : playlists) {
            if (p.getTrackIds().removeAll(deletedIds)) {
                modified.add(p);
            }
        }
        if (!modified.isEmpty()) {
            playlistRepository.saveAll(modified);
        }
        auditService.log(user.getUsername(), AuditService.Action.BULK_DELETE, null, files.size() + " files");
        return ResponseEntity.ok(Map.of("deleted", files.size()));
    }

    // ── Cover art ────────────────────────────────────────

    @GetMapping("/files/{id}/cover")
    public ResponseEntity<byte[]> getCoverArt(@CurrentUser User user, @PathVariable String id) {
        Optional<MusicFile> fileOpt = musicFileRepository.findByIdAndUserId(id, user.getId());
        if (fileOpt.isEmpty() || !fileOpt.get().isHasCoverArt()) {
            return ResponseEntity.notFound().build();
        }
        MusicFile file = fileOpt.get();
        Optional<CoverArt> artOpt = coverArtRepository.findByUserIdAndArtistAndAlbum(
                user.getId(), file.getArtist(), file.getAlbum());
        if (artOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CoverArt art = artOpt.get();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(art.getMimeType()))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)))
                .body(art.getData());
    }

    // ── Duplicates ───────────────────────────────────────

    @GetMapping("/duplicates")
    public ResponseEntity<?> getDuplicates(@CurrentUser User user) {
        List<MusicFile> all = musicFileRepository.findByUserId(user.getId());
        // Group by lowercase title + artist
        Map<String, List<MusicFileDTO>> groups = new LinkedHashMap<>();
        for (MusicFile f : all) {
            if (f.getTitle() == null || f.getTitle().isBlank()
                    || f.getArtist() == null || f.getArtist().isBlank()) continue;
            String key = f.getTitle().toLowerCase() + "|" + f.getArtist().toLowerCase();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(MusicFileDTO.from(f));
        }
        // Keep only groups with 2+ entries
        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            if (entry.getValue().size() > 1) {
                MusicFileDTO first = entry.getValue().get(0);
                result.add(Map.of(
                        "title", first.getTitle(),
                        "artist", first.getArtist(),
                        "files", entry.getValue()
                ));
            }
        }
        return ResponseEntity.ok(result);
    }

    // ── Single delete + clear ────────────────────────────

    @Transactional
    @DeleteMapping("/files/{id}")
    public ResponseEntity<?> deleteFile(@CurrentUser User user, @PathVariable String id) {
        Optional<MusicFile> fileOpt = musicFileRepository.findByIdAndUserId(id, user.getId());
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        musicFileRepository.delete(fileOpt.get());
        removeFileFromPlaylists(id, user.getId());
        auditService.log(user.getUsername(), AuditService.Action.FILE_DELETE, id);
        return ResponseEntity.ok(Map.of("message", msg("library.file.deleted")));
    }

    @Transactional
    @DeleteMapping("/files")
    public ResponseEntity<?> clearLibrary(@CurrentUser User user) {
        long fileCount = musicFileRepository.deleteByUserId(user.getId());
        playlistRepository.deleteByUserId(user.getId());
        coverArtRepository.deleteByUserId(user.getId());
        auditService.log(user.getUsername(), AuditService.Action.LIBRARY_CLEAR, null, fileCount + " files removed");
        return ResponseEntity.ok(Map.of("message", msg("library.cleared"), "filesRemoved", fileCount));
    }

    private void removeFileFromPlaylists(String fileId, String userId) {
        List<Playlist> playlists = playlistRepository.findByUserId(userId);
        List<Playlist> modified = new ArrayList<>();
        for (Playlist p : playlists) {
            if (p.getTrackIds().remove(fileId)) {
                modified.add(p);
            }
        }
        if (!modified.isEmpty()) {
            playlistRepository.saveAll(modified);
        }
    }

    private void validateScanPath(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(msg("scan.path.empty"));
        }
        Path requested = Paths.get(path).normalize().toAbsolutePath();
        if (requested.toString().contains("..")) {
            throw new IllegalArgumentException(msg("scan.path.traversal"));
        }
        if (!Files.exists(requested) || !Files.isDirectory(requested)) {
            throw new IllegalArgumentException(msg("scan.path.notfound"));
        }
        Path canonical = requested.toRealPath();
        if (!canonical.equals(requested)) {
            throw new IllegalArgumentException(msg("scan.path.symlink"));
        }
    }
}

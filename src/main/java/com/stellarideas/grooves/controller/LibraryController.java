package com.stellarideas.grooves.controller;

import com.stellarideas.grooves.model.Genre;
import com.stellarideas.grooves.model.MusicFile;
import com.stellarideas.grooves.model.User;
import com.stellarideas.grooves.repository.MusicFileRepository;
import com.stellarideas.grooves.repository.UserRepository;
import com.stellarideas.grooves.service.MusicScannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResourceRegion;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private static final Logger logger = LoggerFactory.getLogger(LibraryController.class);

    @Autowired
    private MusicScannerService scannerService;

    @Autowired
    private MusicFileRepository musicFileRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/scan")
    public ResponseEntity<?> scanDirectory(@RequestBody Map<String, String> request) {
        String path = request.get("path");
        User user = getCurrentUser();
        try {
            validateScanPath(path);
            logger.info("User '{}' scanning directory: {}", user.getUsername(), path);
            int count = scannerService.scanDirectory(user, path);
            return ResponseEntity.ok(Map.of("message", "Scan completed successfully", "filesFound", count));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Scan failed for user '{}' on path '{}': {}", user.getUsername(), path, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Scan failed: " + e.getMessage()));
        }
    }

    @GetMapping("/files")
    public List<MusicFile> getFiles(@RequestParam(required = false) String genre) {
        User user = getCurrentUser();
        if (genre == null || genre.isBlank()) {
            return musicFileRepository.findByUser(user);
        }
        try {
            Genre g = Genre.valueOf(genre.toUpperCase());
            return musicFileRepository.findByUserAndGenre(user, g);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown genre: " + genre +
                    ". Valid values: CLASSIC_ROCK, HARD_ROCK, HAIR_METAL, HEAVY_METAL, THRASH_METAL, OTHER");
        }
    }

    @GetMapping("/files/{id}/stream")
    public ResponseEntity<ResourceRegion> streamFile(
            @PathVariable String id,
            @RequestHeader HttpHeaders headers) throws IOException {
        User user = getCurrentUser();
        Optional<MusicFile> fileOpt = musicFileRepository.findByIdAndUser(id, user);
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
    public ResponseEntity<?> updateFileGenre(@PathVariable String id, @RequestBody Map<String, String> request) {
        User user = getCurrentUser();
        String genreStr = request.get("genre");
        if (genreStr == null || genreStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Genre is required"));
        }
        Genre genre;
        try {
            genre = Genre.valueOf(genreStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown genre: " + genreStr));
        }
        Optional<MusicFile> fileOpt = musicFileRepository.findByIdAndUser(id, user);
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        MusicFile file = fileOpt.get();
        file.setGenre(genre);
        musicFileRepository.save(file);
        logger.info("User '{}' updated genre of file '{}' to {}", user.getUsername(), id, genre);
        return ResponseEntity.ok(Map.of("message", "Genre updated", "genre", genre.name()));
    }

    @DeleteMapping("/files")
    public ResponseEntity<?> clearLibrary() {
        User user = getCurrentUser();
        List<MusicFile> files = musicFileRepository.findByUser(user);
        musicFileRepository.deleteAll(files);
        logger.info("User '{}' cleared their library ({} files removed)", user.getUsername(), files.size());
        return ResponseEntity.ok(Map.of("message", "Library cleared", "filesRemoved", files.size()));
    }

    private void validateScanPath(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Directory path must not be empty");
        }
        if (path.contains("..")) {
            throw new IllegalArgumentException("Path traversal sequences are not allowed");
        }
        Path normalized = Paths.get(path).normalize();
        if (!normalized.isAbsolute()) {
            throw new IllegalArgumentException("Path must be an absolute directory path");
        }
        if (!Files.exists(normalized) || !Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Path does not exist or is not a directory");
        }
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = ((UserDetails) principal).getUsername();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));
    }
}

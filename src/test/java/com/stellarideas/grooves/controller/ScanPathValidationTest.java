package com.stellarideas.grooves.controller;

import com.stellarideas.grooves.dto.ScanRequest;
import com.stellarideas.grooves.dto.ScanResult;
import com.stellarideas.grooves.model.User;
import com.stellarideas.grooves.repository.CoverArtRepository;
import com.stellarideas.grooves.repository.MusicFileRepository;
import com.stellarideas.grooves.repository.PlaylistRepository;
import com.stellarideas.grooves.service.AuditService;
import com.stellarideas.grooves.service.MusicCatalogService;
import com.stellarideas.grooves.service.MusicScannerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for scan path validation: symlink attacks, path traversal,
 * nonexistent paths, and edge cases.
 */
class ScanPathValidationTest {

    private LibraryController controller;
    private User testUser;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MusicScannerService scannerService = mock(MusicScannerService.class);
        try {
            when(scannerService.scanDirectory(any(User.class), anyString())).thenReturn(new ScanResult());
        } catch (Exception ignored) {}
        MusicFileRepository musicFileRepository = mock(MusicFileRepository.class);
        PlaylistRepository playlistRepository = mock(PlaylistRepository.class);
        CoverArtRepository coverArtRepository = mock(CoverArtRepository.class);
        AuditService auditService = mock(AuditService.class);
        MusicCatalogService catalogService = mock(MusicCatalogService.class);

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");

        controller = new LibraryController(scannerService, musicFileRepository, playlistRepository,
                coverArtRepository, messageSource, auditService, catalogService);

        testUser = new User();
        testUser.setId("user1");
        testUser.setUsername("testuser");
    }

    private ScanRequest scanRequest(String path) {
        ScanRequest req = new ScanRequest();
        req.setPath(path);
        return req;
    }

    @Test
    void rejectsEmptyStringPath() {
        // Empty string after DTO validation would still reach validateScanPath
        ResponseEntity<?> response = controller.scanDirectory(testUser, scanRequest(""));
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void rejectsNonexistentPath() {
        ResponseEntity<?> response = controller.scanDirectory(testUser, scanRequest("/nonexistent/path/xyz"));
        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").toString().contains("not exist") || body.get("error").toString().contains("not a directory"));
    }

    @Test
    void rejectsPathTraversalWithDotDot() {
        // Even if normalized, the check catches "../" patterns
        String traversal = tempDir.toString() + "/../../../etc";
        ResponseEntity<?> response = controller.scanDirectory(testUser, scanRequest(traversal));
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void rejectsFileInsteadOfDirectory() throws IOException {
        Path file = tempDir.resolve("notadir.txt");
        Files.writeString(file, "hello");

        ResponseEntity<?> response = controller.scanDirectory(testUser, scanRequest(file.toString()));
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)  // Symlinks require special privileges on Windows
    void rejectsSymlinkDirectory() throws IOException {
        Path realDir = tempDir.resolve("real");
        Files.createDirectory(realDir);
        Path symlink = tempDir.resolve("link");
        Files.createSymbolicLink(symlink, realDir);

        ResponseEntity<?> response = controller.scanDirectory(testUser, scanRequest(symlink.toString()));
        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").toString().contains("symbolic link") || body.get("error").toString().contains("real path"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void rejectsNestedSymlinkInPath() throws IOException {
        // Create: tempDir/outer/inner -> /tmp (or some other dir)
        Path outer = tempDir.resolve("outer");
        Files.createDirectory(outer);
        Path innerSymlink = outer.resolve("sneaky");
        Files.createSymbolicLink(innerSymlink, Path.of("/tmp"));

        ResponseEntity<?> response = controller.scanDirectory(testUser, scanRequest(innerSymlink.toString()));
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void acceptsValidRealDirectory() throws Exception {
        // Use toRealPath() to resolve any OS-level symlinks (e.g., macOS /var -> /private/var)
        Path validDir = tempDir.toRealPath().resolve("music");
        Files.createDirectory(validDir);

        ResponseEntity<?> response = controller.scanDirectory(testUser, scanRequest(validDir.toString()));
        // With mocked scanner, this should succeed (200) since the path is valid
        assertEquals(200, response.getStatusCode().value());
    }
}

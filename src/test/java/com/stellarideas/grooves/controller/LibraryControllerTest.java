package com.stellarideas.grooves.controller;

import com.stellarideas.grooves.model.Genre;
import com.stellarideas.grooves.model.MusicFile;
import com.stellarideas.grooves.model.User;
import com.stellarideas.grooves.repository.CoverArtRepository;
import com.stellarideas.grooves.repository.MusicFileRepository;
import com.stellarideas.grooves.repository.PlaylistRepository;
import com.stellarideas.grooves.service.AuditService;
import com.stellarideas.grooves.service.MusicCatalogService;
import com.stellarideas.grooves.service.MusicScannerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LibraryControllerTest {

    private LibraryController controller;
    private MusicFileRepository musicFileRepository;
    private PlaylistRepository playlistRepository;
    private MusicScannerService scannerService;
    private User testUser;

    @BeforeEach
    void setUp() {
        scannerService = mock(MusicScannerService.class);
        musicFileRepository = mock(MusicFileRepository.class);
        playlistRepository = mock(PlaylistRepository.class);

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");

        CoverArtRepository coverArtRepository = mock(CoverArtRepository.class);
        AuditService auditService = mock(AuditService.class);
        MusicCatalogService catalogService = mock(MusicCatalogService.class);
        controller = new LibraryController(scannerService, musicFileRepository, playlistRepository, coverArtRepository, messageSource, auditService, catalogService);

        testUser = new User();
        testUser.setId("user1");
        testUser.setUsername("testuser");
    }

    @Test
    void getFilesEnforcesPagination() {
        MusicFile file = MusicFile.builder()
                .id("f1").title("Test Song").artist("Artist").genre(Genre.CLASSIC_ROCK).build();
        Page<MusicFile> page = new PageImpl<>(List.of(file));
        when(musicFileRepository.findByUserId(eq("user1"), any(Pageable.class))).thenReturn(page);

        ResponseEntity<?> response = controller.getFiles(testUser, null, 0, 50);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("content"));
        assertTrue(body.containsKey("totalPages"));
    }

    @Test
    void getFilesClampsSizeToMax() {
        Page<MusicFile> page = new PageImpl<>(List.of());
        when(musicFileRepository.findByUserId(eq("user1"), any(Pageable.class))).thenReturn(page);

        controller.getFiles(testUser, null, 0, 9999);

        verify(musicFileRepository).findByUserId(eq("user1"), argThat(pageable ->
                pageable.getPageSize() <= 200));
    }

    @Test
    void getFilesRejectsInvalidGenre() {
        assertThrows(IllegalArgumentException.class, () ->
                controller.getFiles(testUser, "NOT_A_GENRE", 0, 50));
    }

    @Test
    void getFilesFiltersByGenre() {
        Page<MusicFile> page = new PageImpl<>(List.of());
        when(musicFileRepository.findByUserIdAndGenre(eq("user1"), eq(Genre.HARD_ROCK), any(Pageable.class)))
                .thenReturn(page);

        ResponseEntity<?> response = controller.getFiles(testUser, "HARD_ROCK", 0, 50);

        assertEquals(200, response.getStatusCode().value());
        verify(musicFileRepository).findByUserIdAndGenre(eq("user1"), eq(Genre.HARD_ROCK), any(Pageable.class));
    }

    @Test
    void deleteFileRemovesFromRepository() {
        MusicFile file = MusicFile.builder().id("f1").title("Delete Me").build();
        when(musicFileRepository.findByIdAndUserId("f1", "user1")).thenReturn(Optional.of(file));
        when(playlistRepository.findByUserId("user1")).thenReturn(List.of());

        ResponseEntity<?> response = controller.deleteFile(testUser, "f1");

        assertEquals(200, response.getStatusCode().value());
        verify(musicFileRepository).delete(file);
    }

    @Test
    void deleteFileReturns404ForMissingFile() {
        when(musicFileRepository.findByIdAndUserId("missing", "user1")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.deleteFile(testUser, "missing");

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void streamFileReturns404ForOtherUsersFile() throws java.io.IOException {
        when(musicFileRepository.findByIdAndUserId("other-user-file", "user1")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.streamFile(testUser, "other-user-file",
                new org.springframework.http.HttpHeaders());

        assertEquals(404, response.getStatusCode().value());
    }
}

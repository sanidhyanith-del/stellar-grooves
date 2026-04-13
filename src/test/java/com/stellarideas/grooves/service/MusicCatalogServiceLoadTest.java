package com.stellarideas.grooves.service;

import com.stellarideas.grooves.model.Genre;
import com.stellarideas.grooves.repository.GenreCorrectionRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for MusicCatalogService catalog loading edge cases.
 */
class MusicCatalogServiceLoadTest {

    private MusicCatalogService createService() {
        GenreCorrectionRepository repo = mock(GenreCorrectionRepository.class);
        when(repo.findByArtistLower(anyString())).thenReturn(Optional.empty());
        return new MusicCatalogService(repo);
    }

    @Test
    void serviceReturnsEmptyGenresWhenCatalogFailsToLoad() throws Exception {
        MusicCatalogService service = createService();

        // Point to a nonexistent custom catalog path to trigger load failure
        Field pathField = MusicCatalogService.class.getDeclaredField("customCatalogPath");
        pathField.setAccessible(true);
        pathField.set(service, "/nonexistent/catalog.json");

        // loadCatalog should handle IOException gracefully
        var loadMethod = MusicCatalogService.class.getDeclaredMethod("loadCatalog");
        loadMethod.setAccessible(true);
        loadMethod.invoke(service);

        // After failed load, all lookups should return empty
        Set<Genre> genres = service.identifyGenres("Metallica");
        assertTrue(genres.isEmpty(), "Should return empty genres when catalog failed to load");
        assertFalse(service.isKnownArtist("Metallica"), "Should not know any artists when catalog failed to load");
    }

    @Test
    void serviceLoadsDefaultCatalogSuccessfully() throws Exception {
        MusicCatalogService service = createService();

        Field pathField = MusicCatalogService.class.getDeclaredField("customCatalogPath");
        pathField.setAccessible(true);
        pathField.set(service, "");

        var loadMethod = MusicCatalogService.class.getDeclaredMethod("loadCatalog");
        loadMethod.setAccessible(true);
        loadMethod.invoke(service);

        // Default catalog should load known artists
        assertTrue(service.isKnownArtist("Metallica"), "Should know Metallica from default catalog");
        assertFalse(service.identifyGenres("Metallica").isEmpty(), "Should have genres for Metallica");
    }
}

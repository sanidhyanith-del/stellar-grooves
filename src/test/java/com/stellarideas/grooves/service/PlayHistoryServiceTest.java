package com.stellarideas.grooves.service;

import com.stellarideas.grooves.model.MusicFile;
import com.stellarideas.grooves.model.PlayEvent;
import com.stellarideas.grooves.repository.MusicFileRepository;
import com.stellarideas.grooves.repository.PlayEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PlayHistoryServiceTest {

    private PlayHistoryService service;
    private PlayEventRepository playEventRepository;
    private MusicFileRepository musicFileRepository;
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        playEventRepository = mock(PlayEventRepository.class);
        musicFileRepository = mock(MusicFileRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        service = new PlayHistoryService(playEventRepository, musicFileRepository, mongoTemplate);
    }

    @Test
    void recordPlaySucceedsForOwnedFile() {
        MusicFile file = MusicFile.builder().id("f1").userId("user1").build();
        when(musicFileRepository.findByIdAndUserIdAndDeletedFalse("f1", "user1"))
                .thenReturn(Optional.of(file));

        boolean result = service.recordPlay("user1", "f1", 120_000, true);

        assertTrue(result);

        ArgumentCaptor<PlayEvent> evCaptor = ArgumentCaptor.forClass(PlayEvent.class);
        verify(playEventRepository).save(evCaptor.capture());
        PlayEvent saved = evCaptor.getValue();
        assertEquals("user1", saved.getUserId());
        assertEquals("f1", saved.getMusicFileId());
        assertEquals(120_000, saved.getListenedMs());
        assertTrue(saved.isCompleted());
        assertNotNull(saved.getPlayedAt());

        ArgumentCaptor<Query> qCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> uCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(qCaptor.capture(), uCaptor.capture(), eq(MusicFile.class));

        // Query should scope to both id and userId
        org.bson.Document qDoc = qCaptor.getValue().getQueryObject();
        assertEquals("f1", qDoc.get("_id"));
        assertEquals("user1", qDoc.get("userId"));

        // Update should $inc playCount and $set lastPlayedAt
        org.bson.Document uDoc = uCaptor.getValue().getUpdateObject();
        org.bson.Document inc = (org.bson.Document) uDoc.get("$inc");
        org.bson.Document set = (org.bson.Document) uDoc.get("$set");
        assertNotNull(inc);
        assertEquals(1, inc.get("playCount"));
        assertNotNull(set);
        assertTrue(set.get("lastPlayedAt") instanceof java.time.Instant);
    }

    @Test
    void recordPlayReturnsFalseForMissingFile() {
        when(musicFileRepository.findByIdAndUserIdAndDeletedFalse("missing", "user1"))
                .thenReturn(Optional.empty());

        boolean result = service.recordPlay("user1", "missing", 60_000, false);

        assertFalse(result);
        verify(playEventRepository, never()).save(any());
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(MusicFile.class));
    }

    @Test
    void recordPlayRejectsCrossUserAccess() {
        // File exists but belongs to another user — the ownership-scoped query returns empty
        when(musicFileRepository.findByIdAndUserIdAndDeletedFalse("f1", "intruder"))
                .thenReturn(Optional.empty());

        boolean result = service.recordPlay("intruder", "f1", 100_000, true);

        assertFalse(result);
        verify(playEventRepository, never()).save(any());
    }

    @Test
    void recordPlayClampsNegativeListenedMs() {
        MusicFile file = MusicFile.builder().id("f1").userId("user1").build();
        when(musicFileRepository.findByIdAndUserIdAndDeletedFalse("f1", "user1"))
                .thenReturn(Optional.of(file));

        service.recordPlay("user1", "f1", -500, false);

        ArgumentCaptor<PlayEvent> evCaptor = ArgumentCaptor.forClass(PlayEvent.class);
        verify(playEventRepository).save(evCaptor.capture());
        assertEquals(0, evCaptor.getValue().getListenedMs());
    }

    // ── Retrieval ─────────────────────────────────────────────

    private PlayHistoryService serviceWithFixedClock(java.time.Instant now) {
        return new PlayHistoryService(playEventRepository, musicFileRepository, mongoTemplate,
                java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
    }

    @Test
    void recentPlaysSortsByPlayedAtDescAndHydratesTrack() {
        java.time.Instant now = java.time.Instant.parse("2026-04-20T10:00:00Z");
        PlayHistoryService svc = serviceWithFixedClock(now);

        PlayEvent e1 = new PlayEvent("user1", "f1", now.minusSeconds(60), 120_000, true);
        PlayEvent e2 = new PlayEvent("user1", "f2", now.minusSeconds(30), 45_000, false);
        when(mongoTemplate.find(any(Query.class), eq(PlayEvent.class))).thenReturn(java.util.List.of(e2, e1));
        when(mongoTemplate.count(any(Query.class), eq(PlayEvent.class))).thenReturn(2L);

        MusicFile f1 = MusicFile.builder().id("f1").title("A").artist("X").build();
        MusicFile f2 = MusicFile.builder().id("f2").title("B").artist("Y").build();
        when(musicFileRepository.findByIdInAndUserId(any(), eq("user1"))).thenReturn(java.util.List.of(f1, f2));

        org.springframework.data.domain.Page<PlayHistoryService.RecentPlay> page =
                svc.getRecentPlays("user1", PlayHistoryService.Window.ALL, 0, 50);

        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        // Mongo returns the slice in sort order; service preserves it
        assertEquals("f2", page.getContent().get(0).track().getId());
        assertEquals("f1", page.getContent().get(1).track().getId());
    }

    @Test
    void recentPlaysDropsEventsForPurgedTracks() {
        PlayEvent ev = new PlayEvent("user1", "purged", java.time.Instant.now(), 0, false);
        when(mongoTemplate.find(any(Query.class), eq(PlayEvent.class))).thenReturn(java.util.List.of(ev));
        when(mongoTemplate.count(any(Query.class), eq(PlayEvent.class))).thenReturn(1L);
        when(musicFileRepository.findByIdInAndUserId(any(), eq("user1"))).thenReturn(java.util.List.of());

        org.springframework.data.domain.Page<PlayHistoryService.RecentPlay> page =
                service.getRecentPlays("user1", PlayHistoryService.Window.ALL, 0, 50);

        assertEquals(0, page.getContent().size(), "Purged-track events should be filtered out");
        assertEquals(1L, page.getTotalElements(), "Total still reflects raw event count");
    }

    @Test
    void recentPlaysWindowAppliesTimeThreshold() {
        java.time.Instant now = java.time.Instant.parse("2026-04-20T10:00:00Z");
        PlayHistoryService svc = serviceWithFixedClock(now);
        when(mongoTemplate.find(any(Query.class), eq(PlayEvent.class))).thenReturn(java.util.List.of());
        when(mongoTemplate.count(any(Query.class), eq(PlayEvent.class))).thenReturn(0L);

        svc.getRecentPlays("user1", PlayHistoryService.Window.WEEK, 0, 50);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(PlayEvent.class));
        org.bson.Document qDoc = captor.getValue().getQueryObject();
        assertEquals("user1", qDoc.get("userId"));
        org.bson.Document playedAt = (org.bson.Document) qDoc.get("playedAt");
        assertNotNull(playedAt, "WEEK window should apply a $gte threshold");
        assertTrue(playedAt.get("$gte") instanceof java.time.Instant);
    }

    @Test
    void topTracksReturnsHydratedTracksInAggregateOrder() {
        @SuppressWarnings("unchecked")
        org.springframework.data.mongodb.core.aggregation.AggregationResults<org.bson.Document> results =
                mock(org.springframework.data.mongodb.core.aggregation.AggregationResults.class);
        when(results.getMappedResults()).thenReturn(java.util.List.of(
                new org.bson.Document("_id", "f1").append("plays", 10),
                new org.bson.Document("_id", "f2").append("plays", 4)));
        when(mongoTemplate.aggregate(any(org.springframework.data.mongodb.core.aggregation.Aggregation.class),
                eq(PlayEvent.class), eq(org.bson.Document.class))).thenReturn(results);

        MusicFile f1 = MusicFile.builder().id("f1").title("A").artist("X").build();
        MusicFile f2 = MusicFile.builder().id("f2").title("B").artist("Y").build();
        // Mongo returns results in arbitrary order; the service preserves the aggregate ranking
        when(musicFileRepository.findByIdInAndUserId(any(), eq("user1"))).thenReturn(java.util.List.of(f2, f1));

        java.util.List<PlayHistoryService.TopTrack> out = service.getTopTracks("user1", PlayHistoryService.Window.ALL, 10);

        assertEquals(2, out.size());
        assertEquals("f1", out.get(0).track().getId());
        assertEquals(10, out.get(0).plays());
        assertEquals("f2", out.get(1).track().getId());
        assertEquals(4, out.get(1).plays());
    }

    @Test
    void topArtistsSumsCountsAcrossTracks() {
        @SuppressWarnings("unchecked")
        org.springframework.data.mongodb.core.aggregation.AggregationResults<org.bson.Document> results =
                mock(org.springframework.data.mongodb.core.aggregation.AggregationResults.class);
        when(results.getMappedResults()).thenReturn(java.util.List.of(
                new org.bson.Document("_id", "f1").append("plays", 5),   // Metallica
                new org.bson.Document("_id", "f2").append("plays", 3),   // Metallica
                new org.bson.Document("_id", "f3").append("plays", 4))); // AC/DC
        when(mongoTemplate.aggregate(any(org.springframework.data.mongodb.core.aggregation.Aggregation.class),
                eq(PlayEvent.class), eq(org.bson.Document.class))).thenReturn(results);

        when(musicFileRepository.findByIdInAndUserId(any(), eq("user1"))).thenReturn(java.util.List.of(
                MusicFile.builder().id("f1").artist("Metallica").build(),
                MusicFile.builder().id("f2").artist("Metallica").build(),
                MusicFile.builder().id("f3").artist("AC/DC").build()));

        java.util.List<PlayHistoryService.TopArtist> out =
                service.getTopArtists("user1", PlayHistoryService.Window.ALL, 10);

        assertEquals(2, out.size());
        assertEquals("Metallica", out.get(0).artist());
        assertEquals(8, out.get(0).plays());
        assertEquals("AC/DC", out.get(1).artist());
        assertEquals(4, out.get(1).plays());
    }

    @Test
    void topArtistsDropsTracksWithBlankArtist() {
        @SuppressWarnings("unchecked")
        org.springframework.data.mongodb.core.aggregation.AggregationResults<org.bson.Document> results =
                mock(org.springframework.data.mongodb.core.aggregation.AggregationResults.class);
        when(results.getMappedResults()).thenReturn(java.util.List.of(
                new org.bson.Document("_id", "f1").append("plays", 5),
                new org.bson.Document("_id", "f2").append("plays", 3)));
        when(mongoTemplate.aggregate(any(org.springframework.data.mongodb.core.aggregation.Aggregation.class),
                eq(PlayEvent.class), eq(org.bson.Document.class))).thenReturn(results);
        when(musicFileRepository.findByIdInAndUserId(any(), eq("user1"))).thenReturn(java.util.List.of(
                MusicFile.builder().id("f1").artist("Metallica").build(),
                MusicFile.builder().id("f2").artist("").build()));

        java.util.List<PlayHistoryService.TopArtist> out =
                service.getTopArtists("user1", PlayHistoryService.Window.ALL, 10);

        assertEquals(1, out.size());
        assertEquals("Metallica", out.get(0).artist());
    }

    @Test
    void windowParseFallsBackToAllOnUnknown() {
        assertEquals(PlayHistoryService.Window.ALL, PlayHistoryService.Window.parse(null));
        assertEquals(PlayHistoryService.Window.ALL, PlayHistoryService.Window.parse(""));
        assertEquals(PlayHistoryService.Window.ALL, PlayHistoryService.Window.parse("decade"));
        assertEquals(PlayHistoryService.Window.WEEK, PlayHistoryService.Window.parse("week"));
        assertEquals(PlayHistoryService.Window.MONTH, PlayHistoryService.Window.parse("MONTH"));
    }
}

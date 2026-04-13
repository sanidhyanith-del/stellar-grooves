package com.stellarideas.grooves.controller;

import com.stellarideas.grooves.dto.AddTrackRequest;
import com.stellarideas.grooves.dto.PlaylistDTO;
import com.stellarideas.grooves.model.Playlist;
import com.stellarideas.grooves.model.Role;
import com.stellarideas.grooves.model.User;
import com.stellarideas.grooves.repository.MusicFileRepository;
import com.stellarideas.grooves.repository.PlaylistRepository;
import com.stellarideas.grooves.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that verify user A cannot access user B's playlists or music files.
 */
class PlaylistSecurityTest {

    private PlaylistController controller;
    private PlaylistRepository playlistRepository;
    private MusicFileRepository musicFileRepository;
    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        playlistRepository = mock(PlaylistRepository.class);
        musicFileRepository = mock(MusicFileRepository.class);

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");

        AuditService auditService = mock(AuditService.class);
        controller = new PlaylistController(playlistRepository, musicFileRepository, messageSource, auditService);

        userA = User.builder().username("alice").email("alice@test.com").password("enc").build();
        userA.setId("userA");
        userA.setRoles(Set.of(Role.ROLE_USER));

        userB = User.builder().username("bob").email("bob@test.com").password("enc").build();
        userB.setId("userB");
        userB.setRoles(Set.of(Role.ROLE_USER));
    }

    @Test
    void userCannotAccessOtherUsersPlaylist() {
        when(playlistRepository.findByIdAndUserId("pl-bob", "userA")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getPlaylistTracks(userA, "pl-bob");
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void userCannotDeleteOtherUsersPlaylist() {
        when(playlistRepository.findByIdAndUserId("pl-bob", "userA")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.deletePlaylist(userA, "pl-bob");
        assertEquals(404, response.getStatusCode().value());
        verify(playlistRepository, never()).delete(any());
    }

    private AddTrackRequest addTrackRequest(String fileId) {
        AddTrackRequest req = new AddTrackRequest();
        req.setFileId(fileId);
        return req;
    }

    @Test
    void userCannotAddTrackToOtherUsersPlaylist() {
        when(playlistRepository.findByIdAndUserId("pl-bob", "userA")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.addTrack(userA, "pl-bob", addTrackRequest("f1"));
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void userCannotAddOtherUsersFileToOwnPlaylist() {
        Playlist alicesPlaylist = new Playlist();
        alicesPlaylist.setId("pl-alice");
        alicesPlaylist.setName("Alice's Playlist");
        alicesPlaylist.setUserId("userA");
        alicesPlaylist.setTrackIds(new ArrayList<>());

        when(playlistRepository.findByIdAndUserId("pl-alice", "userA")).thenReturn(Optional.of(alicesPlaylist));
        when(musicFileRepository.findByIdAndUserId("f-bob", "userA")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.addTrack(userA, "pl-alice", addTrackRequest("f-bob"));
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void userOnlySeesOwnPlaylists() {
        Playlist alicePlaylist = new Playlist();
        alicePlaylist.setId("pl-alice");
        alicePlaylist.setName("Alice's Playlist");
        alicePlaylist.setUserId("userA");
        alicePlaylist.setTrackIds(new ArrayList<>(List.of("f1")));

        when(playlistRepository.findByUserId("userA")).thenReturn(List.of(alicePlaylist));

        List<PlaylistDTO> result = controller.getPlaylists(userA);
        assertEquals(1, result.size());
        assertEquals("pl-alice", result.get(0).getId());
    }
}

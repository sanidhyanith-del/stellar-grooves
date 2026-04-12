package com.stellarideas.grooves.controller;

import com.stellarideas.grooves.model.MusicFile;
import com.stellarideas.grooves.model.Playlist;
import com.stellarideas.grooves.model.User;
import com.stellarideas.grooves.repository.MusicFileRepository;
import com.stellarideas.grooves.repository.PlaylistRepository;
import com.stellarideas.grooves.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final UserRepository userRepository;
    private final MusicFileRepository musicFileRepository;
    private final PlaylistRepository playlistRepository;

    public AdminController(UserRepository userRepository, MusicFileRepository musicFileRepository,
                           PlaylistRepository playlistRepository) {
        this.userRepository = userRepository;
        this.musicFileRepository = musicFileRepository;
        this.playlistRepository = playlistRepository;
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "25") int size) {
        if (page == null) {
            return ResponseEntity.ok(userRepository.findAll());
        }
        Page<User> result = userRepository.findAll(PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        ));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        return userRepository.findById(id)
                .map(user -> {
                    List<MusicFile> files = musicFileRepository.findByUser(user);
                    List<Playlist> playlists = playlistRepository.findByUser(user);
                    int fileCount = files.size();
                    int playlistCount = playlists.size();
                    playlistRepository.deleteAll(playlists);
                    musicFileRepository.deleteAll(files);
                    userRepository.deleteById(id);
                    logger.info("Admin deleted user '{}' (id={}) and {} music files, {} playlists",
                            user.getUsername(), id, fileCount, playlistCount);
                    return ResponseEntity.ok(Map.of(
                            "message", "User deleted successfully",
                            "filesRemoved", fileCount,
                            "playlistsRemoved", playlistCount
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

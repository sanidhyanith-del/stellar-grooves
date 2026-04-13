package com.stellarideas.grooves.integration;

import com.stellarideas.grooves.model.User;
import com.stellarideas.grooves.repository.MusicFileRepository;
import com.stellarideas.grooves.repository.UserRepository;
import com.stellarideas.grooves.service.ScheduledScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for ScheduledScanService against a real MongoDB via Testcontainers.
 * Validates that scheduled scans update the user record and persist scan timestamps.
 */
class ScheduledScanServiceIT extends BaseIntegrationTest {

    @Autowired
    private ScheduledScanService scheduledScanService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MusicFileRepository musicFileRepository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
        musicFileRepository.deleteAll();
    }

    @Test
    void scheduledScanUpdatesLastScanTimestamp() {
        User user = new User();
        user.setUsername("scanuser");
        user.setEmail("scan@test.com");
        user.setPassword("encoded");
        user.setScanSchedule("* * * * * *"); // every second — always due
        user.setScanPath(tempDir.toString());
        user.setLastScheduledScan(Instant.now().minus(1, ChronoUnit.HOURS));
        user.setRoles(Set.of(com.stellarideas.grooves.model.Role.ROLE_USER));
        userRepository.save(user);

        scheduledScanService.checkScheduledScans();

        User updated = userRepository.findByUsername("scanuser").orElseThrow();
        assertNotNull(updated.getLastScheduledScan(), "lastScheduledScan should be set after scan");
        assertTrue(updated.getLastScheduledScan().isAfter(user.getLastScheduledScan()),
                "lastScheduledScan should be updated to a newer timestamp");
    }

    @Test
    void scheduledScanSkipsUserWithNoSchedule() {
        User user = new User();
        user.setUsername("noschedule");
        user.setEmail("no@test.com");
        user.setPassword("encoded");
        user.setScanPath(tempDir.toString());
        user.setRoles(Set.of(com.stellarideas.grooves.model.Role.ROLE_USER));
        // No scanSchedule set
        userRepository.save(user);

        scheduledScanService.checkScheduledScans();

        User updated = userRepository.findByUsername("noschedule").orElseThrow();
        assertNull(updated.getLastScheduledScan(), "lastScheduledScan should remain null");
    }

    @Test
    void scheduledScanDoesNotRunWhenNotDue() {
        User user = new User();
        user.setUsername("notdue");
        user.setEmail("notdue@test.com");
        user.setPassword("encoded");
        user.setScanSchedule("0 0 0 1 1 *"); // once a year, Jan 1 midnight
        user.setScanPath(tempDir.toString());
        user.setLastScheduledScan(Instant.now()); // just scanned
        user.setRoles(Set.of(com.stellarideas.grooves.model.Role.ROLE_USER));
        userRepository.save(user);

        Instant before = user.getLastScheduledScan();

        scheduledScanService.checkScheduledScans();

        User updated = userRepository.findByUsername("notdue").orElseThrow();
        assertEquals(before, updated.getLastScheduledScan(),
                "lastScheduledScan should not change when scan is not due");
    }

    @Test
    void scheduledScanProcessesFilesFromDisk() throws Exception {
        // Create a dummy mp3 — will fail to parse but exercises the full path
        Files.write(tempDir.resolve("test.mp3"), new byte[]{0, 1, 2, 3});

        User user = new User();
        user.setUsername("fileuser");
        user.setEmail("file@test.com");
        user.setPassword("encoded");
        user.setScanSchedule("* * * * * *");
        user.setScanPath(tempDir.toString());
        user.setLastScheduledScan(Instant.now().minus(1, ChronoUnit.HOURS));
        user.setRoles(Set.of(com.stellarideas.grooves.model.Role.ROLE_USER));
        userRepository.save(user);

        scheduledScanService.checkScheduledScans();

        User updated = userRepository.findByUsername("fileuser").orElseThrow();
        assertNotNull(updated.getLastScheduledScan());
        // The file is malformed so it won't be saved, but the scan should complete
        assertEquals(0, musicFileRepository.countByUserId(updated.getId()));
    }
}

package com.stellarideas.grooves.integration;

import com.stellarideas.grooves.model.Role;
import com.stellarideas.grooves.model.User;
import com.stellarideas.grooves.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for concurrent signup race conditions against real MongoDB.
 * Validates that MongoDB's unique index on username prevents duplicate users
 * even under concurrent writes.
 */
class AuthConcurrencyIT extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void concurrentUserCreationWithSameUsernameOnlyOneSucceeds() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    User user = new User();
                    user.setUsername("raceuser");
                    user.setEmail("race" + idx + "@test.com"); // unique emails
                    user.setPassword(passwordEncoder.encode("Password1"));
                    user.setRoles(Set.of(Role.ROLE_USER));
                    userRepository.save(user);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    // Expected: DuplicateKeyException for all but first
                    failures.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();

        assertEquals(1, successes.get(), "Exactly one user creation should succeed");
        assertEquals(threadCount - 1, failures.get(), "All others should fail with duplicate key");

        // Verify only one user exists in the database
        List<User> users = userRepository.findAll();
        long raceUsers = users.stream().filter(u -> "raceuser".equals(u.getUsername())).count();
        assertEquals(1, raceUsers, "Only one 'raceuser' should exist in the database");
    }

    @Test
    void concurrentUserCreationWithSameEmailOnlyOneSucceeds() throws Exception {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    User user = new User();
                    user.setUsername("emailrace" + idx); // unique usernames
                    user.setEmail("sameemail@test.com");
                    user.setPassword(passwordEncoder.encode("Password1"));
                    user.setRoles(Set.of(Role.ROLE_USER));
                    userRepository.save(user);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();

        assertEquals(1, successes.get(), "Exactly one user with the email should be created");
        assertEquals(threadCount - 1, failures.get());

        long emailCount = userRepository.findAll().stream()
                .filter(u -> "sameemail@test.com".equals(u.getEmail())).count();
        assertEquals(1, emailCount, "Only one user with that email should exist");
    }

    @Test
    void concurrentCreationOfDifferentUsersAllSucceed() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<User>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                startLatch.await();
                User user = new User();
                user.setUsername("user" + idx);
                user.setEmail("user" + idx + "@test.com");
                user.setPassword(passwordEncoder.encode("Password1"));
                user.setRoles(Set.of(Role.ROLE_USER));
                return userRepository.save(user);
            }));
        }

        startLatch.countDown();

        int saved = 0;
        for (Future<User> f : futures) {
            f.get(30, TimeUnit.SECONDS);
            saved++;
        }
        executor.shutdown();

        assertEquals(threadCount, saved, "All unique users should be created");
        assertEquals(threadCount, userRepository.count());
    }
}

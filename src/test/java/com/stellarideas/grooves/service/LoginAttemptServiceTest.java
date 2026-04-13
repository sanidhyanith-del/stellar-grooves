package com.stellarideas.grooves.service;

import com.stellarideas.grooves.model.User;
import com.stellarideas.grooves.repository.RefreshTokenRepository;
import com.stellarideas.grooves.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginAttemptServiceTest {

    private LoginAttemptService service;
    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        service = new LoginAttemptService(userRepository, refreshTokenRepository);
        ReflectionTestUtils.setField(service, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(service, "lockoutDurationMinutes", 15L);
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        return user;
    }

    @Test
    void loginFailedIncrementsCounter() {
        User user = createUser("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        service.loginFailed("testuser");

        assertEquals(1, user.getFailedLoginAttempts());
        assertFalse(user.isAccountLocked());
        verify(userRepository).save(user);
    }

    @Test
    void loginFailedLocksAccountAfterMaxAttempts() {
        User user = createUser("testuser");
        user.setId("user123");
        user.setFailedLoginAttempts(4); // One more will reach 5
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        service.loginFailed("testuser");

        assertEquals(5, user.getFailedLoginAttempts());
        assertTrue(user.isAccountLocked());
        assertNotNull(user.getLockoutExpiry());
        verify(refreshTokenRepository).deleteByUserId("user123");
        verify(userRepository).save(user);
    }

    @Test
    void loginFailedIgnoresUnknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        service.loginFailed("ghost");

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginSucceededResetsCounter() {
        User user = createUser("testuser");
        user.setFailedLoginAttempts(3);
        user.setAccountLocked(true);
        user.setLockoutExpiry(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        service.loginSucceeded("testuser");

        assertEquals(0, user.getFailedLoginAttempts());
        assertFalse(user.isAccountLocked());
        assertNull(user.getLockoutExpiry());
        verify(userRepository).save(user);
    }

    @Test
    void loginSucceededSkipsSaveWhenNoFailures() {
        User user = createUser("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        service.loginSucceeded("testuser");

        verify(userRepository, never()).save(any());
    }

    @Test
    void isLockedOutReturnsTrueWhenLocked() {
        User user = createUser("testuser");
        user.setAccountLocked(true);
        user.setLockoutExpiry(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        assertTrue(service.isLockedOut("testuser"));
    }

    @Test
    void isLockedOutReturnsFalseWhenNotLocked() {
        User user = createUser("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        assertFalse(service.isLockedOut("testuser"));
    }

    @Test
    void isLockedOutAutoUnlocksAfterExpiry() {
        User user = createUser("testuser");
        user.setAccountLocked(true);
        user.setFailedLoginAttempts(5);
        user.setLockoutExpiry(Instant.now().minus(1, ChronoUnit.MINUTES)); // expired
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        assertFalse(service.isLockedOut("testuser"));
        assertFalse(user.isAccountLocked());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockoutExpiry());
        verify(userRepository).save(user);
    }

    @Test
    void isLockedOutReturnsFalseForUnknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertFalse(service.isLockedOut("ghost"));
    }
}

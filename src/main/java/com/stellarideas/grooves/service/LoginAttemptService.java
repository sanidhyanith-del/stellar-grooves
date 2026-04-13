package com.stellarideas.grooves.service;

import com.stellarideas.grooves.model.User;
import com.stellarideas.grooves.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Tracks failed login attempts and manages account lockout.
 * After {@code maxFailedAttempts} consecutive failures, the account is locked
 * for {@code lockoutDurationMinutes}. The lockout expires automatically.
 */
@Service
public class LoginAttemptService {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptService.class);

    @Value("${stellar.grooves.login.maxFailedAttempts:5}")
    private int maxFailedAttempts;

    @Value("${stellar.grooves.login.lockoutDurationMinutes:15}")
    private long lockoutDurationMinutes;

    private final UserRepository userRepository;

    public LoginAttemptService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Record a failed login attempt. Locks the account if the threshold is reached.
     */
    public void loginFailed(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return; // Don't reveal whether the user exists
        }
        User user = userOpt.get();
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= maxFailedAttempts) {
            Instant expiry = Instant.now().plus(lockoutDurationMinutes, ChronoUnit.MINUTES);
            user.setAccountLocked(true);
            user.setLockoutExpiry(expiry);
            logger.warn("Account '{}' locked after {} failed attempts. Lockout expires at {}",
                    username, attempts, expiry);
        }

        userRepository.save(user);
    }

    /**
     * Record a successful login. Resets the failed attempt counter and clears any lockout.
     */
    public void loginSucceeded(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();
        if (user.getFailedLoginAttempts() > 0 || user.isAccountLocked()) {
            user.setFailedLoginAttempts(0);
            user.setAccountLocked(false);
            user.setLockoutExpiry(null);
            userRepository.save(user);
        }
    }

    /**
     * Check whether a user is currently locked out.
     * Automatically unlocks the account if the lockout period has expired.
     *
     * @return true if the account is locked and the lockout has not expired
     */
    public boolean isLockedOut(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return false;
        }
        User user = userOpt.get();
        if (!user.isAccountLocked()) {
            return false;
        }

        // Auto-unlock if lockout has expired
        if (user.getLockoutExpiry() != null && Instant.now().isAfter(user.getLockoutExpiry())) {
            user.setAccountLocked(false);
            user.setFailedLoginAttempts(0);
            user.setLockoutExpiry(null);
            userRepository.save(user);
            logger.info("Account '{}' auto-unlocked after lockout expiry", username);
            return false;
        }

        return true;
    }
}

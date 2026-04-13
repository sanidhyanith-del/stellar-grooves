package com.stellarideas.grooves.controller;

import com.stellarideas.grooves.dto.LoginRequest;
import com.stellarideas.grooves.dto.SignupRequest;
import com.stellarideas.grooves.model.Role;
import com.stellarideas.grooves.model.User;
import com.stellarideas.grooves.repository.UserRepository;
import com.stellarideas.grooves.security.JwtUtils;
import com.stellarideas.grooves.service.AuditService;
import com.stellarideas.grooves.service.LoginAttemptService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;
    private final MessageSource messageSource;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;

    public AuthController(AuthenticationManager authenticationManager, UserRepository userRepository,
                          PasswordEncoder encoder, JwtUtils jwtUtils, MessageSource messageSource,
                          LoginAttemptService loginAttemptService, AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.jwtUtils = jwtUtils;
        this.messageSource = messageSource;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
    }

    private String msg(String code, Object... args) {
        return messageSource.getMessage(code, args, Locale.getDefault());
    }

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        String username = loginRequest.getUsername();

        if (loginAttemptService.isLockedOut(username)) {
            logger.warn("Login attempt for locked account '{}'", username);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", msg("auth.locked")));
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication);

            loginAttemptService.loginSucceeded(username);
            auditService.log(username, AuditService.Action.LOGIN_SUCCESS);
            return ResponseEntity.ok(Map.of("token", jwt, "username", username));
        } catch (BadCredentialsException e) {
            loginAttemptService.loginFailed(username);
            auditService.log(username, AuditService.Action.LOGIN_FAILED);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", msg("auth.bad_credentials")));
        } catch (LockedException e) {
            auditService.log(username, AuditService.Action.LOGIN_LOCKED);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", msg("auth.locked")));
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())
                || userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", msg("auth.duplicate")));
        }

        User user = User.builder()
                .username(signUpRequest.getUsername())
                .email(signUpRequest.getEmail())
                .password(encoder.encode(signUpRequest.getPassword()))
                .build();

        Set<Role> roles = new HashSet<>();
        roles.add(Role.ROLE_USER);
        user.setRoles(roles);
        userRepository.save(user);

        auditService.log(signUpRequest.getUsername(), AuditService.Action.SIGNUP);
        return ResponseEntity.ok(Map.of("message", msg("auth.registered")));
    }
}

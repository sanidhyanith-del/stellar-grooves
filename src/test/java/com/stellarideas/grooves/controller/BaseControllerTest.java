package com.stellarideas.grooves.controller;

import com.stellarideas.grooves.model.User;
import com.stellarideas.grooves.repository.UserRepository;
import com.stellarideas.grooves.security.CurrentUserResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaseControllerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CurrentUserResolver resolver = new CurrentUserResolver(userRepository);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolverThrowsWhenNoAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(null);

        assertThrows(IllegalStateException.class,
                () -> resolver.resolveArgument(null, null, null, null));
    }

    @Test
    void resolverThrowsWhenPrincipalIsNotUserDetails() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("plain-string", null));

        assertThrows(IllegalStateException.class,
                () -> resolver.resolveArgument(null, null, null, null));
    }

    @Test
    void resolverReturnsUserWhenAuthenticated() {
        User expected = new User();
        expected.setUsername("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(expected));

        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername("testuser")
                .password("irrelevant")
                .authorities(List.of())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        User result = (User) resolver.resolveArgument(null, null, null, null);
        assertEquals("testuser", result.getUsername());
    }

    @Test
    void resolverThrowsWhenUserNotInDatabase() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername("ghost")
                .password("irrelevant")
                .authorities(List.of())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        assertThrows(IllegalStateException.class,
                () -> resolver.resolveArgument(null, null, null, null));
    }
}

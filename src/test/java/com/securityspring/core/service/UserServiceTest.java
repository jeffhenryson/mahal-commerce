package com.securityspring.core.service;

import com.securityspring.core.domain.exception.InvalidPasswordException;
import com.securityspring.core.domain.exception.RoleNotFoundException;
import com.securityspring.core.domain.exception.UserNotFoundException;
import com.securityspring.core.domain.exception.UsernameAlreadyExistsException;
import com.securityspring.core.domain.model.Role;
import com.securityspring.core.domain.model.User;
import com.securityspring.core.ports.out.PasswordHashPort;
import com.securityspring.core.ports.out.RefreshTokenPort;
import com.securityspring.core.ports.out.RoleRepository;
import com.securityspring.core.ports.out.TokenBlocklistPort;
import com.securityspring.core.ports.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordHashPort passwordHash;
    @Mock RefreshTokenPort refreshTokenPort;
    @Mock TokenBlocklistPort tokenBlocklistPort;

    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, roleRepository, passwordHash,
                refreshTokenPort, tokenBlocklistPort);
    }

    @Test
    void createUser_rejectsPasswordShorterThan8Chars() {
        assertThatThrownBy(() -> userService.createUser("alice", "short", List.of()))
                .isInstanceOf(InvalidPasswordException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void createUser_rejectsDuplicateUsername() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(User.of("alice", "hashed", null)));

        assertThatThrownBy(() -> userService.createUser("alice", "password123", List.of()))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void createUser_rejectsUnknownRole() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.createUser("alice", "password123", List.of("ROLE_UNKNOWN")))
                .isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void createUser_savesUserWithRole() {
        Role role = new Role();
        role.setName("ROLE_USER");
        when(passwordHash.hash(anyString())).thenReturn("hashed");
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.createUser("alice", "password123", List.of("ROLE_USER"));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void changeOwnPassword_rejectsWrongCurrentPassword() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHash.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> userService.changeOwnPassword("alice", "wrong", "newPassword1"))
                .isInstanceOf(InvalidPasswordException.class);
        verifyNoInteractions(refreshTokenPort, tokenBlocklistPort);
    }

    @Test
    void changeOwnPassword_invalidatesAllSessions() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHash.matches("current", "hashed")).thenReturn(true);
        when(passwordHash.hash("newPassword1")).thenReturn("newHashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.changeOwnPassword("alice", "current", "newPassword1");

        verify(refreshTokenPort).revokeAll("alice");
        verify(tokenBlocklistPort).blockAllBefore(eq("alice"), any());
    }

    @Test
    void getUserById_throwsWhenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(UserNotFoundException.class);
    }
}

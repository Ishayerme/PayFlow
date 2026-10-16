package com.payflow.account.user;

import com.payflow.account.user.AuthDtos.LoginRequest;
import com.payflow.account.user.AuthDtos.RegisterRequest;
import com.payflow.common.error.ApiException;
import com.payflow.common.security.AuthUser;
import com.payflow.common.security.JwtService;
import com.payflow.common.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @InjectMocks AuthService authService;

    private static User storedUser(Long id, String hash) {
        User user = new User("isha@example.com", hash, "Isha Yerme", Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void registerStoresHashedPasswordAndCustomerRole() {
        when(users.existsByEmailIgnoreCase("Isha@Example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$hashed");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = authService.register(new RegisterRequest("Isha@Example.com", "secret123", "Isha Yerme"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$hashed");
        assertThat(saved.getValue().getEmail()).isEqualTo("isha@example.com");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmailIgnoreCase("isha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("isha@example.com", "secret123", "Isha")))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        verify(users, never()).save(any());
    }

    @Test
    void loginWithCorrectPasswordReturnsToken() {
        when(users.findByEmailIgnoreCase("isha@example.com")).thenReturn(Optional.of(storedUser(5L, "$2a$hash")));
        when(passwordEncoder.matches("secret123", "$2a$hash")).thenReturn(true);
        when(jwtService.generateToken(new AuthUser(5L, "isha@example.com", Role.CUSTOMER))).thenReturn("jwt-token");

        var response = authService.login(new LoginRequest("isha@example.com", "secret123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().id()).isEqualTo(5L);
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() {
        when(users.findByEmailIgnoreCase("isha@example.com")).thenReturn(Optional.of(storedUser(5L, "$2a$hash")));
        when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("isha@example.com", "wrong")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void loginWithUnknownEmailGivesSameMessageAsWrongPassword() {
        when(users.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "x")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid email or password")
                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

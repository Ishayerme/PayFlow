package com.payflow.account.user;

import com.payflow.account.user.AuthDtos.LoginRequest;
import com.payflow.account.user.AuthDtos.LoginResponse;
import com.payflow.account.user.AuthDtos.RegisterRequest;
import com.payflow.account.user.AuthDtos.UserResponse;
import com.payflow.common.error.ApiException;
import com.payflow.common.security.AuthUser;
import com.payflow.common.security.JwtService;
import com.payflow.common.security.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Public sign-up always creates a CUSTOMER; admins can't be created through the API. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("Email already registered");
        }
        User user = new User(
                request.email().toLowerCase(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                Role.CUSTOMER);
        return UserResponse.from(users.save(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                // Same message for "no such email" and "wrong password", so an attacker
                // can't use login to discover which emails are registered.
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        String token = jwtService.generateToken(new AuthUser(user.getId(), user.getEmail(), user.getRole()));
        return new LoginResponse(token, UserResponse.from(user));
    }
}

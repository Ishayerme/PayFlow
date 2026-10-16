package com.payflow.account.config;

import com.payflow.account.user.User;
import com.payflow.account.user.UserRepository;
import com.payflow.common.security.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the single admin user from configuration on first start-up. */
@Component
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository users, PasswordEncoder passwordEncoder,
                       @Value("${payflow.admin.email}") String email,
                       @Value("${payflow.admin.password}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!users.existsByEmailIgnoreCase(email)) {
            users.save(new User(email.toLowerCase(), passwordEncoder.encode(password), "Administrator", Role.ADMIN));
        }
    }
}

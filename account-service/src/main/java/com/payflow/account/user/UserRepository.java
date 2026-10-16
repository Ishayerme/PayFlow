package com.payflow.account.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Spring Data writes the SQL for these from the method names, always with
// bound parameters (WHERE email = ?), so user input can't inject SQL.
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}

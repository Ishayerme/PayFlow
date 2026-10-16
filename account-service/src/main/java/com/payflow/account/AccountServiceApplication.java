package com.payflow.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Scan com.payflow so the shared JwtService and error handler in `common` are picked up.
@SpringBootApplication(scanBasePackages = "com.payflow")
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}

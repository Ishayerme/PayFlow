package com.payflow.account;

import com.payflow.account.config.SecurityConfig;
import com.payflow.common.error.GlobalExceptionHandler;
import com.payflow.common.security.AuthUser;
import com.payflow.common.security.JwtService;
import com.payflow.common.security.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared setup for controller tests: the real security chain, JWT service and
 * error handler are loaded, so these tests exercise authentication for real.
 */
@Import({SecurityConfig.class, JwtService.class, GlobalExceptionHandler.class})
public abstract class WebLayerTestBase {

    protected static final AuthUser CUSTOMER = new AuthUser(1L, "isha@example.com", Role.CUSTOMER);
    protected static final AuthUser ADMIN = new AuthUser(9L, "admin@example.com", Role.ADMIN);

    @Autowired protected MockMvc mvc;
    @Autowired protected JwtService jwtService;

    protected String bearer(AuthUser user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}

package com.payflow.account;

import com.payflow.common.security.AuthUser;
import com.payflow.common.security.JwtService;
import com.payflow.common.security.Role;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the service on a real HTTP port. Regression test for a bug the mocked web
 * layer can't see: Spring forwards a 403 to /error, and if /error requires login
 * the client receives 401 instead of 403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureEmbeddedDatabase(type = DatabaseType.POSTGRES, provider = DatabaseProvider.ZONKY)
class FullHttpSecurityTest {

    @Autowired TestRestTemplate http;
    @Autowired JwtService jwtService;

    private HttpStatus get(String path, AuthUser user) {
        HttpHeaders headers = new HttpHeaders();
        if (user != null) {
            headers.setBearerAuth(jwtService.generateToken(user));
        }
        return HttpStatus.valueOf(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class)
                .getStatusCode().value());
    }

    @Test
    void customerOnAdminEndpointGets403NotAMisleading401() {
        assertThat(get("/api/admin/accounts", new AuthUser(1L, "c@x.com", Role.CUSTOMER)))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anonymousGets401AndAdminGets200() {
        assertThat(get("/api/admin/accounts", null)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/admin/accounts", new AuthUser(9L, "a@x.com", Role.ADMIN))).isEqualTo(HttpStatus.OK);
    }
}

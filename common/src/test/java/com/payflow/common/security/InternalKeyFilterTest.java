package com.payflow.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class InternalKeyFilterTest {

    private final InternalKeyFilter filter = new InternalKeyFilter("shared-internal-key");

    private static MockHttpServletRequest request(String uri, String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        if (key != null) {
            request.addHeader(InternalKeyFilter.HEADER, key);
        }
        return request;
    }

    @Test
    void internalCallWithCorrectKeyPassesThrough() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/internal/transfers", "shared-internal-key"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void internalCallWithWrongKeyIsRejected() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/internal/transfers", "guessed-key"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull(); // never reached the controller
    }

    @Test
    void internalCallWithoutKeyIsRejected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/internal/transfers", null), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void publicPathsAreNotChecked() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/accounts", null), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}

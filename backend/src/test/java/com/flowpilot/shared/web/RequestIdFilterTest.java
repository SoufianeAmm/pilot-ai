package com.flowpilot.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void doFilter_validIncomingId_isReusedInMdcAndResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "nginx-req-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> idSeenByHandler = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                idSeenByHandler.set(MDC.get(RequestIdFilter.MDC_KEY));
            }
        });

        assertThat(idSeenByHandler.get()).isEqualTo("nginx-req-12345");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("nginx-req-12345");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).as("MDC is cleared after the request").isNull();
    }

    @Test
    void doFilter_missingId_generatesUuid() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(UUID.fromString(response.getHeader(RequestIdFilter.HEADER))).isNotNull();
    }

    @Test
    void resolve_maliciousOrMalformedId_isReplaced() {
        assertThat(RequestIdFilter.resolve("abc\nFAKE LOG LINE")).doesNotContain("FAKE");
        assertThat(RequestIdFilter.resolve("short")).isNotEqualTo("short");
        assertThat(RequestIdFilter.resolve("x".repeat(65))).hasSize(36);
    }
}

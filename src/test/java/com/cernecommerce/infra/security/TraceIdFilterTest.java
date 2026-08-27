package com.cernecommerce.infra.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    private TraceIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter();
    }

    @Test
    void generates_uuid_when_no_header_present() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, new MockFilterChain());

        String traceId = res.getHeader("X-Trace-Id");
        assertThat(traceId).isNotNull().matches("[a-fA-F0-9\\-]{36}");
    }

    @Test
    void preserves_valid_trace_id_from_request() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Trace-Id", "abc-123-DEF");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, new MockFilterChain());

        assertThat(res.getHeader("X-Trace-Id")).isEqualTo("abc-123-DEF");
    }

    @Test
    void rejects_trace_id_with_newline_and_generates_uuid() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Trace-Id", "abc\ninjected-log-line");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, new MockFilterChain());

        String traceId = res.getHeader("X-Trace-Id");
        assertThat(traceId).doesNotContain("\n").doesNotContain("injected");
        assertThat(traceId).matches("[a-fA-F0-9\\-]{36}");
    }

    @Test
    void rejects_trace_id_exceeding_64_chars() throws Exception {
        String longId = "a".repeat(65);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Trace-Id", longId);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, new MockFilterChain());

        String traceId = res.getHeader("X-Trace-Id");
        assertThat(traceId).isNotEqualTo(longId);
        assertThat(traceId).matches("[a-fA-F0-9\\-]{36}");
    }

    @Test
    void rejects_trace_id_with_special_characters() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Trace-Id", "<script>alert(1)</script>");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, new MockFilterChain());

        String traceId = res.getHeader("X-Trace-Id");
        assertThat(traceId).doesNotContain("<").doesNotContain(">");
        assertThat(traceId).matches("[a-fA-F0-9\\-]{36}");
    }

    @Test
    void clears_mdc_after_request_even_on_error() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain failingChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws jakarta.servlet.ServletException, java.io.IOException {
                throw new java.io.IOException("simulated error");
            }
        };

        try {
            filter.doFilterInternal(req, res, failingChain);
        } catch (java.io.IOException ignored) {}

        assertThat(org.slf4j.MDC.get("traceId")).isNull();
    }
}

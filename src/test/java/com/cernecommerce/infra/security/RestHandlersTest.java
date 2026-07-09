package com.cernecommerce.infra.security;

import static org.mockito.Mockito.*;

import java.io.PrintWriter;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RestHandlersTest {

    @Test
    void entrypoint_writes_401_json() throws Exception {
        RestAuthenticationEntryPoint ep = new RestAuthenticationEntryPoint();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        PrintWriter writer = new PrintWriter(System.out);

        when(res.getWriter()).thenReturn(writer);

        ep.commence(req, res, new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

        verify(res).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(res).setContentType("application/json");
    }

    @Test
    void access_denied_handler_writes_403_json() throws Exception {
        RestAccessDeniedHandler handler = new RestAccessDeniedHandler();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        PrintWriter writer = new PrintWriter(System.out);

        when(res.getWriter()).thenReturn(writer);

        handler.handle(req, res, new org.springframework.security.access.AccessDeniedException("denied"));

        verify(res).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(res).setContentType("application/json");
    }
}

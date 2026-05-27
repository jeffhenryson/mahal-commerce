package com.securityspring.infra.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.securityspring.infra.security.RestAccessDeniedHandler;
import com.securityspring.infra.security.RestAuthenticationEntryPoint;
import com.securityspring.infra.security.TraceIdFilter;
import com.securityspring.infra.security.jwt.JwtAuthenticationFilter;
import com.securityspring.infra.security.LoginRateLimitingFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
                                           RestAuthenticationEntryPoint entryPoint, RestAccessDeniedHandler deniedHandler,
                                           LoginRateLimitingFilter loginRateLimitingFilter,
                                           TraceIdFilter traceIdFilter,
                                           @org.springframework.beans.factory.annotation.Value("${security.content-security-policy:}") String cspDirective) throws Exception {
        // Convenção de autorização: sempre hasAuthority(), nunca hasRole().
        // Roles têm prefixo ROLE_ (ex: ROLE_ADMIN); permissões não (ex: USER_CREATE).
        // hasRole("ADMIN") adiciona o prefixo automaticamente e seria equivalente a
        // hasAuthority("ROLE_ADMIN"), mas misturar os dois métodos gera inconsistência.
        // Usar hasAuthority() para tudo é mais explícito e funciona para roles e permissões.
        http
            .csrf(csrf -> csrf.disable())
            // Headers de segurança: X-Content-Type-Options, X-Frame-Options e HSTS (HTTPS only)
            // vêm dos defaults do Spring Security. Adicionamos Referrer-Policy e CSP explicitamente.
            // CSP configurável via security.content-security-policy; vazio = desabilitado (dev/Swagger).
            .headers(headers -> {
                headers.referrerPolicy(r -> r.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
                if (cspDirective != null && !cspDirective.isBlank()) {
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives(cspDirective));
                }
            })
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/actuator/health/**",
                    "/actuator/info"
                ).permitAll()
                // ATENÇÃO: estas regras DEVEM vir antes de /auth/** permitAll abaixo.
                // GET e DELETE /auth/sessions exigem autenticação; a regra de /auth/** é mais ampla
                // e cobriria esses endpoints se declarada primeiro. Não reordene sem revisar.
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/auth/sessions").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/auth/sessions").authenticated()
                .requestMatchers("/auth/verify-email", "/auth/resend-verification").permitAll()
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/actuator/**").hasAuthority("ROLE_ADMIN")
                .anyRequest().authenticated())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
            .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(loginRateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .cors(Customizer.withDefaults());
        return http.build();
    }

    // ProviderManager.eraseCredentialsAfterAuthentication is disabled because the default behaviour
    // calls UserDetails.eraseCredentials() which nullifies the password hash on the object stored
    // in the @Cacheable cache, causing BadCredentialsException on subsequent login attempts.
    // Disabling erasure retains the BCrypt hash in memory (the plaintext is never cached here);
    // the raw password in the Authentication token is still garbage-collected after the request.
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        ProviderManager manager = new ProviderManager(provider);
        manager.setEraseCredentialsAfterAuthentication(false);
        return manager;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${cors.allowed-origins:*}") String allowedOrigins,
            @org.springframework.beans.factory.annotation.Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}") String allowedMethods,
            @org.springframework.beans.factory.annotation.Value("${cors.allowed-headers:*}") String allowedHeaders,
            @org.springframework.beans.factory.annotation.Value("${cors.exposed-headers:X-Trace-Id}") String exposedHeaders) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of(allowedMethods.split(",")));
        config.setAllowedHeaders(List.of(allowedHeaders.split(",")));
        config.setExposedHeaders(List.of(exposedHeaders.split(",")));
        // JWT via Authorization header não usa cookies — credentials mode desnecessário e
        // incompatível com allowedOrigins("*") pela spec CORS (causaria IllegalArgumentException)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
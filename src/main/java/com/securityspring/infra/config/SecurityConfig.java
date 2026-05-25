package com.securityspring.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.securityspring.infra.security.CorrelationIdFilter;
import com.securityspring.infra.security.RestAccessDeniedHandler;
import com.securityspring.infra.security.RestAuthenticationEntryPoint;
import com.securityspring.infra.security.jwt.JwtAuthenticationFilter;
import com.securityspring.infra.security.ratelimit.LoginRateLimitingFilter;

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
                                           CorrelationIdFilter correlationIdFilter) throws Exception {
        // Convenção de autorização: sempre hasAuthority(), nunca hasRole().
        // Roles têm prefixo ROLE_ (ex: ROLE_ADMIN); permissões não (ex: USER_CREATE).
        // hasRole("ADMIN") adiciona o prefixo automaticamente e seria equivalente a
        // hasAuthority("ROLE_ADMIN"), mas misturar os dois métodos gera inconsistência.
        // Usar hasAuthority() para tudo é mais explícito e funciona para roles e permissões.
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/actuator/health/**",
                    "/actuator/info"
                ).permitAll()
                // ATENÇÃO: esta regra DEVE vir antes de /auth/** permitAll abaixo.
                // DELETE /auth/sessions exige autenticação; a regra de /auth/** é mais ampla
                // e cobriria este endpoint se declarada primeiro. Não reordene sem revisar.
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/auth/sessions").authenticated()
                .requestMatchers("/auth/verify-email", "/auth/resend-verification").permitAll()
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/actuator/**").hasAuthority("ROLE_ADMIN")
                .anyRequest().authenticated())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
            .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(loginRateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .cors(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${cors.allowed-origins:*}") String allowedOrigins,
            @org.springframework.beans.factory.annotation.Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}") String allowedMethods,
            @org.springframework.beans.factory.annotation.Value("${cors.allowed-headers:*}") String allowedHeaders) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of(allowedMethods.split(",")));
        config.setAllowedHeaders(List.of(allowedHeaders.split(",")));
        // JWT via Authorization header não usa cookies — credentials mode desnecessário e
        // incompatível com allowedOrigins("*") pela spec CORS (causaria IllegalArgumentException)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
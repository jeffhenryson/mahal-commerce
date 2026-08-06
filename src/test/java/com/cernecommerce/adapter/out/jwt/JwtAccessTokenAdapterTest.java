package com.cernecommerce.adapter.out.jwt;

import com.cernecommerce.infra.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class JwtAccessTokenAdapterTest {

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private JwtAccessTokenAdapter adapter;

    @Test
    void generateFor_delegatesToJwtService() {
        Set<String> authorities = Set.of("ROLE_USER");
        when(jwtService.generateAccessToken("user", authorities)).thenReturn("token123");
        
        String result = adapter.generateFor("user", authorities);
        
        assertThat(result).isEqualTo("token123");
        verify(jwtService).generateAccessToken("user", authorities);
    }
}

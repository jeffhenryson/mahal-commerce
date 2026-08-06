package com.cernecommerce.adapter.out.security.password;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BcryptPasswordHashAdapterTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private BcryptPasswordHashAdapter adapter;

    @Test
    void hash_delegatesToPasswordEncoder() {
        when(passwordEncoder.encode("raw")).thenReturn("encoded");
        
        String result = adapter.hash("raw");
        
        assertThat(result).isEqualTo("encoded");
        verify(passwordEncoder).encode("raw");
    }

    @Test
    void matches_delegatesToPasswordEncoder() {
        when(passwordEncoder.matches("raw", "encoded")).thenReturn(true);
        
        boolean result = adapter.matches("raw", "encoded");
        
        assertThat(result).isTrue();
        verify(passwordEncoder).matches("raw", "encoded");
    }
}

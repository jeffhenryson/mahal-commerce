package com.cernecommerce.adapter.out.security.credential;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import com.cernecommerce.core.ports.out.credential.CredentialVerifierPort.VerifiedUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SpringCredentialVerifierAdapterTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private SpringCredentialVerifierAdapter adapter;

    @Test
    void verify_returnsVerifiedUser_whenAuthenticationIsSuccessful() {
        UserDetails userDetails = new User("admin", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, "pass", userDetails.getAuthorities());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        
        VerifiedUser result = adapter.verify("admin", "pass");
        
        assertThat(result.username()).isEqualTo("admin");
        assertThat(result.authorities()).containsExactly("ROLE_ADMIN");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }
}

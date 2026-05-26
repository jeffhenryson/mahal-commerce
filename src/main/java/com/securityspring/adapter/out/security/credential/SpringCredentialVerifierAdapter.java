package com.securityspring.adapter.out.security.credential;

import com.securityspring.core.ports.out.credential.CredentialVerifierPort;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SpringCredentialVerifierAdapter implements CredentialVerifierPort {

    private final AuthenticationManager authenticationManager;

    public SpringCredentialVerifierAdapter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public VerifiedUser verify(String username, String password) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
        UserDetails details = (UserDetails) auth.getPrincipal();
        Set<String> authorities = details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return new VerifiedUser(details.getUsername(), authorities);
    }
}

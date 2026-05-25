package com.security_spring.core.domain.model;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class User {

    private Long id;
    private String username;
    private String password;
    private boolean enabled = true;
    private Set<Role> roles = new HashSet<>();

    User() {
    }

    /**
     * Factory for creating a valid User with all required fields set.
     * Use this instead of the no-arg constructor in business logic.
     */
    public static User of(String username, String hashedPassword, Set<Role> roles) {
        Objects.requireNonNull(username, "username is required");
        Objects.requireNonNull(hashedPassword, "password is required");
        User u = new User();
        u.username = username;
        u.password = hashedPassword;
        u.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
        return u;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }
}

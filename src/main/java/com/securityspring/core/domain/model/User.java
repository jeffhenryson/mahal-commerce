package com.securityspring.core.domain.model;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class User {

    private Long id;
    private String username;
    private String password;
    private boolean enabled = true;
    private String email;
    private boolean emailVerified = false;
    private Set<Role> roles = new HashSet<>();

    User() {
    }

    /** Factory para criação de novo usuário sem email (ex: seed, admin). Criado com enabled=true. */
    public static User of(String username, String hashedPassword, Set<Role> roles) {
        Objects.requireNonNull(username, "username is required");
        Objects.requireNonNull(hashedPassword, "password is required");
        User u = new User();
        u.username = username;
        u.password = hashedPassword;
        u.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
        return u;
    }

    /** Factory para criação via cadastro externo. Criado com enabled=false até confirmação de email. */
    public static User ofPendingVerification(String username, String hashedPassword,
                                              String email, Set<Role> roles) {
        Objects.requireNonNull(email, "email is required");
        User u = of(username, hashedPassword, roles);
        u.email = email;
        u.enabled = false;
        return u;
    }

    /** Factory para reconstituição a partir de persistência — preserva todos os campos. */
    public static User fromPersisted(Long id, String username, String hashedPassword,
                                     boolean enabled, String email, boolean emailVerified,
                                     Set<Role> roles) {
        Objects.requireNonNull(id, "id is required for persisted user");
        User u = of(username, hashedPassword, roles);
        u.id = id;
        u.enabled = enabled;
        u.email = email;
        u.emailVerified = emailVerified;
        return u;
    }

    // --- Domain operations ---

    public void assignEmail(String email) {
        this.email = email;
    }

    public void changeEmail(String newEmail) {
        Objects.requireNonNull(newEmail, "newEmail is required");
        this.email = newEmail;
        this.emailVerified = false;
    }

    public void changePassword(String hashedPassword) {
        Objects.requireNonNull(hashedPassword, "hashedPassword is required");
        this.password = hashedPassword;
    }

    public void rename(String newUsername) {
        Objects.requireNonNull(newUsername, "newUsername is required");
        this.username = newUsername;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void confirmEmail() {
        this.emailVerified = true;
        this.enabled = true;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    // --- Accessors ---

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public Set<Role> getRoles() {
        return roles;
    }
}

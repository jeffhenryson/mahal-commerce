package com.cernecommerce.core.domain.model.auth;

import com.cernecommerce.core.domain.model.rbac.Role;

import java.time.Instant;
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
    private String pendingEmail;
    private String avatarFilename;
    private Instant createdAt;
    private Set<Role> roles = new HashSet<>();
    private String googleId;
    private AuthProvider authProvider = AuthProvider.LOCAL;

    User() {
    }

    public static User of(String username, String hashedPassword, Set<Role> roles) {
        Objects.requireNonNull(username, "username is required");
        Objects.requireNonNull(hashedPassword, "password is required");
        User u = new User();
        u.username = username;
        u.password = hashedPassword;
        u.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
        return u;
    }

    public static User ofPendingVerification(String username, String hashedPassword,
            String email, Set<Role> roles) {
        Objects.requireNonNull(email, "email is required");
        User u = of(username, hashedPassword, roles);
        u.email = email;
        u.enabled = false;
        return u;
    }

    public static User fromGoogle(String googleId, String username, String email, Set<Role> roles) {
        Objects.requireNonNull(googleId, "googleId is required");
        Objects.requireNonNull(username, "username is required");
        Objects.requireNonNull(email, "email is required");
        User u = new User();
        u.googleId = googleId;
        u.username = username;
        u.email = email;
        u.emailVerified = true;
        u.enabled = true;
        u.authProvider = AuthProvider.GOOGLE;
        u.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
        return u;
    }

    public static User fromPersisted(Long id, String username, String hashedPassword,
            boolean enabled, String email, boolean emailVerified, String pendingEmail,
            String avatarFilename, Instant createdAt, Set<Role> roles,
            String googleId, AuthProvider authProvider) {
        Objects.requireNonNull(id, "id is required for persisted user");
        User u = new User();
        u.id = id;
        u.username = username;
        u.password = hashedPassword;
        u.enabled = enabled;
        u.email = email;
        u.emailVerified = emailVerified;
        u.pendingEmail = pendingEmail;
        u.avatarFilename = avatarFilename;
        u.createdAt = createdAt;
        u.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
        u.googleId = googleId;
        u.authProvider = authProvider != null ? authProvider : AuthProvider.LOCAL;
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

    public void setPendingEmail(String email) {
        Objects.requireNonNull(email, "pendingEmail is required");
        this.pendingEmail = email;
    }

    public void applyPendingEmail() {
        Objects.requireNonNull(pendingEmail, "no pending email to apply");
        this.email = this.pendingEmail;
        this.pendingEmail = null;
    }

    public void setAvatar(String avatarFilename) {
        this.avatarFilename = avatarFilename;
    }

    public void clearAvatar() {
        this.avatarFilename = null;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void removeRole(Role role) {
        this.roles.removeIf(r -> r.getName().equals(role.getName()));
    }

    public void linkGoogle(String googleId) {
        Objects.requireNonNull(googleId, "googleId is required");
        this.googleId = googleId;
    }

    // --- Accessors ---

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public boolean isEnabled() { return enabled; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getPendingEmail() { return pendingEmail; }
    public String getAvatarFilename() { return avatarFilename; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<Role> getRoles() { return roles; }
    public String getGoogleId() { return googleId; }
    public AuthProvider getAuthProvider() { return authProvider; }
}

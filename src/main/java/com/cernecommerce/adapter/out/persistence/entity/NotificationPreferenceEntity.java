package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "notification_preferences")
@IdClass(NotificationPreferenceEntity.PreferenceId.class)
public class NotificationPreferenceEntity {

    @Id
    @Column(nullable = false, length = 80)
    private String username;

    @Id
    @Column(nullable = false, length = 50)
    private String type;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isInAppEnabled() { return inAppEnabled; }
    public void setInAppEnabled(boolean inAppEnabled) { this.inAppEnabled = inAppEnabled; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }

    public static class PreferenceId implements Serializable {
        private String username;
        private String type;

        public PreferenceId() {}
        public PreferenceId(String username, String type) { this.username = username; this.type = type; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PreferenceId that)) return false;
            return Objects.equals(username, that.username) && Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() { return Objects.hash(username, type); }
    }
}

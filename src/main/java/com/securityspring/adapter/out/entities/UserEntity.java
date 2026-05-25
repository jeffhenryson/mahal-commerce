package com.securityspring.adapter.out.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_user_email", columnNames = "email")
        })
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 80, unique = true)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 254)
    private String email;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_user_roles_user")),
            inverseJoinColumns = @JoinColumn(name = "role_id", foreignKey = @ForeignKey(name = "fk_user_roles_role")))
    @ToString.Exclude
    private Set<RoleEntity> roles = new HashSet<>();
}
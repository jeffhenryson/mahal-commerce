package com.securityspring.adapter.out.converter;

import com.securityspring.adapter.out.entities.PermissionEntity;
import com.securityspring.adapter.out.entities.RoleEntity;
import com.securityspring.adapter.out.entities.UserEntity;
import com.securityspring.core.domain.model.Permission;
import com.securityspring.core.domain.model.Role;
import com.securityspring.core.domain.model.User;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class UserEntityConverter {

    public User toDomain(UserEntity entity) {
        if (entity == null) return null;
        return User.fromPersisted(entity.getId(), entity.getUsername(), entity.getPassword(),
                entity.isEnabled(), entity.getEmail(), entity.isEmailVerified(),
                toDomainRoles(entity.getRoles()));
    }

    public UserEntity toEntityBase(User domain) {
        if (domain == null) return null;
        UserEntity entity = new UserEntity();
        entity.setId(domain.getId());
        entity.setUsername(domain.getUsername());
        entity.setPassword(domain.getPassword());
        entity.setEnabled(domain.isEnabled());
        entity.setEmail(domain.getEmail());
        entity.setEmailVerified(domain.isEmailVerified());
        return entity;
    }

    public Set<Role> toDomainRoles(Set<RoleEntity> roleEntities) {
        if (roleEntities == null) return new HashSet<>();
        return roleEntities.stream()
                .map(re -> Role.of(re.getId(), re.getName(), toDomainPermissions(re.getPermissions())))
                .collect(Collectors.toSet());
    }

    private Set<Permission> toDomainPermissions(Set<PermissionEntity> permEntities) {
        if (permEntities == null) return new HashSet<>();
        return permEntities.stream()
                .map(pe -> Permission.of(pe.getId(), pe.getName()))
                .collect(Collectors.toSet());
    }
}

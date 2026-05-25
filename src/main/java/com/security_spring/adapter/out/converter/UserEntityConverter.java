package com.security_spring.adapter.out.converter;

import com.security_spring.adapter.out.entities.PermissionEntity;
import com.security_spring.adapter.out.entities.RoleEntity;
import com.security_spring.adapter.out.entities.UserEntity;
import com.security_spring.core.domain.model.Permission;
import com.security_spring.core.domain.model.Role;
import com.security_spring.core.domain.model.User;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class UserEntityConverter {

    public User toDomain(UserEntity entity) {
        if (entity == null) return null;
        User user = new User();
        user.setId(entity.getId());
        user.setUsername(entity.getUsername());
        user.setPassword(entity.getPassword());
        user.setRoles(toDomainRoles(entity.getRoles()));
        return user;
    }

    public UserEntity toEntity(User domain) {
        if (domain == null) return null;
        UserEntity entity = new UserEntity();
        entity.setId(domain.getId());
        entity.setUsername(domain.getUsername());
        entity.setPassword(domain.getPassword());
        return entity;
    }

    public Set<Role> toDomainRoles(Set<RoleEntity> roleEntities) {
        if (roleEntities == null) return new HashSet<>();
        return roleEntities.stream()
                .map(re -> {
                    Role r = new Role();
                    r.setId(re.getId());
                    r.setName(re.getName());
                    r.setPermissions(toDomainPermissions(re.getPermissions()));
                    return r;
                })
                .collect(Collectors.toSet());
    }

    private Set<Permission> toDomainPermissions(Set<PermissionEntity> permEntities) {
        if (permEntities == null) return new HashSet<>();
        return permEntities.stream()
                .map(pe -> {
                    Permission p = new Permission();
                    p.setId(pe.getId());
                    p.setName(pe.getName());
                    return p;
                })
                .collect(Collectors.toSet());
    }
}

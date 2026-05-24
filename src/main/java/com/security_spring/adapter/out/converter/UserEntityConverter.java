package com.security_spring.adapter.out.converter;

import com.security_spring.adapter.out.entities.RoleEntity;
import com.security_spring.adapter.out.entities.UserEntity;
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
        // Importante: password não deve ser exposto além do necessário
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
        // Roles serão associadas no repository impl (carregando RoleEntity por nome)
        return entity;
    }

    public Set<Role> toDomainRoles(Set<RoleEntity> roleEntities) {
        if (roleEntities == null) return new HashSet<>();
        return roleEntities.stream()
                .map(re -> {
                    Role r = new Role();
                    r.setId(re.getId());
                    r.setName(re.getName());
                    return r;
                })
                .collect(Collectors.toSet());
    }
}

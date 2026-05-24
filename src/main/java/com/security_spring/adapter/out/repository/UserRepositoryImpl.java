package com.security_spring.adapter.out.repository;


import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

import com.security_spring.adapter.out.converter.UserEntityConverter;
import com.security_spring.adapter.out.entities.RoleEntity;
import com.security_spring.adapter.out.entities.UserEntity;
import com.security_spring.core.domain.model.Role;
import com.security_spring.core.domain.model.User;
import com.security_spring.core.ports.out.UserRepository;

import jakarta.transaction.Transactional;

@Repository
@Transactional
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userRepo;
    private final RoleJpaRepository roleRepo;
    private final UserEntityConverter converter;
    private final PasswordEncoder passwordEncoder;

    public UserRepositoryImpl(UserJpaRepository userRepo,
                              RoleJpaRepository roleRepo,
                              UserEntityConverter converter,
                              PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.converter = converter;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User save(User user) {
        UserEntity entity = converter.toEntity(user);

        if (entity.getPassword() != null) {
            // Sempre encode aqui (o core não conhece infra)
            entity.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            Set<RoleEntity> roleEntities = user.getRoles().stream()
                    .map(Role::getName)
                    .filter(n -> n != null && !n.isBlank())
                    .distinct()
                    .map(name -> roleRepo.findByName(name).orElseGet(() -> {
                        RoleEntity re = new RoleEntity();
                        re.setName(name);
                        return roleRepo.save(re);
                    }))
                    .collect(Collectors.toSet());
            entity.setRoles(roleEntities);
        }

        UserEntity saved = userRepo.save(entity);
        return converter.toDomain(saved);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userRepo.findById(id).map(e -> converter.toDomain(e));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepo.findByUsername(username).map(e -> converter.toDomain(e));
    }

    @Override
    public java.util.List<User> findAll() {
        return userRepo.findAll().stream()
                .map(e -> converter.toDomain(e))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        userRepo.deleteById(id);
    }
}
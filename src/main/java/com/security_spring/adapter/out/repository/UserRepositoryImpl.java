package com.security_spring.adapter.out.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.security_spring.adapter.out.converter.UserEntityConverter;
import com.security_spring.adapter.out.entities.RoleEntity;
import com.security_spring.adapter.out.entities.UserEntity;
import com.security_spring.core.domain.exception.RoleNotFoundException;
import com.security_spring.core.domain.model.PageResult;
import com.security_spring.core.domain.model.Role;
import com.security_spring.core.domain.model.User;
import com.security_spring.core.ports.out.RoleRepository;
import com.security_spring.core.ports.out.UserRepository;

@Repository
@Transactional
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userRepo;
    private final RoleJpaRepository roleJpaRepo;
    private final RoleRepository roleRepository;
    private final UserEntityConverter converter;

    public UserRepositoryImpl(UserJpaRepository userRepo,
                              RoleJpaRepository roleJpaRepo,
                              RoleRepository roleRepository,
                              UserEntityConverter converter) {
        this.userRepo = userRepo;
        this.roleJpaRepo = roleJpaRepo;
        this.roleRepository = roleRepository;
        this.converter = converter;
    }

    @Override
    public User save(User user) {
        UserEntity entity = converter.toEntityBase(user);

        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            Set<RoleEntity> roleEntities = user.getRoles().stream()
                    .map(Role::getName)
                    .filter(n -> n != null && !n.isBlank())
                    .distinct()
                    .map(name -> {
                        // Validate role exists via port, then get a JPA proxy by ID.
                        Role domainRole = roleRepository.findByName(name)
                                .orElseThrow(() -> new RoleNotFoundException(name));
                        return roleJpaRepo.getReferenceById(domainRole.getId());
                    })
                    .collect(Collectors.toSet());
            entity.setRoles(roleEntities);
        }

        UserEntity saved = userRepo.save(entity);
        return converter.toDomain(saved);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userRepo.findByIdWithRoles(id).map(converter::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepo.findByUsernameWithRoles(username).map(converter::toDomain);
    }

    @Override
    public PageResult<User> findAll(int page, int size) {
        Page<Long> idPage = userRepo.findAllIds(PageRequest.of(page, size));
        List<User> users = idPage.isEmpty()
                ? List.of()
                : userRepo.findAllWithRolesByIdIn(idPage.getContent())
                          .stream()
                          .map(converter::toDomain)
                          .collect(Collectors.toList());
        return new PageResult<>(users, page, size, idPage.getTotalElements(), idPage.getTotalPages());
    }

    @Override
    public void deleteById(Long id) {
        userRepo.deleteById(id);
    }
}

package com.securityspring.adapter.out.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.securityspring.adapter.out.converter.UserEntityConverter;
import com.securityspring.adapter.out.entities.RoleEntity;
import com.securityspring.adapter.out.entities.UserEntity;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.Role;
import com.securityspring.core.domain.model.User;
import com.securityspring.core.ports.out.UserRepository;

@Repository
@Transactional
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userRepo;
    private final RoleJpaRepository roleJpaRepo;
    private final UserEntityConverter converter;

    public UserRepositoryImpl(UserJpaRepository userRepo,
                              RoleJpaRepository roleJpaRepo,
                              UserEntityConverter converter) {
        this.userRepo = userRepo;
        this.roleJpaRepo = roleJpaRepo;
        this.converter = converter;
    }

    @Override
    public User save(User user) {
        UserEntity entity = converter.toEntityBase(user);

        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            // Roles carry their IDs from the service layer — use JPA references directly
            // without going back to the port, decoupling this adapter from RoleRepository.
            Set<RoleEntity> roleEntities = user.getRoles().stream()
                    .filter(r -> r.getId() != null)
                    .map(r -> roleJpaRepo.getReferenceById(r.getId()))
                    .collect(Collectors.toSet());
            entity.setRoles(roleEntities);
        }

        UserEntity saved = userRepo.save(entity);
        return converter.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepo.findByIdWithRoles(id).map(converter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepo.findByUsernameWithRoles(username).map(converter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<User> findAll(int page, int size) {
        // Ordering is deterministic: both JPQL queries use ORDER BY u.id.
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

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepo.findByEmailWithRoles(email).map(converter::toDomain);
    }
}

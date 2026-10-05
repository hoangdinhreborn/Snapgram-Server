package com.example.auth.repository;

import com.example.auth.entity.AuthPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthPermissionRepository extends JpaRepository<AuthPermission, UUID> {

    Optional<AuthPermission> findByName(String name);

    List<AuthPermission> findByCategoryOrderByCategoryAscNameAsc(String category);

    List<AuthPermission> findAllByOrderByCategoryAscNameAsc();
}

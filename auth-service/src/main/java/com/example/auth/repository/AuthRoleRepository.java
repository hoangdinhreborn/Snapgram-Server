package com.example.auth.repository;

import com.example.auth.entity.AuthRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthRoleRepository extends JpaRepository<AuthRole, UUID> {

    Optional<AuthRole> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT r FROM AuthRole r LEFT JOIN FETCH r.permissions WHERE r.name IN :names")
    List<AuthRole> findByNamesWithPermissions(@Param("names") Collection<String> names);

    @Query("SELECT DISTINCT r FROM AuthRole r LEFT JOIN FETCH r.permissions ORDER BY r.name ASC")
    List<AuthRole> findAllWithPermissions();
}

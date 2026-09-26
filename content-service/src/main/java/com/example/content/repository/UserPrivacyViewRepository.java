package com.example.content.repository;

import com.example.content.entity.UserPrivacyView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserPrivacyViewRepository extends JpaRepository<UserPrivacyView, UUID> {
}

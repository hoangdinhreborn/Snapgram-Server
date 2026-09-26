package com.example.content.repository;

import com.example.content.entity.CloseFriend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CloseFriendRepository extends JpaRepository<CloseFriend, CloseFriend.CloseFriendId> {

    boolean existsByIdOwnerIdAndIdFriendId(UUID ownerId, UUID friendId);

    @Query("SELECT c.id.friendId FROM CloseFriend c WHERE c.id.ownerId = :ownerId")
    List<UUID> findFriendIds(@Param("ownerId") UUID ownerId);
}

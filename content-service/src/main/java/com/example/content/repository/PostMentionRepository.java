package com.example.content.repository;

import com.example.content.entity.PostMention;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostMentionRepository extends JpaRepository<PostMention, PostMention.PostMentionId> {

    List<PostMention> findByIdPostId(UUID postId);

    void deleteByIdPostId(UUID postId);
}

package com.example.recommender.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recommender_post_view")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommenderPostView {

    @Id
    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Column(columnDefinition = "TEXT")
    private String caption;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(nullable = false, length = 30)
    private String visibility;

    @Column(name = "author_is_private", nullable = false)
    private boolean authorIsPrivate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}

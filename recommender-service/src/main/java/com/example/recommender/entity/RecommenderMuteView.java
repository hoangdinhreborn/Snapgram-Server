package com.example.recommender.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recommender_mute_view")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommenderMuteView {

    @EmbeddedId
    private RecommenderMuteId id;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Embeddable
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommenderMuteId implements Serializable {
        @Column(name = "muter_id")
        private UUID muterId;

        @Column(name = "muted_id")
        private UUID mutedId;
    }
}

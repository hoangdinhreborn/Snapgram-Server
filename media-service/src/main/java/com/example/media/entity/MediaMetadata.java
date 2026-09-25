package com.example.media.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "media_metadata")
@Data
public class MediaMetadata {

    @Id
    @Column(name = "media_id")
    private UUID mediaId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "media_id")
    @ToString.Exclude
    private MediaFile mediaFile;

    private Integer width;

    private Integer height;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "exif_stripped", nullable = false)
    private boolean exifStripped = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
}

package com.example.auth.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FacebookUserProfile {

    private String id;
    private String name;
    private String email;
    private PictureWrapper picture;

    public String getAvatarUrl() {
        if (picture != null && picture.getData() != null) {
            return picture.getData().getUrl();
        }
        return null;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PictureWrapper {
        private PictureData data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PictureData {
        private String url;
        @JsonProperty("is_silhouette")
        private Boolean isSilhouette;
        private Integer width;
        private Integer height;
    }
}

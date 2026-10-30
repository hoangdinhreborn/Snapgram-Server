package com.example.content.dto.story;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateHighlightRequest {

    @NotBlank(message = "Tên bộ sưu tập không được để trống")
    @Size(max = 100, message = "Tên không quá 100 ký tự")
    private String name;

    private UUID coverStoryId;

    private List<UUID> storyIds;
}

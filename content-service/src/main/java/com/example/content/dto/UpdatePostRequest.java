package com.example.content.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePostRequest {
    @Size(max = 2200)
    private String caption;
    private String tags;
    private String visibility;
}

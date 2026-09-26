package com.example.content.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class SavePostRequest {
    private UUID collectionId;
}

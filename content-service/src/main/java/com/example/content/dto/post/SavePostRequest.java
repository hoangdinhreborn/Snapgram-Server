package com.example.content.dto.post;

import lombok.Data;
import java.util.UUID;

@Data
public class SavePostRequest {
    private UUID collectionId;
}

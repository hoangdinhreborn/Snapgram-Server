package com.example.auth.controller;

import com.example.auth.dto.BatchUserRequest;
import com.example.auth.dto.UserSummaryDto;
import com.example.auth.service.EmailVerificationService;
import com.example.auth.service.TwoFaService;
import com.example.auth.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerBatchTest {

    @Mock
    private UserService userService;

    @Mock
    private TwoFaService twoFaService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private UserController userController;

    @Test
    @DisplayName("getUsersBatch should return list of UserSummaryDto from userService")
    void getUsersBatch_ShouldReturnList() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        List<UUID> ids = List.of(u1, u2);
        BatchUserRequest request = new BatchUserRequest(ids);

        List<UserSummaryDto> expected = List.of(
                UserSummaryDto.builder()
                        .id(u1.toString())
                        .username("user1")
                        .displayName("User One")
                        .avatarUrl("https://example.com/1.jpg")
                        .privateAccount(false)
                        .build(),
                UserSummaryDto.builder()
                        .id(u2.toString())
                        .username("user2")
                        .displayName("User Two")
                        .avatarUrl("https://example.com/2.jpg")
                        .privateAccount(true)
                        .build()
        );

        when(userService.getUsersSummaryBatch(isNull(), eq(ids))).thenReturn(expected);

        ResponseEntity<List<UserSummaryDto>> response = userController.getUsersBatch(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(2);
        assertThat(response.getBody().get(0).getUsername()).isEqualTo("user1");
        assertThat(response.getBody().get(1).getUsername()).isEqualTo("user2");
        verify(userService).getUsersSummaryBatch(isNull(), eq(ids));
    }
}

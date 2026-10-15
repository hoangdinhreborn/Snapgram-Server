package com.example.auth.controller;

import com.example.auth.dto.AdminUserResponse;
import com.example.auth.service.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminController adminController;

    @Test
    @DisplayName("getUsers should pass q, status, role and default sort to adminService")
    void getUsers_WithFilters_ShouldDelegateCorrectly() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(adminService.getUsers(eq("john"), eq("ACTIVE"), eq("ADMIN"), any(Pageable.class)))
                .thenReturn(Page.empty());

        ResponseEntity<Page<AdminUserResponse>> response = adminController.getUsers(
                "john", "ACTIVE", "ADMIN", "createdAt,desc", 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(adminService).getUsers(eq("john"), eq("ACTIVE"), eq("ADMIN"), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("getUsers with custom ascending sort on username should parse correctly")
    void getUsers_WithCustomSort_ShouldParseSortCorrectly() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(adminService.getUsers(any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        ResponseEntity<Page<AdminUserResponse>> response = adminController.getUsers(
                null, null, null, "username,asc", 1, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(adminService).getUsers(any(), any(), any(), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("username")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("username").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("getUsers with invalid sort field should fallback to createdAt desc")
    void getUsers_WithInvalidSortField_ShouldFallbackToCreatedAtDesc() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(adminService.getUsers(any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        ResponseEntity<Page<AdminUserResponse>> response = adminController.getUsers(
                null, null, null, "malicious_column;DROP TABLE,desc", 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(adminService).getUsers(any(), any(), any(), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}

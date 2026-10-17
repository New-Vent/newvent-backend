package com.newvent.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import com.newvent.admin.domain.Admin;
import com.newvent.admin.dto.response.AdminProfileResponse;
import com.newvent.admin.repository.AdminRepository;

class AdminProfileServiceTest {

    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final AdminProfileService adminProfileService = new AdminProfileService(adminRepository);

    @Test
    void 활성_관리자의_본인_정보를_조회한다() {
        Admin admin = mock(Admin.class);

        when(admin.getId()).thenReturn(1L);
        when(admin.getLoginId()).thenReturn("admin01");
        when(admin.getName()).thenReturn("뉴벤트");
        when(admin.isActive()).thenReturn(true);
        when(adminRepository.findById(1L))
            .thenReturn(Optional.of(admin));

        AdminProfileResponse response =
            adminProfileService.getMyProfile(1L);

        assertEquals(1L, response.id());
        assertEquals("admin01", response.loginId());
        assertEquals("뉴벤트", response.name());

        verify(adminRepository).findById(1L);
    }

    @Test
    void 존재하지_않는_관리자는_조회를_거부한다() {
        when(adminRepository.findById(999L))
            .thenReturn(Optional.empty());

        assertThrows(
            BadCredentialsException.class,
            () -> adminProfileService.getMyProfile(999L));
    }

    @Test
    void 비활성_관리자는_조회를_거부한다() {
        Admin admin = mock(Admin.class);

        when(admin.isActive()).thenReturn(false);
        when(adminRepository.findById(1L))
            .thenReturn(Optional.of(admin));

        assertThrows(
            BadCredentialsException.class,
            () -> adminProfileService.getMyProfile(1L));
    }
}

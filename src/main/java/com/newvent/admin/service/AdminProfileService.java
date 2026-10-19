package com.newvent.admin.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.admin.domain.Admin;
import com.newvent.admin.dto.response.AdminProfileResponse;
import com.newvent.admin.repository.AdminRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProfileService {

    private final AdminRepository adminRepository;

    public AdminProfileResponse getMyProfile(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
            .filter(Admin::isActive)
            .orElseThrow(() -> new BadCredentialsException("유효한 관리자 계정이 아닙니다."));

        return AdminProfileResponse.from(admin);
    }
}

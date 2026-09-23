package com.newvent.user.web;

import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;

public record UserResponse(
        Long id, String loginId, String name, String email, String phone, int plan, MembershipGrade membershipGrade) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getLoginId(), user.getName(), user.getEmail(), user.getPhone(), user.getPlan(),
                user.getMembershipGrade());
    }
}

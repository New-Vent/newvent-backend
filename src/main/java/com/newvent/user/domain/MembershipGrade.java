package com.newvent.user.domain;

public enum MembershipGrade {
    NORMAL("일반"),
    EXCELLENT("우수"),
    BEST("최우수");

    private final String label;

    MembershipGrade(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

package com.newvent.filtering;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PrivacyDetectorTest {
    @Test
    void detectsDomesticInternationalAndLandlinePhones() {
        for (String text : new String[]{"010-1234-5678", "01012345678", "+82 10 1234 5678",
                "02-123-4567", "031-1234-5678", "０１０-１２３４-５６７８"}) {
            assertTrue(PrivacyDetector.types(text).contains("PHONE"), text);
        }
    }
    @Test
    void detectsRoadAndLotAddresses() {
        for (String text : new String[]{"서울특별시 강남구 테헤란로 123", "서초구 반포대로 58",
                "서울 강남구 역삼동 123-4", "경기도 성남시 분당구 판교역로 235"}) {
            assertTrue(PrivacyDetector.types(text).contains("ADDRESS"), text);
        }
    }
    @Test
    void detectsEmailAndResidentNumber() {
        assertTrue(PrivacyDetector.types("test+event@example.com").contains("EMAIL"));
        assertTrue(PrivacyDetector.types("900101-1234567").contains("RESIDENT_NUMBER"));
    }
    @Test
    void keepsOrdinaryNumbersAndLocationNamesOut() {
        assertTrue(PrivacyDetector.types("혜택 10GB 3만원 72시간 100% 2026-09-30 서울 강남구").isEmpty());
    }
}

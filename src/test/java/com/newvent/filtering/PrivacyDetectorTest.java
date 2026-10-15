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
    void detectsMoreAddressForms() {
        for (String text : new String[]{"서울 강남구 테헤란로 152", "성남시 분당구 판교로 235", "부산 해운대구 우동 1234",
                "경기 파주시 문산읍 123-4", "서울시 강남구 테헤란로152", "대구 수성구 달구벌대로 2410",
                "제주특별자치도 제주시 연동 123-4", "제주시 첨단로 242", "서울 종로구 세종대로 175",
                "배송지는 서울 마포구 월드컵북로 396 입니다", "강남구 역삼동 산 12-3",
                // "번길" · "번지" 는 주소에만 쓰는 말이라 지역 낱말 없이도 잡는다
                "테헤란로 152번길 3", "역삼동 123번지"}) {
            assertTrue(PrivacyDetector.types(text).contains("ADDRESS"), text);
        }
    }

    /**
     * 이벤트 요청에 흔한 "~하면 숫자", "~으로 숫자" 가 주소로 판정되던 것을 막는다.
     * 조사 "으로" 와 어미 "~하면" 의 끝 글자가 도로명 "로" · 읍면 "면" 과 같아서 걸렸다.
     */
    @Test
    void ordinaryEventRequestsAreNotAddresses() {
        for (String text : new String[]{
                "가입하면 3GB를 드립니다", "참여하면 5000원 쿠폰 지급", "응모하면 10명에게 경품을 드려요",
                "추첨으로 50명에게 드립니다", "선착순으로 100명", "무료로 3GB 제공", "10만원으로 3개월 사용",
                "활동 3회 이상 참여하면 포인트", "이동 5분 거리 매장 방문 이벤트", "참여 방법 정리 3단계로 써줘",
                "리뷰 작성하면 2배 적립", "화면 3번 클릭하면 응모 완료", "운동 2시간 하면 포인트", "리워드로 5000원 지급",
                "신청하면 1000명 추첨", "다운로드하면 2GB 추가", "친구를 초대하면 3명까지 쿠폰", "매일 출석하면 100포인트",
                "구독하면 3개월 무료", "이용하면 5회 쿠폰", "로그인하면 30% 할인", "가입 시 무료로 3GB 제공",
                "서울시민이면 3GB", "대전 고객이 가입하면 5GB", "부산 지역 가입자는 이벤트 참여하면 2배",
                "강남구 매장에서 구매하면 5천원 할인", "전국 어디서나 신청하면 1만원",
                "제주도 여행자가 로밍 가입하면 3일 무료", "경기도 거주자가 참여하면 10명 추첨",
                // 지역 낱말로 끝나는 말 바로 뒤에 오는 경우 — 붙여 쓴 "~시" 와 "~도" 로 끝나는 명사
                "구매시 무료로 3개월 이용", "참여시 활동 3회 이상", "가입시 이동 5분 거리 매장", "대전시 가입하면 3GB",
                "서울시 거주자 추첨으로 50명", "강남구 가입하면 5GB 제공", "만족도 이동 3분", "신청시 무료로 1GB",
                "이용시 정리 3단계", "응모시 추첨으로 50명"}) {
            assertFalse(PrivacyDetector.types(text).contains("ADDRESS"), text);
        }
    }

    /**
     * 부사 "다시"가 "시"로 끝나서 지역 낱말로 읽히고, 뒤의 "쓰면 401" 이 읍·면 + 번호로 읽히던 것.
     * 저장소 문서의 "이미 쓴 토큰을 다시 쓰면 401" 에서 발견했다.
     */
    @Test
    void adverbAgainAndTimeWordsAreNotRegions() {
        for (String text : new String[]{
                "다시 쓰면 401 응답", "다시 오면 3일 무료", "다시 사면 20% 할인", "다시 보면 3번 더 쉬워요",
                "다시 들어오면 2배 적립", "다시 신청하면 5GB 추가", "다시 참여하면 10명 추첨", "다시 가면 3일 더",
                "다시 받으면 1GB", "다시 먹으면 5개 증정", "다시 입으면 2벌", "다시 타면 3회",
                "당시 가입하면 3GB", "수시 접속하면 5회", "매시 정각에 접속하면 100명", "동시 접속하면 3명",
                "일시 중지하면 2일", "임시 저장하면 5분", "정시 도착하면 1000원", "한시 운영하면 3일", "상시 신청하면 10명"}) {
            assertFalse(PrivacyDetector.types(text).contains("ADDRESS"), text);
        }
    }

    /** 지명에 쓰이는 글자로 끝나는 시·면은 계속 잡는다 (안동시, 서종면 …) */
    @Test
    void stillDetectsRegionsThatEndWithCommonCharacters() {
        for (String text : new String[]{"경북 안동시 경동로 123", "경기 동두천시 중앙로 100", "강원 동해시 천곡로 12",
                "경남 거제시 거제중앙로 1926", "전남 목포시 영산로 100", "경기 양평군 서종면 123-4",
                "강원 홍천군 서면 123", "경기 가평군 설악면 12-3", "강원 평창군 대화면 산 12", "서울 영등포구 여의도동 1"}) {
            assertTrue(PrivacyDetector.types(text).contains("ADDRESS"), text);
        }
    }

    /**
     * 어미 "~하므로", 부사 "~대로", "가로로" 처럼 끝 글자가 도로명 접미사(로 · 대로 · 길)와 같은 낱말.
     * 지역 낱말이 앞에 없으면 주소로 보지 않는다.
     */
    @Test
    void wordsThatMerelyEndLikeRoadNamesAreNotAddresses() {
        for (String text : new String[]{
                "안내드린 내용을 확인하므로 3명에게 쿠폰 지급", "수량이 한정되므로 100명만 참여", "선착순 마감되므로 5일 안에 신청",
                "하는대로 3일 안에 신청", "원하는 대로 2개 선택", "안내한대로 5분 안에 접속", "순서대로 3명에게 지급",
                "차례대로 10명 추첨", "신청한 대로 5GB 지급", "가로로 3픽셀 늘려줘", "세로로 5칸 배치해줘", "옆으로 2칸 이동",
                "위로 3줄 올려줘", "처음으로 3번 클릭", "마지막으로 5명 추첨", "길 3개 안내", "먼 길 2시간 걸려도 참여",
                "이 길로 10분 가면 매장", "지름길 3분"}) {
            assertFalse(PrivacyDetector.types(text).contains("ADDRESS"), text);
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

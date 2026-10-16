package com.newvent.filtering;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.newvent.generation.service.RequestFilter;


// 개인정보 후보 탐지. 주소는 국내 도로명/지번의 명시적인 형태만 지원한다
public final class PrivacyDetector {
    private static final Pattern RESIDENT = Pattern.compile("(?<!\\d)\\d{6}[ \\-]?[1-8]\\d{6}(?!\\d)");
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:(?:01[016789]|02|0[3-6][1-5]|070)[ .\\-]?\\d{3,4}[ .\\-]?\\d{4}|\\+82[ .\\-]?(?:10|2|[3-6][1-5]|70)[ .\\-]?\\d{3,4}[ .\\-]?\\d{4})(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");
    // 주소는 "시·구 같은 지역 낱말 바로 뒤에 도로명·동 + 번호" 일 때만 본다.
    // 전에는 지역 낱말 없이 "~로 숫자", "~면 숫자" 만 보아서 "추첨으로 50명", "가입하면 3GB" 같은
    // 이벤트 문장이 전부 주소로 판정됐다(조사 "으로", 어미 "~하면" 의 끝 글자가 "로", "면" 이라서).
    /**
     * 이 낱말로 끝나는 "~시" 는 지역이 아니다 — 붙여 쓴 "구매시 · 참여시", 부사 "다시", 시간말 "당시 · 매시".
     * 지명 중 이 글자로 끝나는 시(안동시의 "동")는 일부러 넣지 않았다.
     */
    private static final String NOT_REGION_SI =
            "구매|참여|가입|신청|이용|응모|사용|결제|방문|접속|주문|등록|출석|지급|당첨|수령|확인"
            + "|다|당|수|매|일|임|정|한|상";
    /** 서울특별시 · 성남시 · 강남구 · 경기도 처럼 번호 앞에 오는 지역 낱말 */
    private static final String DISTRICT =
            "(?:[가-힣]{1,10}(?:특별시|광역시|특별자치시|특별자치도|(?<!" + NOT_REGION_SI + ")시|군|구)"
            + "|(?:경기|강원|충청[남북]|전라[남북]|경상[남북]|제주)도)\\s+";
    /** 도로명 + 번호. 조사 "으로" 는 도로명이 아니다 */
    // 이름 길이에 상한(20자)을 둔다. "+" 였을 때는 띄어쓰기 없이 한글이 길게 이어지는 입력에서
    // 길이의 제곱으로 느려졌다(5,000자 약 0.6초). 도로명·동 이름은 20자를 넘지 않는다.
    private static final String ROAD = "[가-힣A-Za-z0-9·]{1,20}(?:대로|(?<!으)로|길)\\s*\\d+(?:-\\d+)?";
    /** 동·읍·면·리 낱말의 끝. 동사 어미 "~하면 · ~오면 · ~쓰면 · ~사면" 의 "면" 은 제외한다 */
    private static final String EUP_MYEON_DONG_RI =
            "(?:동|읍|(?<![하이라으다려니오사보가쓰되주받먹입타자나])면|리)";
    /**
     * 시·군·구와 번지수 사이의 읍·면·동·리 단계 — "양평군 <b>서종면</b> 문호리 123", "신안군 <b>압해읍</b> 송공리 123".
     * 군 아래 면, 면 아래 리처럼 이어지므로 번지수 앞에 최대 둘까지 허용한다(PR #229 리뷰: 면 뒤의 리가 안 잡혔다).
     */
    private static final String MID = "(?:[가-힣]{1,20}" + EUP_MYEON_DONG_RI + "\\s+){0,2}";
    /** 동·읍·면·리 + 번지수 */
    private static final String LOT =
            "[가-힣]{1,20}" + EUP_MYEON_DONG_RI + "\\s+(?:산\\s*)?\\d+(?:-\\d+)?(?:번지)?";
    /**
     * 광역 약칭만 쓴 지번 주소 — "서울 역삼동 123-4", "경기 서종면 문호리 123". 시·구 없이도 이렇게 쓴다.
     * 약칭 뒤는 번지수가 <b>세 자리 이상이거나 "12-3" 꼴</b>일 때만 본다. "서울 이동 5분", "경기 활동 3회" 같은
     * 이벤트 문장은 한 자리 수라서 걸리지 않는다. 도로명은 이 경로에서 보지 않는다("서울 무료로 3GB" 와 구분이 안 된다).
     */
    private static final String SIDO_ABBR =
            "(?:서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)"
            + "(?:특별시|광역시|특별자치시|특별자치도|도)?\\s+";
    private static final String LOT_STRICT =
            "[가-힣]{1,20}" + EUP_MYEON_DONG_RI + "\\s+(?:산\\s*)?(?:\\d+-\\d+|\\d{3,})(?:번지)?";
    private static final Pattern ADDRESS = Pattern.compile(
            "(?:(?:" + DISTRICT + "){1,3}" + MID + "(?:" + ROAD + "|" + LOT + ")"
            + "|" + SIDO_ABBR + MID + LOT_STRICT
            // "번길" · "번지" 는 주소에만 쓰는 말이라 지역 낱말 없이도 판정한다
            + "|[가-힣A-Za-z0-9·]{1,20}(?:대로|로|길)\\s*\\d+번길\\s*\\d+"
            + "|[가-힣]{2,20}(?:동|읍|면|리)\\s+(?:산\\s*)?\\d+(?:-\\d+)?번지)(?!\\d)");
    private PrivacyDetector() {}
    public static List<String> types(String text) {
        String normalized = RequestFilter.clean(Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC));
        List<String> result = new ArrayList<>();
        if (EMAIL.matcher(normalized).find()) result.add("EMAIL");
        if (PHONE.matcher(normalized).find()) result.add("PHONE");
        if (ADDRESS.matcher(normalized).find()) result.add("ADDRESS");
        if (RESIDENT.matcher(normalized).find()) result.add("RESIDENT_NUMBER");
        return List.copyOf(result);
    }
    public static String question(List<String> types) {
        String labels = String.join(", ", types.stream().map(type -> switch (type) {
            case "EMAIL" -> "이메일";
            case "PHONE" -> "전화번호";
            case "ADDRESS" -> "주소";
            case "RESIDENT_NUMBER" -> "주민등록번호 형태";
            default -> "개인정보";
        }).toList());
        return labels + " 정보가 포함되어 있습니다. 해당 정보를 LLM에 전달하고 이벤트 페이지에 포함할 수 있습니다. 그대로 진행할까요?";
    }
}

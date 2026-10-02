package com.newvent.filtering;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.newvent.generation.service.RequestFilter;

/** 개인정보 후보 탐지. 주소는 국내 도로명/지번의 명시적인 형태만 지원한다. */
public final class PrivacyDetector {
    private static final Pattern RESIDENT = Pattern.compile("(?<!\\d)\\d{6}[ \\-]?[1-8]\\d{6}(?!\\d)");
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:(?:01[016789]|02|0[3-6][1-5]|070)[ .\\-]?\\d{3,4}[ .\\-]?\\d{4}|\\+82[ .\\-]?(?:10|2|[3-6][1-5]|70)[ .\\-]?\\d{3,4}[ .\\-]?\\d{4})(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");
    private static final Pattern ADDRESS = Pattern.compile(
            "(?:[가-힣A-Za-z0-9·]+(?:대로|로|길)\\s+\\d+(?:-\\d+)?"
            + "|[가-힣]+(?:동|읍|면|리)\\s+(?:산\\s*)?\\d+(?:-\\d+)?(?:번지)?)(?!\\d)");
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

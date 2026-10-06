package com.newvent.filtering;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.newvent.common.exception.code.ErrorCode;
import com.newvent.generation.service.GenerationJob;
import com.newvent.generation.service.GenerationJobStore;
import com.newvent.generation.service.RequestFilter;

/** 금지어/문체 판단은 하지 않는다. 개인정보 후보가 있으면 명시적 확인 전 LLM 호출을 보류한다. */
@Component
public class FilteringPolicy {
    public record Result(String text, ErrorCode error, String question,
                         String fingerprint, List<String> privacyTypes) {
        public static Result accepted(String text) {
            return new Result(text, null, null, null, List.of());
        }
        public static Result rejected(ErrorCode error) {
            return new Result("", error, null, null, List.of());
        }
    }
    public Result prepare(String raw, UUID previousId, Long eventId, String flow, Long versionId,
                          GenerationJobStore jobs, boolean privacyConfirmed, String title) {
        var invalid = RequestFilter.reject(raw);
        if (invalid.isPresent()) return Result.rejected(invalid.get());
        String cleaned = RequestFilter.clean(raw);
        GenerationJob prior = null;
        if (previousId != null) {
            prior = jobs.byId(previousId).orElse(null);
            if (prior == null || !eventId.equals(prior.eventId())
                    || prior.phase() != GenerationJob.Phase.ASK_BACK || !prior.privacyConfirmationRequired()
                    || prior.privacyRequest() == null
                    || !flow.equals(prior.privacyRequest().flow())
                    || prior.startedAt().isBefore(Instant.now().minus(Duration.ofMinutes(30)))) {
                return Result.rejected(FilteringErrorCode.PRIVACY_CONFIRMATION_NOT_FOUND);
            }
            if (!Objects.equals(versionId, prior.privacyRequest().baseVersionId())) {
                return Result.rejected(FilteringErrorCode.STALE_PRIVACY_CONFIRMATION);
            }
        }
        String text = cleaned;
        if (prior != null && prior.privacyConfirmationRequired() && !privacyConfirmed) {
            return Result.rejected(FilteringErrorCode.INVALID_PRIVACY_CONFIRMATION);
        }
        if (privacyConfirmed) {
            if (prior == null || !prior.privacyConfirmationRequired()
                    || !fingerprint(title, text).equals(prior.privacyFingerprint())) {
                return Result.rejected(FilteringErrorCode.INVALID_PRIVACY_CONFIRMATION);
            }
            return Result.accepted(text);
        }
        List<String> types = PrivacyDetector.types((title == null ? "" : title) + "\n" + text);
        if (!types.isEmpty()) {
            return new Result("", null, PrivacyDetector.question(types), fingerprint(title, text), types);
        }
        return Result.accepted(text);
    }
    /** 이벤트/경로/버전은 위에서 검사하며, 해시는 제목과 전체 요청을 연결한다. */
    private static String fingerprint(String title, String text) {
        String normalizedTitle = title == null ? "" : title;
        String payload = normalizedTitle.length() + ":" + normalizedTitle + text;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}

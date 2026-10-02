package com.newvent.filtering;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.newvent.generation.service.GenerationJob;
import com.newvent.generation.service.GenerationJobStore;

class FilteringPolicyTest {
    private final FilteringPolicy policy = new FilteringPolicy();
    private final GenerationJobStore jobs = new GenerationJobStore();
    private FilteringPolicy.Result prepare(String text) {
        return policy.prepare(text, null, 1L, "edit", 10L, jobs, false, "행사");
    }
    private GenerationJob pending(String text) {
        var result = prepare(text);
        var job = jobs.start(1L).orElseThrow();
        job.privacyRequest("edit", 10L);
        job.privacyConfirmation(result.fingerprint(), result.privacyTypes());
        job.askBack(result.question());
        jobs.finish(job);
        return job;
    }
    @Test
    void asksWithoutRetainingRawContact() {
        var result = prepare("연락처 a@example.com 010-1234-5678 넣어줘");
        assertNull(result.error());
        assertTrue(result.privacyTypes().contains("EMAIL"));
        assertTrue(result.privacyTypes().contains("PHONE"));
        assertNotNull(result.question());
        assertEquals("", result.text());
        assertFalse(result.question().contains("010-1234"));
    }
    @Test
    void onlyExplicitConfirmationOfSameRequestAllowsOriginal() {
        String raw = "a@example.com 넣어줘";
        var job = pending(raw);
        var accepted = policy.prepare(raw, job.jobId(), 1L, "edit", 10L, jobs, true, "행사");
        assertNull(accepted.error());
        assertNull(accepted.question());
        assertEquals(raw, accepted.text());
        assertEquals(FilteringErrorCode.INVALID_PRIVACY_CONFIRMATION,
                policy.prepare("b@example.com 넣어줘", job.jobId(), 1L, "edit", 10L, jobs, true, "행사").error());
        assertEquals(FilteringErrorCode.INVALID_PRIVACY_CONFIRMATION,
                policy.prepare(raw, job.jobId(), 1L, "edit", 10L, jobs, true, "바뀐 제목").error());
        assertEquals(FilteringErrorCode.INVALID_PRIVACY_CONFIRMATION,
                policy.prepare(raw, null, 1L, "edit", 10L, jobs, true, "행사").error());
        assertEquals(FilteringErrorCode.INVALID_PRIVACY_CONFIRMATION,
                policy.prepare("네", job.jobId(), 1L, "edit", 10L, jobs, false, "행사").error());
    }
    @Test
    void rejectsOtherEventFlowVersionAndUnknownJob() {
        var job = pending("a@example.com");
        assertEquals(FilteringErrorCode.PRIVACY_CONFIRMATION_NOT_FOUND,
                policy.prepare("a@example.com", job.jobId(), 2L, "edit", 10L, jobs, true, "행사").error());
        assertEquals(FilteringErrorCode.PRIVACY_CONFIRMATION_NOT_FOUND,
                policy.prepare("a@example.com", job.jobId(), 1L, "generate", 10L, jobs, true, "행사").error());
        assertEquals(FilteringErrorCode.STALE_PRIVACY_CONFIRMATION,
                policy.prepare("a@example.com", job.jobId(), 1L, "edit", 11L, jobs, true, "행사").error());
        assertEquals(FilteringErrorCode.PRIVACY_CONFIRMATION_NOT_FOUND,
                policy.prepare("a@example.com", UUID.randomUUID(), 1L, "edit", 10L, jobs, true, "행사").error());
    }
    @Test
    void rejectsExpiredConfirmation() {
        var store = mock(GenerationJobStore.class);
        var job = mock(GenerationJob.class);
        UUID id = UUID.randomUUID();
        when(store.byId(id)).thenReturn(Optional.of(job));
        when(job.eventId()).thenReturn(1L);
        when(job.phase()).thenReturn(GenerationJob.Phase.ASK_BACK);
        when(job.privacyRequest()).thenReturn(new GenerationJob.PrivacyRequest("edit", 10L));
        when(job.privacyConfirmationRequired()).thenReturn(true);
        when(job.startedAt()).thenReturn(Instant.now().minusSeconds(1801));
        assertEquals(FilteringErrorCode.PRIVACY_CONFIRMATION_NOT_FOUND,
                policy.prepare("a@example.com", id, 1L, "edit", 10L, store, true, "행사").error());
    }
    @Test
    void normalRouterQuestionIsNotAPrivacyConfirmation() {
        var job = jobs.start(1L).orElseThrow();
        job.askBack("어떤 제목인가요?");
        jobs.finish(job);
        var reply = policy.prepare("새 제목", job.jobId(), 1L, "edit", 10L, jobs, false, "행사");
        assertEquals(FilteringErrorCode.PRIVACY_CONFIRMATION_NOT_FOUND, reply.error());
        assertEquals("새 제목", prepare("새 제목").text());
    }

    @Test
    void original500CharacterLimitAppliesToInitialAndConfirmedRequests() {
        String atLimit = "a@example.com " + "가".repeat(486);
        assertEquals(500, atLimit.length());
        var job = pending(atLimit);
        var accepted = policy.prepare(atLimit, job.jobId(), 1L, "edit", 10L, jobs, true, "행사");
        assertNull(accepted.error());
        assertEquals(atLimit, accepted.text());
        assertEquals(com.newvent.generation.exception.GenerationErrorCode.REQUEST_TOO_LONG,
                prepare(atLimit + "나").error());
        assertEquals(com.newvent.generation.exception.GenerationErrorCode.REQUEST_TOO_LONG,
                policy.prepare(atLimit + "나", job.jobId(), 1L, "edit", 10L, jobs, true, "행사").error());
    }

    @Test
    void noProfanityOrStyleRulesAndNoAutomaticRedaction() {
        for (String text : new String[]{"씨발", "전체적으로 밝게 해줘", "무료 당첨 100%", "기간 늘려줘"}) {
            var result = prepare(text);
            assertNull(result.error());
            assertNull(result.question());
            assertEquals(text, result.text());
        }
        assertNull(prepare("연락처 없이 행사 소개만 써줘").question());
    }
    @Test
    void titleIsAlsoChecked() {
        var result = policy.prepare("행사 소개", null, 1L, "generate", null, jobs, false, "a@example.com 행사");
        assertTrue(result.privacyTypes().contains("EMAIL"));
        assertNotNull(result.question());
    }
}

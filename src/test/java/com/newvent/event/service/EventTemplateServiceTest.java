package com.newvent.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.event.domain.EventTemplate;
import com.newvent.event.dto.response.TemplateResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventTemplateRepository;

class EventTemplateServiceTest {

    private final EventTemplateRepository eventTemplateRepository = mock(EventTemplateRepository.class);
    private final EventTemplateService eventTemplateService = new EventTemplateService(eventTemplateRepository);

    @Test
    @DisplayName("활성 템플릿 5종을 헌진 키 순서로 반환한다")
    void 템플릿_목록_조회에_성공한다() {
        when(eventTemplateRepository.findAllActive()).thenReturn(List.of(
                EventTemplate.seed("sports_cheer", "스포츠 응원", "설명", "<html/>", true),
                EventTemplate.seed("holiday_gift", "한가위 선물", "설명", "<html/>", true),
                EventTemplate.seed("member_appreciation", "회원 감사", "설명", "<html/>", true),
                EventTemplate.seed("flash_sale", "72h 특가", "설명", "<html/>", true),
                EventTemplate.seed("pre_registration", "사전예약", "설명", "<html/>", true)));

        List<TemplateResponse> templates = eventTemplateService.findActiveTemplates();

        assertThat(templates).hasSize(5);
        assertThat(templates).extracting(TemplateResponse::templateKey)
                .containsExactly(
                        "sports_cheer",
                        "holiday_gift",
                        "member_appreciation",
                        "flash_sale",
                        "pre_registration");
        assertThat(templates).allMatch(TemplateResponse::active);
    }

    @Test
    @DisplayName("키로 단건 조회한다")
    void 템플릿_상세_조회에_성공한다() {
        when(eventTemplateRepository.findByKey("flash_sale")).thenReturn(
                Optional.of(EventTemplate.seed("flash_sale", "72h 특가", "설명", "<html/>", true)));

        TemplateResponse template = eventTemplateService.findByKey("flash_sale");

        assertThat(template.name()).isEqualTo("72h 특가");
        assertThat(template.theme()).isEqualTo("theme-sale");
    }

    @Test
    @DisplayName("없는 키는 EVENT404-1 이다")
    void 없는_템플릿은_404를_던진다() {
        when(eventTemplateRepository.findByKey("없는키")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventTemplateService.findByKey("없는키"))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.TEMPLATE_NOT_FOUND.getCode());
    }
}

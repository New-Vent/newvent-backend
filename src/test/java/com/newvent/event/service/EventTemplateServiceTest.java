package com.newvent.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.event.dto.TemplateResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.InMemoryEventTemplateRepository;

class EventTemplateServiceTest {

    private EventTemplateService eventTemplateService;

    @BeforeEach
    void setUp() {
        eventTemplateService = new EventTemplateService(new InMemoryEventTemplateRepository());
    }

    @Test
    @DisplayName("활성 템플릿 5종을 헌진 키 순서로 반환한다")
    void 템플릿_목록_조회에_성공한다() {
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
        TemplateResponse template = eventTemplateService.findByKey("flash_sale");

        assertThat(template.name()).isEqualTo("72h 특가");
        assertThat(template.theme()).isEqualTo("theme-sale");
    }

    @Test
    @DisplayName("없는 키는 EVENT404-1 이다")
    void 없는_템플릿은_404를_던진다() {
        assertThatThrownBy(() -> eventTemplateService.findByKey("없는키"))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.TEMPLATE_NOT_FOUND.getCode());
    }
}

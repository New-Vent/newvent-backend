package com.newvent.event.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.newvent.event.domain.EventTemplate;

/**
 * 이헌진 추천 템플릿 5종. JPA 전까지 메모리 고정.
 */
@Repository
public class InMemoryEventTemplateRepository implements EventTemplateRepository {

    private final Map<String, EventTemplate> templates = new LinkedHashMap<>();

    public InMemoryEventTemplateRepository() {
        seed();
    }

    @Override
    public List<EventTemplate> findAllActive() {
        return templates.values().stream()
                .filter(EventTemplate::active)
                .toList();
    }

    @Override
    public Optional<EventTemplate> findByKey(String templateKey) {
        return Optional.ofNullable(templates.get(templateKey));
    }

    private void seed() {
        save(new EventTemplate(
                "sports_cheer",
                "스포츠 응원",
                "월드컵 승부예측 투표와 스코어 맞추기. template_1_sports_cheer.html",
                "theme-sports",
                true));
        save(new EventTemplate(
                "holiday_gift",
                "한가위 선물",
                "전통 복주머니 뽑기형 명절 선물. template_2_holiday_gift.html",
                "theme-holiday",
                true));
        save(new EventTemplate(
                "member_appreciation",
                "회원 감사",
                "VIP 쿠폰·패스·기프티콘 바우처. template_3_member_appreciation.html",
                "theme-vip",
                true));
        save(new EventTemplate(
                "flash_sale",
                "72h 특가",
                "72시간 한정 플래시 세일. template_4_flash_sale.html",
                "theme-sale",
                true));
        save(new EventTemplate(
                "pre_registration",
                "사전예약",
                "런칭 마일스톤·휴대폰 인증 사전예약. template_5_pre_registration.html",
                "theme-launch",
                true));
    }

    private void save(EventTemplate template) {
        templates.put(template.templateKey(), template);
    }
}

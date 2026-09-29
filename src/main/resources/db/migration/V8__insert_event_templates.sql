-- 관리자 이벤트 JPA 전환 (feat/51-admin-event-jpa)
-- InMemoryEventTemplateRepository 에 있던 이헌진 추천 템플릿 5종을 실제 DB로 옮긴다.
-- html_content 는 아직 실제 템플릿 파일이 아니라 자리표시 값이다 (기존 인메모리 시드와 동일).

INSERT INTO event_templates (code, name, description, html_content, is_builtin, is_active) VALUES
('sports_cheer', '스포츠 응원', '월드컵 승부예측 투표와 스코어 맞추기. template_1_sports_cheer.html', '<!-- sports_cheer -->', TRUE, TRUE),
('holiday_gift', '한가위 선물', '전통 복주머니 뽑기형 명절 선물. template_2_holiday_gift.html', '<!-- holiday_gift -->', TRUE, TRUE),
('member_appreciation', '회원 감사', 'VIP 쿠폰·패스·기프티콘 바우처. template_3_member_appreciation.html', '<!-- member_appreciation -->', TRUE, TRUE),
('flash_sale', '72h 특가', '72시간 한정 플래시 세일. template_4_flash_sale.html', '<!-- flash_sale -->', TRUE, TRUE),
('pre_registration', '사전예약', '런칭 마일스톤·휴대폰 인증 사전예약. template_5_pre_registration.html', '<!-- pre_registration -->', TRUE, TRUE);

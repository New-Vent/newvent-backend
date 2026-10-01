-- 지원하는 참여 방식 목록
INSERT INTO games (code, name, description) VALUES
('BASIC', '기본형', '추가 입력 없이 참여'),
('SPORTS_PREDICTION', '스포츠 예측형', '예측값을 선택하여 참여'),
('PRE_REGISTRATION', '사전예약형', '전화번호를 입력하여 참여'),
('LUCKY_POUCH', '복주머니형', '복주머니를 선택하여 참여')
ON CONFLICT (code) DO NOTHING;
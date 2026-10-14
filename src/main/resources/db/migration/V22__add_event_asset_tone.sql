-- 사진 위 글자를 어떻게 할 것인가 (LIGHT · DARK · MIXED).
--
-- ★ 왜 컬럼이 필요한가 — 그릴 때는 못 정한다.
--   CSS 는 사진이 무슨 색인지 모르고, 모델은 그림을 아예 못 본다.
--   올릴 때 ImageProbe 가 한 번 재서 여기 박아 둔다.
--
-- ★ 기존 행은 MIXED 로 채운다. 이미 올라온 사진의 바이트를 다시 재러 가지 않는다 —
--   MIXED(흰 글씨 + 덮개)는 어떤 사진에서도 글자가 읽히는 쪽이라 안전한 기본값이다.
--   보기 좋게 하려면 그 이미지를 다시 올리면 된다.
alter table event_assets
    add column tone varchar(8) not null default 'MIXED';

alter table event_assets
    alter column tone drop default;

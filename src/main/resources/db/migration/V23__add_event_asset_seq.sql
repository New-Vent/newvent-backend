-- 이벤트별 고정 번호. 관리자가 "2번 이미지" 라고 말하는 그 번호다.
--
-- ★★ 목록 순서(i+1)로 번호를 매기면 1번을 지운 순간 2번이 1번이 된다.
--   "1번 이미지를 배경으로" 가 **다른 사진**을 가리키게 되고, 관리자는 그걸 모른다.
--   그래서 행에 박아 둔다. 번호는 올릴 때 한 번 정해지고 끝까지 안 바뀐다.
--
-- ★ 다시 쓰지 않는다. 지운 번호는 구멍으로 남는다 (1, 3, 4).
--   구멍은 눈에 보이지만, 번호가 밀리는 건 안 보인다.
alter table event_assets add column seq integer;

-- 기존 행에는 올린 순서대로 매긴다
update event_assets a
   set seq = r.rn
  from (select id, row_number() over (partition by event_id order by created_at, id) as rn
          from event_assets) r
 where a.id = r.id;

alter table event_assets alter column seq set not null;

-- ★ 한 이벤트에 같은 번호가 둘이면 "2번" 이 어느 쪽인지 알 수 없다. DB 가 막는다
create unique index ux_event_assets_event_seq on event_assets (event_id, seq);

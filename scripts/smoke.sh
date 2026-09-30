#!/usr/bin/env bash
#
# 프론트 없이 생성 → 미리보기 → 채팅 수정 한 바퀴.
#
#   bash scripts/smoke.sh                 템플릿 생성 (모델 안 부름)
#   bash scripts/smoke.sh blank           백지 생성 (모델 호출)
#
# 필요한 것
#   docker compose up -d
#   JWT_SECRET=... ./gradlew bootRun
#   jq
#
# 바꿔 쓸 수 있는 것
#   OUT=.smoke/b2         결과 폴더 (여러 번 돌릴 때 안 덮어쓰려고)
#   TEMPLATE=holiday_gift
#   REQUEST="..."         백지 생성 요청문
#   ASK="..."             채팅 수정 요청문
#   CSS_SRC=<event.css 경로>
#
# 결과물은 ./.smoke/ 에 떨어진다 (Git Bash 에서 /tmp 가 불편해서).
#
set -uo pipefail

HOST=${HOST:-http://localhost:8080}
MODE=${1:-template}
TEMPLATE=${TEMPLATE:-sports_cheer}
OUT=${OUT:-.smoke}

# ★ 혜택을 **둘** 적는다. 하나만 적으면 모델에게 거짓말을 시키게 된다.
#   BENEFITS.minItems = 2 인데 프롬프트는 "주어지지 않은 혜택을 만들어내지 마라" 다.
#   요청문에 혜택이 하나뿐이면 둘이 충돌하고, 실제로 그래서
#   1차 시도가 few_benefits 로 떨어진 뒤 재시도에서 모델이 없는 혜택을
#   지어내 통과했다("푸짐한 경품 추첨 기회"). 스모크가 그 상황을 만들면 안 된다.
#   혜택 하나짜리 요청을 어떻게 다룰지는 별도 결정 사항이다.
REQUEST=${REQUEST:-"여름 데이터 이벤트 페이지를 만들어줘. 혜택은 데이터 3GB 즉시 지급, 월 요금 30% 할인 두 가지야"}
ASK=${ASK:-혜택 문구를 더 짧게 다듬어줘}

say()  { printf '\n\033[1;36m── %s\033[0m\n' "$*"; }
info() { printf '   %s\n' "$*"; }
die()  { printf '\n\033[1;31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

command -v jq >/dev/null || die "jq 가 없습니다. 설치 후 셸을 새로 여세요"
mkdir -p "$OUT"

TOKEN=""

# ── 요청 한 번. 실패하면 본문을 그대로 보여주고 멈춘다 ──────────
# $1 메서드  $2 경로  $3 본문(없으면 "")  → 응답 본문을 stdout 으로
req() {
  local method=$1 path=$2 body=${3:-} tmp bodyfile code
  tmp=$(mktemp)
  local args=(-sS -o "$tmp" -w '%{http_code}' -X "$method" "$HOST$path")
  [ -n "$TOKEN" ] && args+=(-H "Authorization: Bearer $TOKEN")

  # ★ 본문을 파일로 써서 --data-binary 로 보낸다.
  #   -d 로 셸 문자열을 넘기면 Git Bash 콘솔 코드페이지를 타서 한글이 깨진다.
  #   깨진 바이트는 서버에서 HttpMessageNotReadableException → COMMON400-0 이다.
  if [ -n "$body" ]; then
    bodyfile=$(mktemp)
    printf '%s' "$body" > "$bodyfile"
    args+=(-H 'Content-Type: application/json; charset=UTF-8' --data-binary "@$bodyfile")
  fi

  code=$(curl "${args[@]}" 2>/dev/null) || {
    rm -f "$tmp" ${bodyfile:+"$bodyfile"}
    die "$method $path — 서버에 연결하지 못했습니다. bootRun 이 떠 있나요? ($HOST)"
  }
  rm -f ${bodyfile:+"$bodyfile"}

  if [ "$code" -ge 400 ]; then
    printf '\n\033[1;31m✗ %s %s → HTTP %s\033[0m\n' "$method" "$path" "$code" >&2
    [ -n "$body" ] && printf '  보낸 것 : %s\n' "$body" >&2
    printf '  받은 것 : ' >&2
    jq -c . <"$tmp" >&2 2>/dev/null || cat "$tmp" >&2
    printf '\n' >&2
    rm -f "$tmp"
    exit 1
  fi
  cat "$tmp"
  rm -f "$tmp"
}

# ── 1. 관리자 로그인 ───────────────────────────────────────────
say "관리자 로그인"
LOGIN=$(req POST /auth/admin/login '{"loginId":"admin","password":"admin1234!"}')
TOKEN=$(jq -r '.data.accessToken // empty' <<<"$LOGIN")
[ -n "$TOKEN" ] || die "accessToken 을 못 찾았습니다. 응답: $LOGIN"
info "토큰 확보"

# ── 2. 이벤트 만들기 ───────────────────────────────────────────
say "이벤트 생성"
# ★ Git Bash 의 date 는 GNU 다. macOS 는 BSD 라 -v 를 쓴다.
#   둘 다 실패하면 고정 날짜로 간다 — 여기서 스크립트가 죽으면 안 된다
START=$(date -u -d '+1 day'   '+%Y-%m-%dT00:00:00Z' 2>/dev/null \
     || date -u -v+1d         '+%Y-%m-%dT00:00:00Z' 2>/dev/null \
     || echo '2026-10-01T00:00:00Z')
END=$(date -u -d '+30 days'   '+%Y-%m-%dT23:59:59Z' 2>/dev/null \
   || date -u -v+30d          '+%Y-%m-%dT23:59:59Z' 2>/dev/null \
   || echo '2026-10-31T23:59:59Z')
info "기간 $START ~ $END"

# ★ JSON 은 jq 로 만든다. 손으로 따옴표를 이어 붙이면 요청문에 " 나 \ 가
#   들어간 순간 깨진 JSON 이 나가고, 서버는 COMMON400-0 만 돌려준다.
CREATED=$(req POST /api/admin/events \
  "$(jq -nc --arg n '스모크 이벤트' --arg s "$START" --arg e "$END" \
        '{name:$n, startAt:$s, endAt:$e, grade:"NORMAL"}')")
EVENT=$(jq -r '.data.id // empty' <<<"$CREATED")
[ -n "$EVENT" ] || die "eventId 를 못 찾았습니다. 응답: $CREATED"
info "eventId = $EVENT"

# ── 3. 생성 시작 ───────────────────────────────────────────────
if [ "$MODE" = blank ]; then
  say "백지 생성 (모델 호출)"
  info "요청문: $REQUEST"
  BODY=$(jq -nc --arg t "$REQUEST" '{requestText:$t}')
else
  say "템플릿 생성 ($TEMPLATE — 모델을 안 부른다)"
  BODY=$(jq -nc --arg c "$TEMPLATE" '{templateCode:$c}')
fi
JOB=$(jq -r '.data.jobId // empty' <<<"$(req POST "/api/admin/events/$EVENT/generate" "$BODY")")
[ -n "$JOB" ] || die "jobId 를 못 받았습니다"
info "jobId = $JOB"

# ── 폴링 ───────────────────────────────────────────────────────
# 마지막 phase 를 stdout 으로, 진행 상황은 stderr 로 (조합해서 쓰려고)
poll() {
  local job=$1 i r phase percent finished
  for i in $(seq 1 120); do
    r=$(req GET "/api/admin/events/$EVENT/generate/$job")
    phase=$(jq -r '.data.phase' <<<"$r")
    percent=$(jq -r '.data.percent' <<<"$r")
    finished=$(jq -r '.data.done' <<<"$r")
    printf '   %-12s %3s%%\n' "$phase" "$percent" >&2
    if [ "$finished" = true ]; then
      jq -r '"   message   : " + (.data.message // "(없음)")' <<<"$r" >&2
      jq -r '"   versionId : " + ((.data.versionId // "없음")|tostring)' <<<"$r" >&2
      printf '%s' "$phase"
      return 0
    fi
    sleep 0.5
  done
  die "120회 폴링해도 안 끝났습니다"
}

say "생성 진행"
RESULT=$(poll "$JOB")
[ "$RESULT" = DONE ] || die "생성이 $RESULT 로 끝났습니다"

# ── 4. 미리보기 ────────────────────────────────────────────────
say "미리보기 — 여기가 HTML 이다"
PREV=$(req GET "/api/admin/events/$EVENT/preview")
jq -r '"   versionId=\(.data.versionId)  versionNo=v\(.data.versionNo)  html \(.data.html|length)자"' <<<"$PREV"
jq -r '.data.html' <<<"$PREV" > "$OUT/before.html"
info "→ $OUT/before.html"

# ── 5. 채팅 수정 ───────────────────────────────────────────────
say "채팅 수정 — \"$ASK\""
EJOB=$(jq -r '.data.jobId // empty' <<<"$(req POST "/api/admin/events/$EVENT/edit" \
  "$(jq -nc --arg t "$ASK" '{requestText:$t}')")")
[ -n "$EJOB" ] || die "jobId 를 못 받았습니다. EditController 가 붙었나요?"
info "jobId = $EJOB"

ERESULT=$(poll "$EJOB")
case "$ERESULT" in
  DONE)     info "수정 성공" ;;
  ASK_BACK) info "되묻기 — 실패가 아닙니다. message 의 질문을 화면에 띄우면 됩니다" ;;
  FAILED)   info "실패 — 서버 소유 영역이거나 검증에 걸렸습니다" ;;
  *)        info "$ERESULT" ;;
esac

# ── 6. 다시 미리보기 ───────────────────────────────────────────
say "수정 후 미리보기"
AFTER=$(req GET "/api/admin/events/$EVENT/preview")
jq -r '"   versionId=\(.data.versionId)  versionNo=v\(.data.versionNo)"' <<<"$AFTER"
jq -r '.data.html' <<<"$AFTER" > "$OUT/after.html"
info "→ $OUT/after.html"

say "차이"
if diff -q "$OUT/before.html" "$OUT/after.html" >/dev/null; then
  info "(같습니다 — 수정이 저장되지 않았습니다)"
else
  diff <(fold -w120 "$OUT/before.html") <(fold -w120 "$OUT/after.html") | head -40
fi

# ── 7. 블록 확인 ───────────────────────────────────────────────
#
# ★ 눈으로 보기 전에 구조부터 본다. 화면이 허전할 때
#   "모델이 안 만든 것" 과 "CSS 가 안 걸린 것" 은 원인이 완전히 다르다.
say "블록 구성"
info "$(grep -o 'data-block="[a-z]*"' "$OUT/after.html" | sed 's/data-block=//;s/"//g' | tr '\n' ' ')"
info "래퍼: $(grep -o 'class="ev-container[^"]*"' "$OUT/after.html" | head -1)"

# ── 8. 브라우저용 껍데기 ───────────────────────────────────────
#
# ★ before/after.html 은 API 가 준 조각 그대로 둔다. 그게 진실이다.
#   저장되는 건 <body> 안쪽이고 스타일시트는 **페이지가** 로드하는 것이라,
#   조각만 열면 스타일이 안 붙는 게 정상이다.
#   view-*.html 은 "프론트가 씌울 껍데기" 를 흉내 내서 따로 만든다.
#
# ★ event.css 는 백엔드 레포에 없다. 프론트 레포에서 가져온다.
CSS_SRC=${CSS_SRC:-$HOME/git/newvent-frontend/public/assets/event.css}
if [ -f "$CSS_SRC" ]; then
  cp "$CSS_SRC" "$OUT/event.css"
else
  info "⚠ event.css 를 못 찾았습니다: $CSS_SRC"
  info "  CSS_SRC=<경로> 로 알려주면 스타일까지 입혀서 보여줍니다"
fi

view() {
  { printf '<!doctype html><html lang="ko"><head><meta charset="UTF-8">\n'
    printf '<meta name="viewport" content="width=device-width,initial-scale=1">\n'
    printf '<link rel="stylesheet" href="./event.css"></head><body>\n'
    cat "$OUT/$1.html"
    printf '\n</body></html>\n'
  } > "$OUT/view-$1.html"
}
view before
view after

say "브라우저로 보기"
info "start $OUT/view-after.html    # 스타일 입힌 것 — 프론트가 보여줄 모습"
info "start $OUT/view-before.html   # 수정 전"
info ""
info "$OUT/after.html 은 저장된 조각 그대로입니다 (스타일 없는 게 정상)"
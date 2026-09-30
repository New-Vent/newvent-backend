#!/usr/bin/env bash
#
# 프론트 없이 생성 → 미리보기 → 채팅 수정 한 바퀴.
#
#   bash scripts/smoke.sh                 템플릿 생성 (모델 안 부름)
#   bash scripts/smoke.sh blank           백지 생성 (mock 모델)
#
# 필요한 것
#   docker compose up -d
#   JWT_SECRET=... ./gradlew bootRun
#   jq
#
# 결과물은 ./.smoke/ 에 떨어진다 (Git Bash 에서 /tmp 가 불편해서).
#
set -uo pipefail

HOST=${HOST:-http://localhost:8080}
MODE=${1:-template}
TEMPLATE=${TEMPLATE:-template_1_sports_cheer}
OUT=${OUT:-.smoke}

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

CREATED=$(req POST /api/admin/events \
  "{\"name\":\"스모크 이벤트\",\"startAt\":\"$START\",\"endAt\":\"$END\",\"grade\":\"NORMAL\"}")
EVENT=$(jq -r '.data.id // empty' <<<"$CREATED")
[ -n "$EVENT" ] || die "eventId 를 못 찾았습니다. 응답: $CREATED"
info "eventId = $EVENT"

# ── 3. 생성 시작 ───────────────────────────────────────────────
if [ "$MODE" = blank ]; then
  say "백지 생성 (mock 모델 호출)"
  BODY='{"requestText":"여름 데이터 이벤트 페이지를 만들어줘. 혜택은 데이터 3GB 증정이야"}'
else
  say "템플릿 생성 ($TEMPLATE — 모델을 안 부른다)"
  BODY="{\"templateCode\":\"$TEMPLATE\"}"
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
ASK=${ASK:-혜택 문구를 더 짧게 다듬어줘}
say "채팅 수정 — \"$ASK\""
EJOB=$(jq -r '.data.jobId // empty' <<<"$(req POST "/api/admin/events/$EVENT/edit" \
  "{\"requestText\":\"$ASK\"}")")
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

say "브라우저로 보기"
info "start $OUT/after.html        # Git Bash · Windows"
info "open $OUT/after.html         # macOS"
info "xdg-open $OUT/after.html     # Linux"
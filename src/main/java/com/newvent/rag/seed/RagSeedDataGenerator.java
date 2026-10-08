package com.newvent.rag.seed;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.newvent.admin.domain.Admin;
import com.newvent.admin.repository.AdminRepository;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.rag.service.EmbeddingService;
import com.newvent.user.domain.MembershipGrade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RAG 시드 데이터 생성기. 개발·QA·시연용이다.
 *
 * 동작: definitions() 내용을 이벤트+버전으로 저장한 뒤 버전마다
 * embedding.reindex() 로 청크까지 적재한다. 제목 앞에 SEED: 를 붙여
 * 시드임을 표시한다 (운영 데이터와 눈으로 구분하기 위해).
 * prod 실행 금지는 RagSeedDataRunner 가 맡는다.
 *
 * 멱등: 시드 단위로 판단한다. 이미 있는 제목은 건너뛰고 없는 것만 적재한다.
 * 적재 중 하나라도 실패하면(예: 임베딩 호출 실패) 그 시드의 이벤트·버전을 지워서
 * 청크 없는 반쪽짜리 시드가 남지 않게 하고, 다음 실행 때 다시 시도된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSeedDataGenerator {

	/** EmbeddingService.isSeedData() 의 "SEED:" 판별과 맞춰야 한다. */
	static final String SEED_PREFIX = "SEED: ";

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final EventRepository events;
	private final EventVersionRepository versions;
	private final AdminRepository admins;
	private final EmbeddingService embedding;
	private final Environment environment;

	/**
	 * 시드 1건. title 앞에 SEED: 가 붙어 저장된다. html 은 data-block 섹션들로만 구성한다.
	 * grade 는 이벤트 노출 대상 등급이다. 지정하지 않으면 NORMAL.
	 */
	public record SeedDefinition(String title, String html, MembershipGrade grade) {

		public SeedDefinition(String title, String html) {
			this(title, html, MembershipGrade.NORMAL);
		}
	}

	/**
	 * 적재 결과. skipped=true 면 전부 이미 있어서 아무것도 안 한 것이다.
	 * (일부만 새로 적재했으면 skipped=false)
	 */
	public record SeedResult(int eventCount, int chunkCount, List<FailedSeed> failedSeeds,
			boolean skipped) {

		public record FailedSeed(String title, String error) {}
	}

	public SeedResult generate() {
		return generate(null);
	}

	/**
	 * 시드 적재. adminLoginId 가 있으면 그 관리자를 소유자로 쓰고, 없으면 id 첫 관리자.
	 * prod 차단은 Runner 와 별개로 여기서도 건다 — 직접 주입 호출 실수를 막기 위해.
	 */
	public SeedResult generate(String adminLoginId) {
		RagSeedDataRunner.rejectProdIfActive(environment.getActiveProfiles());
		Admin owner = adminLoginId != null
				? admins.findByLoginId(adminLoginId)
						.orElseThrow(() -> new IllegalStateException(
								"시드 적재용 관리자가 없다: " + adminLoginId))
				: admins.findAll(PageRequest.of(0, 1, Sort.by("id"))).stream().findFirst()
						.orElseThrow(() -> new IllegalStateException(
								"시드 적재용 관리자가 없다. 먼저 관리자를 만드세요."));

		OffsetDateTime now = OffsetDateTime.now(SEOUL);
		OffsetDateTime start = now.minusDays(7);
		OffsetDateTime end = now.plusDays(30);

		List<SeedResult.FailedSeed> failed = new ArrayList<>();
		int succeeded = 0;
		int existing = 0;
		int totalChunks = 0;

		for (SeedDefinition def : definitions()) {
			String title = SEED_PREFIX + def.title();
			if (events.existsByTitleStartingWith(title)) {
				existing++;
				continue;
			}

			Event event = null;
			try {
				event = events.save(Event.createDraft(owner, null, title, start, end, def.grade()));
				EventVersion version = versions.save(EventVersion.create(event, 1, def.html(), null));
				event.publish(version);
				event = events.save(event);
				totalChunks += embedding.reindex(event.getId(), version.getId());
				succeeded++;
			} catch (RuntimeException e) {
				log.error("RAG 시드 실패: title={}", def.title(), e);
				cleanup(event);
				failed.add(new SeedResult.FailedSeed(def.title(),
						e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			}
		}

		log.info("RAG 시드 완료 — 신규 {}건, 기존 {}건, 실패 {}건, 청크 {}개",
				succeeded, existing, failed.size(), totalChunks);
		return new SeedResult(succeeded, totalChunks, failed, succeeded == 0 && failed.isEmpty());
	}

	/**
	 * 실패한 시드의 이벤트를 지운다. 버전·청크는 FK ON DELETE CASCADE 에 맡긴다.
	 * 정리 자체가 실패해도 본 흐름은 막지 않고 로그만 남긴다.
	 */
	private void cleanup(Event event) {
		if (event == null || event.getId() == null) {
			return;
		}
		try {
			events.deleteById(event.getId());
		} catch (RuntimeException ex) {
			log.warn("RAG 시드 정리 실패 (수동 삭제 필요): eventId={}", event.getId(), ex);
		}
	}

	/** 전체 시드 정의: 템플릿 변형 14 + 실제형 10 + 엣지 5 = 29. */
	public static List<SeedDefinition> definitions() {
		List<SeedDefinition> out = new ArrayList<>();
		out.addAll(templateVariants());
		out.addAll(realistic());
		out.addAll(edgeCases());
		return out;
	}

	/** 템플릿 변형 14종. 템플릿별 대표 문구로 팔레트 조합을 흉내 낸다. */
	static List<SeedDefinition> templateVariants() {
		List<SeedDefinition> out = new ArrayList<>();
		out.add(new SeedDefinition("프로야구 승부예측 이벤트",
				page(sec("hero", h1("가을 야구 승부예측") + p("응원하는 팀의 승리를 예측하고 굿즈를 받으세요.")),
						sec("benefits", h2("승부예측 경품")
								+ ul("<li>1등 — 선수 사인 유니폼</li><li>2등 — 치킨 기프티콘</li>")),
						sec("steps", h2("참여 방법")
								+ ol("<li>로그인 후 응원 팀을 고르세요.</li><li>경기 시작 전까지 승패를 제출하세요.</li>")),
						cta("승부예측 참여하기"))));
		out.add(new SeedDefinition("축구 응원 스코어 맞히기",
				page(sec("hero", h1("스코어 맞히기 챌린지") + p("정확한 스코어를 맞힌 응원단에게 경품을 드립니다.")),
						sec("benefits", h2("스코어 적중 혜택")
								+ ul("<li>정답자 — 백화점 상품권 5만원</li><li>참여자 — 응원 스티콘</li>")),
						sec("steps", h2("참여 방법")
								+ ol("<li>경기 페이지에서 스코어를 입력하세요.</li><li>킥오프 전까지 제출하면 응모 완료입니다.</li>")),
						cta("스코어 제출하기"))));
		out.add(new SeedDefinition("시즌 응원 투표",
				page(sec("hero", h1("이달의 응원왕 투표") + p("가장 뜨거웠던 응원 메시지를 뽑아주세요.")),
						sec("highlight", p("투표 1위 응원단에게 단체 관람권 20매 증정")),
						cta("투표하러 가기"))));
		out.add(new SeedDefinition("한가위 출석체크 복주머니",
				page(sec("hero", h1("한가위 출석체크") + p("매일 출석하고 복주머니를 열어보세요.")),
						sec("benefits", h2("복주머니 혜택")
								+ ul("<li>데이터 1GB — 매일 선착순</li><li>편의점 상품권 — 랜덤 당첨</li>")),
						sec("steps", h2("출석체크 방법")
								+ ol("<li>이벤트 페이지에 접속하세요.</li><li>출석 버튼을 눌러 복주머니를 여세요.</li>")),
						cta("출석체크 하기"))));
		out.add(new SeedDefinition("설날 세뱃돈 뽑기",
				page(sec("hero", h1("설날 세뱃돈 뽑기") + p("새해 첫 행운을 뽑아보세요.")),
						sec("steps", h2("뽑기 방법")
								+ ol("<li>세뱃돈 봉투를 고르세요.</li><li>꽝 없는 뽑기라 모두가 받습니다.</li>")),
						cta("세뱃돈 뽑기"))));
		out.add(new SeedDefinition("추석 선물대전",
				page(sec("hero", h1("추석 선물대전") + p("감사의 마음을 담은 명절 선물 기획전입니다.")),
						sec("benefits", h2("명절 혜택")
								+ ul("<li>전 상품 무료배송</li><li>3만원 이상 구매 시 송편 세트</li>")),
						cta("선물 보러 가기"))));
		out.add(new SeedDefinition("최우수 등급 감사 쿠폰팩",
				page(sec("hero", h1("최우수 등급 감사 쿠폰팩") + p("최우수 등급 회원님께 드리는 전용 혜택입니다.")),
						sec("benefits", h2("최우수 쿠폰팩 구성")
								+ ul("<li>전 상품 20% 할인 쿠폰</li><li>무료배송 쿠폰 5매</li>")),
						sec("audience", h2("참여 대상 등급")
								+ ul("<li>최우수 등급 고객만 참여할 수 있습니다.</li>")),
						cta("쿠폰팩 받기")),
				MembershipGrade.BEST));
		out.add(new SeedDefinition("우수 등급 무료배송",
				page(sec("hero", h1("우수 등급 무료배송") + p("우수 등급은 조건 없이 무료배송됩니다.")),
						sec("benefits", h2("등급 혜택")
								+ ul("<li>상시 무료배송</li><li>생일 쿠폰 1만원권</li>")),
						cta("등급 확인하기")),
				MembershipGrade.EXCELLENT));
		out.add(new SeedDefinition("로열티 리워드",
				page(sec("hero", h1("로열티 리워드") + p("오래 함께한 회원님께 드리는 보상 프로그램입니다.")),
						sec("intro", h2("리워드 안내") + p("가입 연차에 따라 단계별 보상이 자동 지급됩니다.")),
						sec("benefits", h2("연차별 보상")
								+ ul("<li>1년차 — 데이터 1GB</li><li>3년차 — 상품권 3만원</li>")),
						cta("내 보상 보기"))));
		out.add(new SeedDefinition("72시간 타임딜 특가",
				page(sec("hero", h1("72시간 타임딜") + p("72시간 한정, 역대 최대 할인율로 판매합니다.")),
						sec("highlight", p("타임딜 마감 시간이 다가오면 조기 품절될 수 있습니다")),
						sec("benefits", h2("타임딜 상품")
								+ ul("<li>프리미엄 패딩 — 78% 할인</li><li>겨울 부츠 — 65% 할인</li>")),
						cta("타임딜 잡기"))));
		out.add(new SeedDefinition("선착순 클리어런스",
				page(sec("hero", h1("선착순 클리어런스") + p("재고 소진 시 종료되는 역시즌 클리어런스입니다.")),
						sec("benefits", h2("클리어런스 품목")
								+ ul("<li>여름 샌들 — 1만원 균일가</li><li>반팔 티셔츠 — 5천원 균일가</li>")),
						sec("steps", h2("구매 방법")
								+ ol("<li>상품을 장바구니에 담으세요.</li><li>재고가 있으면 결제가 확정됩니다.</li>")),
						cta("클리어런스 가기"))));
		out.add(new SeedDefinition("게릴라 반짝세일",
				page(sec("hero", h1("게릴라 반짝세일") + p("오늘 자정까지만 열리는 깜짝 세일입니다.")),
						cta("반짝세일 보기"))));
		out.add(new SeedDefinition("신규 서비스 사전예약",
				page(sec("hero", h1("신규 서비스 사전예약") + p("출시 전 미리 신청하고 얼리버드 혜택을 받으세요.")),
						sec("benefits", h2("사전예약 혜택")
								+ ul("<li>얼리버드 1만원 쿠폰</li><li>누적 신청 1만명 돌파 시 전원 데이터 2GB</li>")),
						sec("steps", h2("사전예약 방법")
								+ ol("<li>예약 버튼을 누르세요.</li><li>출시일에 알림을 보내드립니다.</li>")),
						cta("사전예약 하기"))));
		out.add(new SeedDefinition("얼리버드 대기자 모집",
				page(sec("hero", h1("얼리버드 대기자 모집") + p("가장 먼저 써볼 500명을 모집합니다.")),
						sec("steps", h2("대기 방법")
								+ ol("<li>대기 명단에 이메일을 등록하세요.</li><li>순번이 되면 초대장을 발송합니다.</li>")),
						sec("faq", h2("자주 묻는 질문")
								+ "<dl><dt>비용이 드나요?</dt><dd>베타 기간은 무료입니다.</dd></dl>"),
						cta("대기 등록하기"))));
		return out;
	}

	/** 실제형 10종. 운영에서 나올 법한 시나리오 그대로 쓴다. */
	static List<SeedDefinition> realistic() {
		List<SeedDefinition> out = new ArrayList<>();
		out.add(new SeedDefinition("여름 데이터 대방출",
				page(sec("hero", h1("여름 데이터 대방출") + p("신규 가입하면 데이터 3GB를 드립니다.")),
						sec("benefits", h2("신규 가입 혜택")
								+ ul("<li>데이터 3GB — 가입 즉시 지급</li><li>영상 통화 무료 100분</li>")),
						cta("지금 가입하기"))));
		out.add(new SeedDefinition("가을 할인 쿠폰 프로모션",
				page(sec("hero", h1("가을 할인 쿠폰") + p("가을맞이 전 상품 할인 쿠폰을 드립니다.")),
						sec("benefits", h2("할인 쿠폰 안내")
								+ ul("<li>장바구니 15% 할인 쿠폰</li><li>첫구매 5천원 할인 쿠폰</li>")),
						sec("steps", h2("쿠폰 받는 방법")
								+ ol("<li>쿠폰함에 쿠폰을 담으세요.</li><li>결제 단계에서 쿠폰을 적용하세요.</li>")),
						cta("쿠폰 받기"))));
		out.add(new SeedDefinition("연말 감사 경품 이벤트",
				page(sec("hero", h1("연말 감사 경품") + p("올 한 해 감사한 마음을 경품 응모로 전합니다.")),
						cta("경품 응모하기"))));
		out.add(new SeedDefinition("신년 무제한 요금제 세일",
				page(sec("hero", h1("신년 무제한 요금제") + p("데이터 무제한 요금제를 연중 최저가로 만나보세요.")),
						sec("benefits", h2("요금제 혜택")
								+ ul("<li>데이터 무제한 — 속도 제한 없음</li><li>가족 결합 시 추가 10% 할인</li>")),
						sec("compare", h2("요금제 비교표")
								+ "<table><tr><th>구분</th><th>기본형</th><th>무제한형</th></tr>"
								+ "<tr><td>데이터</td><td>10GB</td><td>무제한</td></tr>"
								+ "<tr><td>월 요금</td><td>33,000원</td><td>55,000원</td></tr></table>"),
						cta("요금제 변경하기"))));
		out.add(new SeedDefinition("봄맞이 축제",
				page(sec("hero", h1("봄맞이 축제") + p("봄나들이 준비물을 한자리에 모았습니다.")),
						sec("benefits", h2("축제 혜택")
								+ ul("<li>피크닉 세트 20% 할인</li><li>포토존 인증 시 음료 쿠폰</li>")),
						sec("steps", h2("축제 참여 방법")
								+ ol("<li>축제장에 방문하세요.</li><li>스탬프 3개를 모아 경품에 응모하세요.</li>")),
						cta("축제 안내 보기"))));
		out.add(new SeedDefinition("블랙프라이데이 빅세일",
				page(sec("hero", h1("블랙프라이데이") + p("1년 중 가장 큰 할인, 단 하루의 기회입니다.")),
						sec("benefits", h2("빅세일 품목")
								+ ul("<li>가전 — 최대 50% 할인</li><li>패션 — 최대 70% 할인</li>")),
						sec("steps", h2("세일 참여 방법")
								+ ol("<li>0시에 오픈되는 특가 페이지에 접속하세요.</li>"
										+ "<li>장바구니 쿠폰을 먼저 챙기세요.</li>")),
						cta("빅세일 입장"))));
		out.add(new SeedDefinition("신학기 준비 패키지",
				page(sec("hero", h1("신학기 준비") + p("새 학기 필수품 패키지로 가볍게 시작하세요.")),
						sec("benefits", h2("패키지 구성")
								+ ul("<li>노트북 파우치 — 사은품 증정</li><li>문구 세트 — 30% 할인</li>")),
						sec("steps", h2("주문 방법")
								+ ol("<li>학년별 패키지를 고르세요.</li><li>개강 전까지 배송됩니다.</li>")),
						sec("faq", h2("자주 묻는 질문")
								+ "<dl><dt>배송은 언제 오나요?</dt><dd>결제 후 2일 안에 출발합니다.</dd></dl>"),
						cta("패키지 담기"))));
		out.add(new SeedDefinition("여름 워터페스티벌",
				page(sec("hero", h1("여름 워터페스티벌") + p("도심 속 물놀이 축제에 초대합니다.")),
						sec("benefits", h2("페스티벌 혜택")
								+ ul("<li>얼리버드 티켓 30% 할인</li><li>우천 시 전액 환불</li>")),
						sec("steps", h2("예매 방법")
								+ ol("<li>날짜를 골라 티켓을 예매하세요.</li><li>현장에서 QR로 입장합니다.</li>")),
						cta("티켓 예매하기"))));
		out.add(new SeedDefinition("블랙프라이데이 플래시딜",
				page(sec("hero", h1("플래시딜 특가") + p("매시간 바뀌는 한정 수량 플래시딜입니다.")),
						sec("highlight", p("이번 시간 특가: 무선 이어폰 59% 할인")),
						sec("benefits", h2("플래시딜 상품")
								+ ul("<li>무선 이어폰 — 59% 할인</li><li>스마트워치 — 45% 할인</li>")),
						cta("플래시딜 보기"))));
		out.add(new SeedDefinition("신년 카운트다운",
				page(sec("hero", h1("신년 카운트다운") + p("새해 첫 순간을 함께 세어보세요.")),
						sec("steps", h2("카운트다운 참여 방법")
								+ ol("<li>12월 31일 밤 페이지를 여세요.</li><li>자정에 새해 소원을 적으면 응모됩니다.</li>")),
						cta("알림 신청하기"))));
		return out;
	}

	/** 엣지 케이스 5종. 긴 텍스트·특수문자·최소 구성·다국어·중첩 구조를 검증한다. */
	static List<SeedDefinition> edgeCases() {
		List<SeedDefinition> out = new ArrayList<>();
		StringBuilder longHero = new StringBuilder();
		longHero.append("이 이벤트는 안내문이 매우 깁니다. ");
		while (longHero.length() < 900) {
			longHero.append("긴 안내문은 여러 청크로 나뉘어 저장되는지 확인하기 위한 문장입니다. ");
		}
		out.add(new SeedDefinition("초긴 안내문 이벤트",
				page(sec("hero", h1("초긴 안내문") + p(longHero.toString())),
						cta("확인했습니다"))));
		out.add(new SeedDefinition("특수문자 대잔치",
				page(sec("hero", h1("특수문자 대잔치") + p("이모지와 기호가 섞인 혜택 안내입니다.")),
						sec("benefits", h2("기호 혜택 ★♪")
								+ ul("<li>🎉 첫구매 50% 할인!</li><li>💯 리뷰 작성 시 100% 적립 ★</li>")),
						cta("받으러 가기 ▶"))));
		out.add(new SeedDefinition("최소 구성 이벤트",
				page(sec("hero", h1("최소 구성") + p("제목과 버튼만 있는 이벤트입니다.")),
						cta("참여"))));
		out.add(new SeedDefinition("Welcome Global Event",
				page(sec("hero", h1("Welcome Global Event")
						+ p("New members get 3GB of free data upon signup.")),
						sec("benefits", h2("Member benefits")
								+ ul("<li>3GB free data</li><li>Free shipping coupon</li>")),
						cta("Join now"))));
		out.add(new SeedDefinition("요금제 비교표 이벤트",
				page(sec("hero", h1("요금제 비교") + p("두 요금제를 나란히 비교해 보세요.")),
						sec("compare", h2("요금제 비교표")
								+ "<div class=\"compare-wrap\"><div class=\"compare-inner\">"
								+ "<table><tr><th>구분</th><th>A형</th><th>B형</th></tr>"
								+ "<tr><td>데이터</td><td>5GB</td><td>무제한</td></tr>"
								+ "<tr><td>통화</td><td>100분</td><td>무제한</td></tr>"
								+ "<tr><td>월 요금</td><td>25,000원</td><td>49,000원</td></tr></table>"
								+ "</div></div>"),
						cta("비교 후 가입하기"))));
		return out;
	}

	// ── HTML 조립 헬퍼 ──

	static String page(String... sections) {
		StringBuilder sb = new StringBuilder("<div class=\"ev-container event-page\">");
		for (String s : sections) {
			sb.append(s);
		}
		return sb.append("</div>").toString();
	}

	/** page() 의 마지막 인자로 등급까지 넘기기 위한 오버로드가 필요 없도록, 등급은 SeedDefinition 쪽에서 받는다. */
	private static SeedDefinition withGrade(String title, String html, MembershipGrade grade) {
		return new SeedDefinition(title, html, grade);
	}

	static String sec(String block, String inner) {
		return "<section data-block=\"" + block + "\">" + inner + "</section>";
	}

	/** CTA 블록. event.css 의 [data-block="cta"] .btn 스타일이 적용되는 형태. */
	static String cta(String label) {
		return sec("cta", "<a class=\"btn\" href=\"#\">" + label + "</a>");
	}

	static String h1(String text) {
		return "<h1>" + text + "</h1>";
	}

	static String h2(String text) {
		return "<h2>" + text + "</h2>";
	}

	static String p(String text) {
		return "<p>" + text + "</p>";
	}

	static String ul(String items) {
		return "<ul>" + items + "</ul>";
	}

	static String ol(String items) {
		return "<ol>" + items + "</ol>";
	}
}

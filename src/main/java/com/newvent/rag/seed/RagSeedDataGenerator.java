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
 * 멱등: 시드 단위로 판단한다. 삭제되지 않은 같은 제목이 있으면 건너뛰고 없는 것만 적재한다.
 * 휴지통(soft delete)에 있는 것은 없는 것으로 보고 다시 만든다.
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
		return generate(null, false);
	}

	public SeedResult generate(String adminLoginId) {
		return generate(adminLoginId, false);
	}

	/**
	 * 시드 적재. adminLoginId 가 있으면 그 관리자를 소유자로 쓰고, 없으면 id 첫 관리자.
	 * prod 차단은 Runner 와 별개로 여기서도 건다 — 직접 주입 호출 실수를 막기 위해.
	 * allowProd=true 는 시연 등 시간 제한 예외용. 남용 금지.
	 */
	public SeedResult generate(String adminLoginId, boolean allowProd) {
		RagSeedDataRunner.rejectProdIfActive(environment.getActiveProfiles(), allowProd);
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
			if (events.existsByTitleStartingWithAndDeletedAtIsNull(title)) {
				existing++;
				continue;
			}

			Event event = null;
			try {
				event = events.save(Event.createDraft(owner, null, title, start, end, def.grade()));
				EventVersion version = versions.save(EventVersion.create(event, 1, def.html(), null,  null));
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

	/** 전체 시드 정의: 스포츠 7 + 시즌 9 + 멤버십 10 + 특가 8 + 출시 6 = 40.
	 * 카테고리마다 다른 테마(theme-*)를 써서 미리보기가 겹치지 않게 한다.
	 * 문구는 통신사 멤버십 커머스 말투로 통일한다 (자연스러운 한국어, 직역체 금지). */
	public static List<SeedDefinition> definitions() {
		List<SeedDefinition> out = new ArrayList<>();
		out.addAll(sports());
		out.addAll(season());
		out.addAll(membership());
		out.addAll(sale());
		out.addAll(launch());
		return out;
	}

	/** 스포츠 응원 7종 (theme-sports). */
	static List<SeedDefinition> sports() {
		List<SeedDefinition> out = new ArrayList<>();
		out.add(new SeedDefinition("프로야구 승부예측 이벤트",
				page("theme-sports",
						sec("hero", h1("가을 야구 승부예측") + badge("매 경기 참여 가능") + p("응원하는 팀의 승리를 예측하고 굿즈를 받으세요.")),

						sec("highlight", p("이번 주 경기부터 참여됩니다. 경기 시작 1시간 전에 마감됩니다")),
						sec("benefits", h2("승부예측 경품")
								+ ul("<li>1등 — 선수 사인 유니폼</li><li>2등 — 치킨 기프티콘</li>")),
						sec("steps", h2("참여 방법")
								+ ol("<li>로그인 후 응원 팀을 고르세요.</li><li>경기 시작 전까지 승패를 제출하세요.</li>")),
						cta("승부예측 참여하기"))));
		out.add(new SeedDefinition("축구 응원 스코어 맞히기",
				page("theme-sports",
						sec("hero", h1("스코어 맞히기 챌린지") + badge("적중하면 경품") + p("정확한 스코어를 맞힌 응원단에게 경품을 드립니다.")),

						sec("highlight", p("킥오프 전까지 제출된 예측만 인정됩니다")),
						sec("benefits", h2("스코어 적중 혜택")
								+ ul("<li>정답자 — 백화점 상품권 5만원</li><li>참여자 — 응원 스티콘</li>")),
						sec("steps", h2("참여 방법")
								+ ol("<li>경기 페이지에서 스코어를 입력하세요.</li><li>킥오프 전까지 제출하면 응모 완료입니다.</li>")),
						cta("스코어 제출하기"))));
		out.add(new SeedDefinition("이달의 응원왕 투표",
				page("theme-sports",
						sec("hero", h1("이달의 응원왕 투표") + badge("팬 투표 진행 중") + p("가장 뜨거웠던 응원 메시지를 뽑아주세요.")),
						sec("highlight", p("투표 1위 응원단에게 단체 관람권 20매 증정")),
						sec("steps", h2("투표 방법")
								+ ol("<li>마음에 드는 응원 메시지를 고르세요.</li><li>하루 한 번 투표할 수 있습니다.</li>")),
						cta("투표하러 가기"))));
		out.add(new SeedDefinition("농구 3점슛 챌린지",
				page("theme-sports",
						sec("hero", h1("농구 3점슛 챌린지") + badge("매 라운드 도전") + p("3점슛 성공 횟수를 예측하고 경품에 도전하세요.")),

						sec("highlight", p("매 라운드 새로 시작됩니다. 지난 기록은 이월되지 않습니다")),
						sec("benefits", h2("챌린지 경품")
								+ ul("<li>전 경기 적중 — 농구화 상품권</li><li>참여자 — 음료 쿠폰</li>")),
						sec("faq", h2("자주 묻는 질문")
								+ "<dl><dt>연장전 기록도 포함되나요?</dt><dd>정규 시간 기록만 인정됩니다.</dd></dl>"),
						cta("챌린지 참여하기"))));
		out.add(new SeedDefinition("e스포츠 승자 예측",
				page("theme-sports",
						sec("hero", h1("e스포츠 승자 예측") + badge("결승전 한정") + p("결승전 승자를 맞힌 분께 게이밍 기어를 드립니다.")),

						sec("highlight", p("결승전 당일에만 참여됩니다")),
						sec("benefits", h2("예측 혜택")
								+ ul("<li>우승팀 적중 — 헤드셋</li><li>스코어 적중 — 게임 아이템 쿠폰</li>")),
						sec("steps", h2("참여 방법")
								+ ol("<li>대진표에서 승자를 고르세요.</li><li>결승 시작 전까지 제출하세요.</li>")),
						cta("승자 예측하기"))));
		out.add(new SeedDefinition("마라톤 응원 메시지",
				page("theme-sports",
						sec("hero", h1("마라톤 응원 메시지") + badge("선착순 500명") + p("뛰는 분들에게 응원 메시지를 남겨주세요.")),
						sec("highlight", p("채택된 응원 메시지 100건에 커피 쿠폰 증정")),
						sec("steps", h2("참여 방법")
								+ ol("<li>응원할 선수를 고르세요.</li><li>100자 이내로 메시지를 적어주세요.</li>")),
						cta("응원 남기기"))));
		out.add(new SeedDefinition("야구 올스타 투표",
				page("theme-sports",
						sec("hero", h1("야구 올스타 투표") + badge("전 포지션 투표") + p("올해의 올스타는 팬 여러분이 뽑습니다.")),

						sec("highlight", p("하루 한 번 투표됩니다. 전 포지션을 다 고르셔야 완료됩니다")),
						sec("benefits", h2("투표 혜택")
								+ ul("<li>전 포지션 투표 완료 — 치킨 쿠폰 응모권</li><li>적중한 라인업 — 사인볼 추첨</li>")),
						cta("투표하기"))));
		return out;
	}

	/** 시즌·명절 9종 (theme-holiday). */
	static List<SeedDefinition> season() {
		List<SeedDefinition> out = new ArrayList<>();
		out.add(new SeedDefinition("한가위 출석체크 복주머니",
				page("theme-holiday",
						sec("hero", h1("한가위 출석체크") + badge("명절 한정") + p("매일 출석하고 복주머니를 열어보세요.")),
						sec("steps", h2("출석체크 방법")
								+ ol("<li>이벤트 페이지에 접속하세요.</li><li>출석 버튼을 눌러 복주머니를 여세요.</li>")),
						sec("benefits", h2("복주머니 혜택")
								+ ul("<li>데이터 1GB — 매일 선착순</li><li>편의점 상품권 — 랜덤 당첨</li>")),
						cta("출석체크 하기"))));
		out.add(new SeedDefinition("설날 세뱃돈 뽑기",
				page("theme-holiday",
						sec("hero", h1("설날 세뱃돈 뽑기") + badge("설 연휴 한정") + p("새해 첫 행운을 뽑아보세요.")),
						sec("steps", h2("뽑기 방법")
								+ ol("<li>세뱃돈 봉투를 고르세요.</li><li>당첨 결과는 바로 확인됩니다.</li>")),
						sec("benefits", h2("세뱃돈 구성")
								+ ul("<li>특급 — 데이터 10GB</li><li>전원 — 커피 쿠폰 (꽝 없음)</li>")),
						cta("세뱃돈 뽑기"))));
		out.add(new SeedDefinition("추석 선물대전",
				page("theme-holiday",
						sec("hero", h1("추석 선물대전") + badge("추석 한정") + p("감사의 마음을 담은 명절 선물 기획전입니다.")),

						sec("steps", h2("선물하는 방법") + ol("<li>마음에 드는 선물을 고르세요.</li><li>배송일을 확인하고 주문하세요.</li>")),
						sec("benefits", h2("명절 혜택")
								+ ul("<li>전 상품 무료배송</li><li>3만원 이상 구매 시 송편 세트</li>")),
						sec("faq", h2("자주 묻는 질문")
								+ "<dl><dt>배송 마감이 언제인가요?</dt><dd>연휴 3일 전까지 주문하시면 명절 전에 받으십니다.</dd></dl>"),
						cta("선물 보러 가기"))));
		out.add(new SeedDefinition("크리스마스 데이터 선물",
				page("theme-holiday",
						sec("hero", h1("크리스마스 데이터 선물") + badge("12월 25일 단 하루") + p("소중한 사람에게 데이터를 선물하세요.")),
						sec("steps", h2("선물 방법")
								+ ol("<li>받을 사람의 번호를 입력하세요.</li><li>25일 자정에 자동 발송됩니다.</li>")),
						sec("benefits", h2("선물 혜택")
								+ ul("<li>데이터 선물 2GB — 크리스마스 당일 지급</li><li>선물 포장 메시지 무료</li>")),
						cta("선물하기"))));
		out.add(new SeedDefinition("연말 감사 인사 이벤트",
				page("theme-holiday",
						sec("hero", h1("연말 감사 인사") + badge("12월 한 달간") + p("올 한 해 함께해 주셔서 감사합니다.")),
						sec("highlight", p("12월 한 달간 멤버십 전원 데이터 5GB 추가 지급")),

						sec("steps", h2("혜택 받는 방법") + ol("<li>12월에 앱에 접속하세요.</li><li>데이터는 자동으로 들어옵니다.</li>")),
						sec("benefits", h2("감사 혜택")
								+ ul("<li>데이터 5GB — 자동 지급</li><li>제휴 카페 연말 쿠폰북</li>")),
						cta("혜택 확인하기"))));
		out.add(new SeedDefinition("신년 카운트다운",
				page("theme-holiday",
						sec("hero", h1("신년 카운트다운") + badge("12월 31일 밤") + p("새해 첫 순간을 함께 세어보세요.")),
						sec("steps", h2("카운트다운 참여 방법")
								+ ol("<li>12월 31일 밤 페이지를 여세요.</li><li>자정에 새해 소원을 적으면 응모됩니다.</li>")),
						sec("faq", h2("자주 묻는 질문")
								+ "<dl><dt>소원은 어디에 쓰나요?</dt><dd>자정에 열리는 소원 입력창에 적으시면 됩니다.</dd></dl>"),
						cta("알림 신청하기"))));
		out.add(new SeedDefinition("봄맞이 축제",
				page("theme-holiday",
						sec("hero", h1("봄맞이 축제") + badge("봄 한정") + p("봄나들이 준비물을 한자리에 모았습니다.")),
						sec("steps", h2("축제 참여 방법")
								+ ol("<li>축제장에 방문하세요.</li><li>스탬프 3개를 모아 경품에 응모하세요.</li>")),
						sec("benefits", h2("축제 혜택")
								+ ul("<li>피크닉 세트 20% 할인</li><li>포토존 인증 시 음료 쿠폰</li>")),
						cta("축제 안내 보기"))));
		out.add(new SeedDefinition("설 연휴 로밍 혜택",
				page("theme-holiday",
						sec("hero", h1("설 연휴 로밍 혜택") + badge("연휴 한정") + p("해외에 계신 가족과 부담 없이 통화하세요.")),
						sec("steps", h2("신청 방법")
								+ ol("<li>출국 전에 로밍을 신청하세요.</li><li>현지 도착 문자로 자동 개통됩니다.</li>")),
						sec("benefits", h2("로밍 혜택")
								+ ul("<li>주요 20개국 데이터 로밍 반값</li><li>영상통화 60분 무료</li>")),
						cta("로밍 신청하기"))));
		out.add(new SeedDefinition("어린이날 가족 이벤트",
				page("theme-holiday",
						sec("hero", h1("어린이날 가족 이벤트") + badge("5월 5일 단 하루") + p("아이와 함께하는 5월의 하루를 준비했습니다.")),

						sec("steps", h2("참여 방법") + ol("<li>가족 결합 회선을 확인하세요.</li><li>5월 5일 전에 신청하세요.</li>")),
						sec("benefits", h2("가족 혜택")
								+ ul("<li>키즈 카페 제휴 할인</li><li>가족 결합 회선 데이터 공유 2배</li>")),
						sec("faq", h2("자주 묻는 질문")
								+ "<dl><dt>누가 참여할 수 있나요?</dt><dd>미성년 자녀가 있는 가족 결합 회원이면 됩니다.</dd></dl>"),
						cta("참여 신청하기"))));
		return out;
	}

	/** 멤버십·요금제 10종 (theme-vip). 등급 지정이 필요한 것만 grade 를 둔다. */
	static List<SeedDefinition> membership() {
		List<SeedDefinition> out = new ArrayList<>();
		out.add(new SeedDefinition("최우수 등급 감사 쿠폰팩",
				page("theme-vip",
						sec("hero", h1("최우수 등급 감사 쿠폰팩") + badge("최우수 등급 전용") + p("최우수 등급 회원님께 드리는 전용 혜택입니다.")),
						sec("audience", h2("참여 대상 등급")
								+ ul("<li>최우수 등급 고객만 참여할 수 있습니다.</li>")),
						sec("benefits", h2("최우수 쿠폰팩 구성")
								+ ul("<li>전 상품 20% 할인 쿠폰</li><li>무료배송 쿠폰 5매</li>")),
						cta("쿠폰팩 받기")),
				MembershipGrade.BEST));
		out.add(new SeedDefinition("우수 등급 무료배송",
				page("theme-vip",
						sec("hero", h1("우수 등급 무료배송") + badge("우수 등급 전용") + p("우수 등급은 조건 없이 무료배송됩니다.")),

						sec("audience", h2("참여 대상 등급") + ul("<li>우수 등급 고객만 참여할 수 있습니다.</li>")),
						sec("benefits", h2("등급 혜택")
								+ ul("<li>상시 무료배송</li><li>생일 쿠폰 1만원권</li>")),
						sec("faq", h2("자주 묻는 질문")
								+ "<dl><dt>도서산간도 되나요?</dt><dd>제주·도서산간은 2,500원이 추가됩니다.</dd></dl>"),
						cta("등급 확인하기")),
				MembershipGrade.EXCELLENT));
		out.add(new SeedDefinition("로열티 리워드",
				page("theme-vip",
						sec("hero", h1("로열티 리워드") + badge("연차별 자동 지급") + p("오래 함께한 회원님께 드리는 보상 프로그램입니다.")),
						sec("intro", h2("리워드 안내") + p("가입 연차에 따라 단계별 보상이 자동 지급됩니다.")),
						sec("benefits", h2("연차별 보상")
								+ ul("<li>1년차 — 데이터 1GB</li><li>3년차 — 상품권 3만원</li><li>5년차 — 단말 교체 지원금</li>")),
						cta("내 보상 보기"))));
		out.add(new SeedDefinition("여름 데이터 대방출",
				page("theme-vip",
						sec("hero", h1("여름 데이터 대방출") + badge("신규 가입 한정") + p("신규 가입하면 데이터 3GB를 드립니다.")),

						sec("intro", h2("신규 가입 안내") + p("처음 가입하시는 분께 드리는 혜택입니다. 개통과 동시에 자동 적용됩니다.")),
						sec("benefits", h2("신규 가입 혜택")
								+ ul("<li>데이터 3GB — 가입 즉시 지급</li><li>영상 통화 무료 100분</li>")),
						sec("steps", h2("가입 방법")
								+ ol("<li>본인 인증을 완료하세요.</li><li>요금제를 고르면 개통됩니다.</li>")),
						cta("지금 가입하기"))));
		out.add(new SeedDefinition("신년 무제한 요금제 세일",
				page("theme-vip",
						sec("hero", h1("신년 무제한 요금제") + badge("연중 최저가") + p("데이터 무제한 요금제를 연중 최저가로 만나보세요.")),
						sec("compare", h2("요금제 비교표")
								+ "<table><tr><th>구분</th><th>기본형</th><th>무제한형</th></tr>"
								+ "<tr><td>데이터</td><td>10GB</td><td>무제한</td></tr>"
								+ "<tr><td>월 요금</td><td>33,000원</td><td>55,000원</td></tr></table>"),
						sec("benefits", h2("요금제 혜택")
								+ ul("<li>데이터 무제한 — 속도 제한 없음</li><li>가족 결합 시 추가 10% 할인</li>")),
						sec("faq", h2("자주 묻는 질문")
								+ "<dl><dt>약정이 있나요?</dt><dd>무약정이라 위약금 없이 바꾸실 수 있습니다.</dd></dl>"),
						cta("요금제 변경하기"))));
		out.add(new SeedDefinition("데이터 충전소",
				page("theme-vip",
						sec("hero", h1("데이터 충전소") + badge("필요한 만큼만") + p("모자란 데이터만 골라서 충전하세요.")),

						sec("intro", h2("충전소 안내") + p("약정 없이 필요한 만큼만 충전하는 방식입니다. 남은 데이터는 다음 달로 넘어가지 않습니다.")),
						sec("benefits", h2("충전 혜택")
								+ ul("<li>1GB — 3,900원</li><li>5GB — 15,000원 (5천원 할인)</li>")),
						sec("steps", h2("충전 방법")
								+ ol("<li>필요한 용량을 고르세요.</li><li>결제 즉시 충전됩니다.</li>")),
						cta("충전하기"))));
		out.add(new SeedDefinition("장기가입 감사 프로그램",
				page("theme-vip",
						sec("hero", h1("장기가입 감사 프로그램") + badge("5년 이상 한정") + p("5년 이상 함께하신 분께 드리는 특별 혜택입니다.")),
						sec("intro", h2("꼭 확인해주세요")
								+ p("이 혜택은 가입 기간 5년 이상인 회선에만 적용됩니다. "
										+ "일시정지 기간은 가입 기간에서 제외됩니다. "
										+ "지원금은 단말 할부금에서 차감되는 방식으로 지급됩니다. "
										+ "가족 결합 회선은 대표 회선 1개만 대상이 되며 나머지 회선은 일반 혜택이 적용됩니다. "
										+ "혜택 적용 중에 회선을 해지하면 남은 지원금은 소멸되고 위약금이 따로 청구되지 않습니다. "
										+ "단말 교체 지원금은 출고가 기준이 아니라 실구매가 기준으로 산정됩니다. "
										+ "자급제 단말로 개통한 경우에도 동일하게 적용됩니다. "
										+ "생일 데이터는 생일이 속한 달 1일 자정에 지급되고 다음 달 1일 자정에 소멸됩니다. "
										+ "미사용 데이터는 이월되지 않으며 현금으로 바꿀 수 없습니다. "
										+ "전용 상담 창구는 평일 오전 9시부터 오후 6시까지 운영되고 주말과 공휴일은 쉽니다. "
										+ "상담 예약은 앱에서 하시면 되고 대기 없이 바로 연결됩니다. "
										+ "이 프로그램은 회사 사정에 따라 바뀔 수 있으며 바뀌기 30일 전에 미리 알려드립니다. "
										+ "이미 받은 혜택은 바뀌어도 그대로 유지됩니다. "
										+ "궁금한 점은 전용 창구로 문의해 주세요. "
										+ "안내문은 길지만 혜택을 받으시는 데 지장은 없습니다. "
										+ "천천히 읽어보시고 모르는 부분만 상담 창구에 물어보시면 됩니다. "
										+ "지원금은 매월 할부금에서 나눠 차감되고 중도에 기기를 바꾸셔도 남은 금액은 이어집니다. "
										+ "다만 회선을 다른 통신사로 옮기시면 남은 지원금은 소멸됩니다. "
										+ "생일 데이터는 함께 쓰는 가족에게 나눠줄 수 없고 본인 회선에서만 씁니다. "
										+ "데이터를 다 쓰면 속도가 느려지지만 추가 요금은 붙지 않습니다. "
										+ "지원금 신청은 개통일로부터 60일 안에 하셔야 하며 기간이 지나면 받을 수 없습니다. "
										+ "신청은 앱이나 매장에서 하실 수 있고 서류는 별도로 필요하지 않습니다. "
										+ "지원 대상 여부는 신청 다음 날 문자로 알려드리며 앱에서도 확인할 수 있습니다. "
										+ "명단에서 빠진 것 같으면 전용 창구에 문의하시면 다시 확인해 드립니다. "
										+ "이 안내를 끝까지 읽어주셔서 감사합니다.")),
						sec("benefits", h2("감사 혜택")
								+ ul("<li>매년 생일 데이터 10GB</li><li>단말 교체 지원금 10만원</li><li>전용 상담 창구 운영</li>")),
						cta("대상 확인하기"))));
		out.add(new SeedDefinition("생일 축하 쿠폰",
				page("theme-vip",
						sec("hero", h1("생일 축하 쿠폰") + badge("생일월 한정") + p("생일인 회원님께 작은 선물을 드립니다.")),

						sec("intro", h2("생일 혜택 안내") + p("생일이 속한 달에만 받을 수 있는 혜택입니다. 쿠폰함에서 직접 꺼내 쓰세요.")),
						sec("benefits", h2("생일 혜택")
								+ ul("<li>케이크 교환권 1매</li><li>데이터 2GB (당월 한정)</li>")),
						sec("steps", h2("받는 방법")
								+ ol("<li>생일월에 쿠폰함에서 확인하세요.</li><li>한 달 안에 쓰셔야 합니다.</li>")),
						cta("쿠폰함 열기"))));
		out.add(new SeedDefinition("친구 초대 리워드",
				page("theme-vip",
						sec("hero", h1("친구 초대 리워드") + badge("초대할수록 적립") + p("친구를 초대하면 둘 다 데이터를 받습니다.")),

						sec("intro", h2("초대 안내") + p("친구가 개통해야 적립됩니다. 초대 링크로 들어온 신규 개통만 인정됩니다.")),
						sec("benefits", h2("초대 혜택")
								+ ul("<li>초대 1명당 각 1GB</li><li>월 최대 10GB까지 적립</li>")),
						sec("steps", h2("초대 방법")
								+ ol("<li>내 초대 링크를 공유하세요.</li><li>친구가 개통하면 자동 적립됩니다.</li>")),
						sec("faq", h2("자주 묻는 질문")
								+ "<dl><dt>기존 회원 초대도 되나요?</dt><dd>신규 개통에만 적용됩니다.</dd>"
								+ "<dt>적립 한도가 있나요?</dt><dd>월 10GB까지 적립됩니다.</dd>"
								+ "<dt>데이터는 언제 들어오나요?</dt><dd>개통 확인 다음 날 자정에 들어옵니다.</dd></dl>"),
						cta("초대 링크 복사"))));
		out.add(new SeedDefinition("멤버십 데이",
				page("theme-vip",
						sec("hero", h1("멤버십 데이") + badge("매달 마지막 수요일") + p("매달 마지막 수요일은 멤버십 데이입니다.")),

						sec("intro", h2("멤버십 데이 안내") + p("매달 마지막 수요일 하루만 열리는 혜택입니다. 당일에 앱에서 확인하세요.")),
						sec("benefits", h2("데이 혜택")
								+ ul("<li>제휴 카페 전 메뉴 30% 할인</li><li>영화 예매 1+1 쿠폰</li>")),
						cta("이번 달 혜택 보기"))));
		return out;
	}

	/** 특가·할인 8종 (theme-sale). */
	static List<SeedDefinition> sale() {
		List<SeedDefinition> out = new ArrayList<>();
		out.add(new SeedDefinition("72시간 타임딜 특가",
				page("theme-sale",
						sec("hero", h1("72시간 타임딜") + badge("72시간 한정") + p("72시간 한정, 역대 최대 할인율로 판매합니다.")),
						sec("highlight", p("타임딜 마감 시간이 다가오면 조기 품절될 수 있습니다")),
						sec("benefits", h2("타임딜 상품")
								+ ul("<li>프리미엄 패딩 — 78% 할인</li><li>겨울 부츠 — 65% 할인</li>")),
						sec("steps", h2("구매 방법")
								+ ol("<li>상품을 장바구니에 담으세요.</li><li>72시간 안에 결제하세요.</li>")),
						cta("타임딜 잡기"))));
		out.add(new SeedDefinition("선착순 클리어런스",
				page("theme-sale",
						sec("hero", h1("선착순 클리어런스") + badge("재고 소진 시 종료") + p("재고 소진 시 종료되는 역시즌 클리어런스입니다.")),

						sec("highlight", p("재고가 떨어지면 예고 없이 끝납니다")),
						sec("benefits", h2("클리어런스 품목")
								+ ul("<li>여름 샌들 — 1만원 균일가</li><li>반팔 티셔츠 — 5천원 균일가</li>")),
						sec("steps", h2("구매 방법")
								+ ol("<li>상품을 장바구니에 담으세요.</li><li>재고가 있으면 결제가 확정됩니다.</li>")),
						cta("클리어런스 가기"))));
		out.add(new SeedDefinition("게릴라 반짝세일",
				page("theme-sale",
						sec("hero", h1("게릴라 반짝세일") + badge("오늘 자정까지") + p("오늘 자정까지만 열리는 깜짝 세일입니다.")),
						sec("highlight", p("게릴라 세일은 예고 없이 열리고 예고 없이 끝납니다")),
						sec("benefits", h2("반짝 상품")
								+ ul("<li>무선 충전기 — 49% 할인</li><li>블루투스 스피커 — 39% 할인</li>")),
						cta("반짝세일 보기"))));
		out.add(new SeedDefinition("가을 할인 쿠폰 프로모션",
				page("theme-sale",
						sec("hero", h1("가을 할인 쿠폰") + badge("가을 한정") + p("가을맞이 전 상품 할인 쿠폰을 드립니다.")),

						sec("highlight", p("가을 기획전 기간에만 쓸 수 있습니다")),
						sec("benefits", h2("할인 쿠폰 안내")
								+ ul("<li>장바구니 15% 할인 쿠폰</li><li>첫구매 5천원 할인 쿠폰</li>")),
						sec("steps", h2("쿠폰 받는 방법")
								+ ol("<li>쿠폰함에 쿠폰을 담으세요.</li><li>결제 단계에서 쿠폰을 적용하세요.</li>")),
						cta("쿠폰 받기"))));
		out.add(new SeedDefinition("블랙프라이데이 빅세일",
				page("theme-sale",
						sec("hero", h1("블랙프라이데이") + badge("단 하루") + p("1년 중 가장 큰 할인, 단 하루의 기회입니다.")),
						sec("highlight", p("0시 오픈 — 인기 상품은 10분 안에 동납니다")),
						sec("benefits", h2("빅세일 품목")
								+ ul("<li>가전 — 최대 50% 할인</li><li>패션 — 최대 70% 할인</li>")),
						sec("steps", h2("세일 참여 방법")
								+ ol("<li>0시에 오픈되는 특가 페이지에 접속하세요.</li>"
										+ "<li>장바구니 쿠폰을 먼저 챙기세요.</li>")),
						cta("빅세일 입장"))));
		out.add(new SeedDefinition("신학기 준비 패키지",
				page("theme-sale",
						sec("hero", h1("신학기 준비") + badge("개강 전 한정") + p("새 학기 필수품 패키지로 가볍게 시작하세요.")),

						sec("highlight", p("개강 전까지만 판매합니다")),
						sec("benefits", h2("패키지 구성")
								+ ul("<li>노트북 파우치 — 사은품 증정</li><li>문구 세트 — 30% 할인</li>")),
						sec("steps", h2("주문 방법")
								+ ol("<li>학년별 패키지를 고르세요.</li><li>개강 전까지 배송됩니다.</li>")),
						sec("faq", h2("자주 묻는 질문")
								+ "<dl><dt>배송은 언제 오나요?</dt><dd>결제 후 2일 안에 출발합니다.</dd></dl>"),
						cta("패키지 담기"))));
		out.add(new SeedDefinition("여름 워터페스티벌",
				page("theme-sale",
						sec("hero", h1("여름 워터페스티벌") + badge("얼리버드 한정") + p("도심 속 물놀이 축제에 초대합니다.")),

						sec("highlight", p("얼리버드 티켓은 한정 수량입니다")),
						sec("benefits", h2("페스티벌 혜택")
								+ ul("<li>얼리버드 티켓 30% 할인</li><li>우천 시 전액 환불</li>")),
						sec("steps", h2("예매 방법")
								+ ol("<li>날짜를 골라 티켓을 예매하세요.</li><li>현장에서 QR로 입장합니다.</li>")),
						sec("faq", h2("자주 묻는 질문")
								+ "<dl><dt>우천 시 어떻게 되나요?</dt><dd>기상특보 발효 시 전액 환불됩니다.</dd></dl>"),
						cta("티켓 예매하기"))));
		out.add(new SeedDefinition("블랙프라이데이 플래시딜",
				page("theme-sale",
						sec("hero", h1("플래시딜 특가") + badge("매시간 갱신") + p("매시간 바뀌는 한정 수량 플래시딜입니다.")),
						sec("highlight", p("이번 시간 특가: 무선 이어폰 59% 할인")),
						sec("benefits", h2("플래시딜 상품")
								+ ul("<li>무선 이어폰 — 59% 할인</li><li>스마트워치 — 45% 할인</li>")),
						sec("steps", h2("참여 방법")
								+ ol("<li>시간별 상품을 확인하세요.</li><li>수량 소진 전에 결제하세요.</li>")),
						cta("플래시딜 보기"))));
		return out;
	}

	/** 출시·예약 6종 (theme-launch). */
	static List<SeedDefinition> launch() {
		List<SeedDefinition> out = new ArrayList<>();
		out.add(new SeedDefinition("신규 서비스 사전예약",
				page("theme-launch",
						sec("hero", h1("신규 서비스 사전예약") + badge("사전예약 중") + p("출시 전 미리 신청하고 얼리버드 혜택을 받으세요.")),

						sec("intro", h2("출시 안내") + p("출시일이 정해지면 예약하신 분께 가장 먼저 알려드립니다. 출시는 9월 중입니다.")),
						sec("benefits", h2("사전예약 혜택")
								+ ul("<li>얼리버드 1만원 쿠폰</li><li>누적 신청 1만명 돌파 시 전원 데이터 2GB</li>")),
						sec("steps", h2("사전예약 방법")
								+ ol("<li>예약 버튼을 누르세요.</li><li>출시일에 알림을 보내드립니다.</li>")),
						cta("사전예약 하기"))));
		out.add(new SeedDefinition("얼리버드 대기자 모집",
				page("theme-launch",
						sec("hero", h1("얼리버드 대기자 모집") + badge("가장 먼저 체험") + p("가장 먼저 써볼 500명을 모집합니다.")),

						sec("intro", h2("베타 안내") + p("베타 기간은 무료입니다. 500명이 차면 대기는 마감됩니다.")),
						sec("steps", h2("대기 방법")
								+ ol("<li>대기 명단에 이메일을 등록하세요.</li><li>순번이 되면 초대장을 발송합니다.</li>")),
						sec("faq", h2("자주 묻는 질문")
								+ "<dl><dt>비용이 드나요?</dt><dd>베타 기간은 무료입니다.</dd>"
								+ "<dt>언제 시작하나요?</dt><dd>9월 중 순차적으로 안내드립니다.</dd>"
								+ "<dt>기기 제한이 있나요?</dt><dd>안드로이드 12 이상, iOS 17 이상에서 됩니다.</dd></dl>"),
						cta("대기 등록하기"))));
		out.add(new SeedDefinition("신규 요금제 출시",
				page("theme-launch",
						sec("hero", h1("신규 요금제 출시") + badge("신규 출시") + p("라이트 유저를 위한 가벼운 요금제가 나왔습니다.")),

						sec("intro", h2("신규 요금제 안내") + p("데이터를 가볍게 쓰는 분을 위한 요금제입니다. 기존 요금제에서 바꿀 때 위약금은 없습니다.")),
						sec("benefits", h2("신규 요금제 혜택")
								+ ul("<li>월 5GB — 9,900원</li><li>소진 시 저속 무제한</li>")),
						sec("compare", h2("기존 요금제와 비교")
								+ "<table><tr><th>구분</th><th>라이트</th><th>기본형</th></tr>"
								+ "<tr><td>데이터</td><td>5GB</td><td>10GB</td></tr>"
								+ "<tr><td>월 요금</td><td>9,900원</td><td>33,000원</td></tr></table>"),
						cta("요금제 바꾸기"))));
		out.add(new SeedDefinition("앱 리뉴얼 기념 이벤트",
				page("theme-launch",
						sec("hero", h1("앱 리뉴얼 기념") + badge("업데이트 기념") + p("새로워진 앱을 써보시고 의견을 들려주세요.")),

						sec("intro", h2("리뉴얼 안내") + p("앱을 최신 버전으로 올리셔야 새 화면이 보입니다. 이전 버전에서는 일부 기능이 안 열립니다.")),
						sec("benefits", h2("리뉴얼 혜택")
								+ ul("<li>리뷰 작성 시 데이터 1GB</li><li>신규 기능 체험 미션 완료 시 쿠폰</li>")),
						sec("steps", h2("참여 방법")
								+ ol("<li>앱을 최신 버전으로 업데이트하세요.</li><li>체험 미션을 확인하세요.</li>")),
						cta("앱 업데이트하기"))));
		out.add(new SeedDefinition("5G 커버리지 확대 기념",
				page("theme-launch",
						sec("hero", h1("5G 커버리지 확대") + badge("신규 개통 한정") + p("전국 85개 도시에서 5G가 더 빨라졌습니다.")),
						sec("intro", h2("확대 안내") + p("이번 달 새로 뚫린 지역에서도 기존 요금 그대로 씁니다.")),
						sec("benefits", h2("기념 혜택")
								+ ul("<li>속도 체험 이벤트 응모권</li><li>신규 개통 시 단말 할인 추가</li>")),
						cta("커버리지 확인하기"))));
		out.add(new SeedDefinition("키즈 요금제 출시",
				page("theme-launch",
						sec("hero", h1("키즈 요금제 출시") + badge("만 12세 이하 한정") + p("아이 첫 휴대폰에 딱 맞는 요금제입니다.")),

						sec("intro", h2("키즈 요금제 안내") + p("만 12세 이하만 가입됩니다. 보호자 동의가 필요합니다.")),
						sec("benefits", h2("키즈 요금제 혜택")
								+ ul("<li>월 3GB + 안심 차단 — 12,000원</li><li>위치 알림 무료</li>")),
						sec("faq", h2("자주 묻는 질문")
								+ "<dl><dt>성인도 가입되나요?</dt><dd>만 12세 이하만 가입됩니다.</dd></dl>"),
						cta("가입 상담하기"))));
		return out;
	}

	// ── HTML 조립 헬퍼 ──

	static String page(String theme, String... sections) {
		StringBuilder sb = new StringBuilder("<div class=\"ev-container event-page " + theme + "\">");
		for (String s : sections) {
			sb.append(s);
		}
		return sb.append("</div>").toString();
	}

	static String sec(String block, String inner) {
		return "<section data-block=\"" + block + "\">" + inner + "</section>";
	}

	/** hero용 뱃지. 전 테마 hero .badge 스타일이 적용된다. 블록 구조는 그대로라 청킹에 영향 없음. */
	static String badge(String text) {
		return "<span class=\"badge\">" + text + "</span>";
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

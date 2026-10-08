package com.newvent.rag.seed;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.newvent.rag.seed.RagSeedDataGenerator.SeedResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 시드 적재 실행기. --seed-rag-data 인자가 있을 때만 돈다.
 *
 * 실행: ./gradlew seedRagData  (args 에 --seed-rag-data 를 넣어 부팅한다)
 * ★ prod 프로파일에서는 시작 자체를 막는다. 실수 방지가 본체보다 중요하다.
 * ★ 적재가 끝나면 컨텍스트를 닫고 나간다. 서버로 띄워 두는 용도가 아니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagSeedDataRunner implements ApplicationRunner {

	private final ApplicationContext context;
	private final Environment environment;
	private final RagSeedDataGenerator generator;

	@Override
	public void run(ApplicationArguments args) {
		if (!args.containsOption("seed-rag-data")) {
			return;
		}
		rejectProdIfActive(environment.getActiveProfiles());
		SeedResult result = generator.generate(seedAdminLoginId(args));
		if (result.skipped()) {
			log.info("RAG 시드 완료: 이미 적재됨 — 건너뜀");
		} else {
			log.info("RAG 시드 완료: 이벤트 {}건, 청크 {}건, 실패 {}건",
					result.eventCount(), result.chunkCount(), result.failedSeeds().size());
		}
		SpringApplication.exit(context, () -> 0);
	}

	static void rejectProdIfActive(String[] activeProfiles) {
		for (String profile : activeProfiles) {
			if ("prod".equalsIgnoreCase(profile)) {
				throw new IllegalStateException("RAG 시드는 prod 에서 실행할 수 없다");
			}
		}
	}

	/** --seed-admin=loginId 값을 읽는다. 없으면 null (id 첫 관리자 사용). */
	static String seedAdminLoginId(ApplicationArguments args) {
		List<String> values = args.getOptionValues("seed-admin");
		return (values == null || values.isEmpty()) ? null : values.get(0);
	}
}

package com.newvent.infra.llm;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * provider=bedrock 일 때 <b>기동 시점에</b> AWS 자격증명이 있는지 본다.
 *
 *   왜 필요한가
 *   이게 없으면 자격증명이 없어도 앱은 정상으로 뜬다(컨테이너도 healthy 다).
 *   실패는 관리자가 실제로 생성을 누를 때 오고, 그때 관리자에게 가는 문구는
 *   "페이지 생성 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요." 다 —
 *   설정 누락인데 일시적 장애로 읽힌다. 원인을 짚은 문장은 로그에만 남는다.
 *   게다가 재시도가 돌면서 llm_call_logs 에 실패 행이 쌓여 하루 상한을 깎는다.
 *   호출은 못 했는데 예산은 줄어든다.
 *
 *   왜 BedrockClient 생성자가 아닌가
 *   LlmConfigTest · LlmClientLifecycleTest 가 BedrockClient 를 직접 만든다.
 *   생성자에서 검사하면 자격증명이 없는 CI 에서 그 테스트들이 깨진다.
 *   여기는 스프링 컨텍스트가 뜰 때만 도므로 단위 테스트는 지나간다.
 *
 *   네트워크 호출이 아니다 — 호출 권한을 확인하는 게 아니라 "키가 있나" 만 본다.
 *   키가 틀렸거나 모델 권한이 없으면 첫 호출에서 걸린다. 그건 여기서 알 수 없다.
 *   다만 자격증명 체인의 마지막이 IMDS 라, 아무 자격증명도 없는 환경에서는
 *   실패 판정까지 몇 초 걸린다. 실패 경로에서만 생기는 지연이라 둔다.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "bedrock")
public class BedrockCredentialCheck {

    private static final Logger log = LoggerFactory.getLogger(BedrockCredentialCheck.class);

    @PostConstruct
    void check() {
        try (DefaultCredentialsProvider provider = DefaultCredentialsProvider.create()) {
            provider.resolveCredentials();
            log.info("Bedrock 자격증명 확인됨. (권한·모델 접근은 첫 호출에서 확인된다)");
        } catch (SdkException e) {
            throw new IllegalStateException(
                    "llm.provider=bedrock 인데 AWS 자격증명을 찾지 못했습니다. "
                    + "컨테이너면 .env 의 AWS_ACCESS_KEY_ID·AWS_SECRET_ACCESS_KEY 를, "
                    + "호스트면 AWS_PROFILE 또는 ~/.aws/credentials 를 확인하세요. "
                    + "LLM 이 필요 없는 작업이면 LLM_PROVIDER 를 지우면 mock 으로 돕니다.", e);
        }
    }
}

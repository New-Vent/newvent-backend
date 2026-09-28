package com.newvent.common.config;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// @SpringBootApplication 클래스에 직접 붙이면 @WebMvcTest 같은 슬라이스 테스트에서도 적용돼서 JPA metamodel이 없다는 이유로 컨텍스트 로딩이 깨진다 (jpaMappingContext 생성 실패).
// 그래서 별도 설정 클래스로 분리
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}

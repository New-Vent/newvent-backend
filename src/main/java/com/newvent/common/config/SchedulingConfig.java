package com.newvent.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러·비동기 스위치. 풀 크기는 코드가 아니라 application.yaml 의
 * spring.task.scheduling.* / spring.task.execution.* 에서 잡는다.
 * (TaskScheduler 빈을 직접 등록하면 Boot 자동 설정이 물러나서 yaml 이 무시된다)
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}

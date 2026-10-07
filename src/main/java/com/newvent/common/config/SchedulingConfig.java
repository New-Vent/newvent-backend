package com.newvent.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulingConfig {

	/**
	 * 스케줄러 공용 풀. 기본값(스레드 1)이면 야간 재색인 같은 장시간 작업이
	 * 다른 스케줄 작업(알림·만료 등)을 끝날 때까지 막는다. 여유 있게 잡는다.
	 */
	@Bean
	public TaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(4);
		scheduler.setThreadNamePrefix("scheduled-");
		scheduler.initialize();
		return scheduler;
	}
}

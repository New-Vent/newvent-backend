package com.newvent.common.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시간 기준점을 하나로 모으는 설정
 * 
 * 필요한 이유
 * 	- countToday()의 "오늘 자정" 계산이 서버 시간대에 흔들리면 안됨.
 * 	  Asia/Seoul 고정 Clock을 빈으로 두면 전역에서 같은 기준 사용
 *  - 테스트에서 가짜 시간(Clock.fixed)을 주입하는 통로. 실제 시간에 의존하면 자정 경계 테스트가 특정 시각에만 통과하는 사태 발생
 */
@Configuration
public class TimeConfig {

	public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	
	@Bean
	public Clock clock() {
		return Clock.system(SEOUL);
	}
}

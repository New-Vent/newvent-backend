package com.newvent.auth.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.newvent.auth.service.RefreshTokenService;

import lombok.extern.slf4j.Slf4j;

/** 만료된 Refresh Token 을 하루에 한 번 지운다. cron 은 auth.cleanup.cron 으로 바꿀 수 있다. */
@Slf4j
@Component
public class RefreshTokenCleanupJob {

    private final RefreshTokenService refreshTokens;

    public RefreshTokenCleanupJob(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Scheduled(cron = "${auth.cleanup.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void run() {
        int deleted = refreshTokens.purgeExpired();
        log.info("만료된 Refresh Token {}건 삭제", deleted);
    }
}

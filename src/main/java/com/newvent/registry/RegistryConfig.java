package com.newvent.registry;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Configuration;

/**
 * 레지스트리가 자기모순 없이 정의됐는지 시작할 때 한 번 본다.
 *
 * ★ 어긋난 채로 뜨면 통과율이 0 이 되는데 원인을 못 찾는다.
 *   그 자리에서 죽는 게 훨씬 낫다.
 *
 * shape 를 시켰으면 must 도 있어야 하고,
 * SERVER 블록에는 shape 가 있으면 안 되고,
 * minItems 를 세려면 must 가 필요하다 — 전부 Block.assertConsistent() 안에 있다.
 */
@Configuration
public class RegistryConfig {

    @PostConstruct
    void 레지스트리_정합성_검사() {
        Block.assertConsistent();
    }
}

# Newvent Backend

AI 기반 이벤트 페이지 생성 및 게시·참여 관리 서비스의 백엔드 프로젝트입니다.

## 기술 스택

* Java 21
* Spring Boot 4.1.1
* Gradle
* PostgreSQL
* Ollama

## 주요 기능

* 관리자 및 사용자 인증
* 이벤트 생성·수정·게시 관리
* LLM 기반 이벤트 페이지 생성
* 채팅 기반 이벤트 수정 및 버전 관리
* 공개 이벤트 조회 및 사용자 참여
* 이벤트 생성 이력 기반 RAG 확장

## 패키지 구조

```text
com.newvent
├── auth
├── event
├── registry
├── generation
├── editor
├── participation
├── discovery
├── user
├── rag
├── common
└── infra
```

## 실행 환경

* JDK 21
* Gradle Wrapper 사용

Windows:

```bash
gradlew.bat bootRun
```

macOS/Linux:

```bash
./gradlew bootRun
```

## 문서

프로젝트의 공통 규약과 설계 문서는 `docs/`에서 관리합니다.

* API 공통 규약: `docs/api-conventions.md`
* API 명세: `docs/api-spec.md`
* ERD: `docs/erd.md`
* 담당 영역: `docs/OWNERSHIP.md`
* 로컬 설정: `docs/setup.md`
* 주요 결정 기록: `docs/decisions/`

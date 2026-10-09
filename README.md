# swtp-monol — 스마트정수장 운영 백엔드 (모놀리스)

정수장의 **시설, 계측기, SCADA 태그, 송수펌프 운전, 전력 피크**를 관리하는 운영 시스템의 백엔드입니다.
Gradle 멀티모듈 모놀리스로 만들었고, 이후 MSA 버전인 [swtp-platform](https://github.com/ohsse/swtp-platform)으로 확장했습니다.

## 모듈 구성

| 모듈 | 역할 |
|---|---|
| `common` | 도메인 엔티티 전체, QueryDSL Q클래스, 공통 응답·예외·JWT 유틸, DB 마이그레이션 SQL |
| `api` | REST API 서버: 인증, 웹 계층, Swagger, SSE 실시간 푸시 |
| `scheduler` | Spring Batch 배치 서버 골격 (영속성·감사 설정까지 구성, 잡은 미구현) |

## 도메인과 API

| 도메인 | Swagger 그룹 | 내용 |
|---|---|---|
| `auth` · `user` · `menu` | 인증, 사용자 관리, 메뉴 관리 | JWT 액세스·리프레시 토큰, 권한(Role)별 메뉴 N:M 매핑 |
| `facility` | 시설물 관리, 설비별 사용량 | 정수지·배수지·가압장 등 **JPA JOINED 상속 + Discriminator** 다형성 마스터 |
| `instrument` | 계측기 관리, 송수펌프 가동·제어 이력 | 펌프·밸브·유량계 등 계측기 다형성 마스터, 가동률, 시계열 조회 |
| `tag` · `raw` | 태그 관리 | SCADA 태그(자연키 PK), 1분 원시 데이터(월 RANGE 파티션) |
| `proc` | AI 운전모드 | 공정별 AI 운전모드 상태·이력, **SSE**로 모드 변경 실시간 통지 |
| `opt` | 전력피크 목표값·분석, 사용량 트렌드 | 목표 피크 설정(SSE 통지), 피크 분석, 에너지 사용량 추이 |

## 기술 스택

- Java 21, **Spring Boot 4.0.5**, Gradle 멀티모듈
- Spring Data JPA + **QueryDSL** + MyBatis (조회 성격에 따라 하나만 골라 사용하고, 같은 쿼리를 두 방식으로 이중 구현하지 않음)
- Spring Batch, PostgreSQL(시계열 파티셔닝·BRIN 인덱스), p6spy
- springdoc-openapi, JUnit 5 · Mockito · AssertJ (테스트 클래스 81개)

## 설계 포인트

- **다형성 마스터 모델링**: 시설(`facility_m`)과 계측기(`instrument_m`)를 `@Inheritance(JOINED)` + `@DiscriminatorColumn`으로 나눴습니다. 응답 DTO도 Jackson `@JsonTypeInfo`와 Swagger `oneOf`로 자식 타입별 스키마를 노출합니다.
- **표준 용어 사전 기반 DB 네이밍**: 표준 단어, 데이터 도메인, 표준 용어의 3층 사전(`.claude/rules/dict/`)을 거쳐 컬럼명을 정합니다.
- **마이그레이션 운영 규칙**: `V{N}__{도메인}.sql`은 동결하고, 변경은 `V{N}_{연번}__patch.sql`로 분리합니다(`common/src/main/resources/db/migration/`).
- **에러 계약 단순화**: `ErrorCode` enum은 `httpStatus`만 가집니다. 메시지는 프론트엔드가 코드로 매핑합니다.
- **멀티테넌트 빌드**: 지자체별 설정을 `resources-env/{profile}/`과 `-Pprofile`로 분리합니다.

## AI 협업 하네스 (`.claude/`)

Claude Code로 개발하면서 쓴 **작업 절차와 규칙 체계**입니다.
- `/dev` 워크플로우: 분석 → 계획 → 태스크 → 구현 → 결과 → 리뷰 → 커밋 → 명세 전파. 산출물은 `docs/`에 남습니다.
- 도메인 검토 에이전트 4종: DBA, 백엔드, 도메인 전문가, 용어 관리자
- 훅: ErrorCode 계약 위반, DDL 컬럼 COMMENT 누락, 미완료 TASK 커밋을 자동으로 차단합니다.

## 실행 방법

```bash
# 환경변수 (.env.example 참고)
export DB_URL=jdbc:p6spy:postgresql://localhost:5432/smartwtp
export DB_USERNAME=smartwtp
export DB_PASSWORD=smartwtp
export JWT_SECRET=<32바이트 이상 임의 문자열>

# 스키마: common/src/main/resources/db/migration/*.sql 을 V 번호 순서대로 적용

./gradlew :api:bootRun
./gradlew test
```

## 공개 버전에서 바뀐 점

원본에서 다음을 제거하거나 치환했습니다.
- 개발 서버 주소, DB 계정, 기본 JWT 키 → 환경변수와 로컬 전용 기본값
- 로그, 빌드 산출물, 로컬 개인 설정(`.mcp.json`, `settings.local.json`)

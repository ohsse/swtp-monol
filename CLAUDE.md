# 스마트정수장 백엔드 프로젝트

## 프로젝트 개요
스마트정수장(Smart WTP) 관리 시스템의 백엔드 서비스입니다. Gradle 멀티모듈 구조로 구성되며, 각 모듈은 명확한 책임을 가집니다.

---

## 기술스택
- **언어**: Java 21
- **프레임워크**: Spring Boot 4.0.5
- **빌드도구**: Gradle Groovy DSL
- **데이터베이스**: PostgreSQL
- **ORM**: Spring Data JPA + Querydsl + MyBatis

---

## 멀티모듈 구조

| 모듈 | 역할 |
|------|------|
| `common` | 전체 도메인 엔티티, Querydsl QClass 생성, 공통 응답/예외/유틸리티 |
| `api` | HTTP 요청 처리, 인증, Swagger 문서화, 웹 계층 전반 |
| `scheduler` | Spring Batch 기반 스케줄링 및 배치 오케스트레이션 |

---

## 빌드 및 테스트 명령어

```bash
./gradlew.bat build              # 전체 빌드 및 테스트
./gradlew.bat test               # 테스트만 실행
./gradlew.bat :api:test          # api 모듈 테스트
./gradlew.bat :scheduler:test    # scheduler 모듈 테스트
./gradlew.bat :common:test       # common 모듈 테스트
./gradlew.bat clean build        # 클린 빌드 (QClass 재생성 포함)
./gradlew.bat :api:bootRun       # api 로컬 실행
./gradlew.bat :scheduler:bootRun # scheduler 로컬 실행
```

---

## 작업 흐름

개발 작업은 `/dev {슬러그}` 커맨드로 시작한다. 규모 분류(Small/Medium/Large), 단계별 정의(요청→**분석**→계획→분해→구현→결과→리뷰→커밋→**명세전파**), Fix Cycle 처리 등 상세는 `.claude/commands/dev.md`와 `.claude/commands/dev/*.md`를 참조한다.

Medium/Large 작업은 PLAN 직전에 `/dev:analyze` 단계에서 5인 회의 (메인 Claude 오케스트레이터 + 4 에이전트: wtp-dba-reviewer · wtp-backend-engineer · wtp-domain-expert · wtp-glossary-manager) 를 통해 신규 등장 용어·엔티티의 정합성을 검토하고, 결과를 `docs/analyze/{날짜}/{슬러그}/ANALYZE{n}.md` 와 룰 갱신 지시서(`.claude/rules/...` 파일들)에 반영한다. Small 작업은 본 단계를 면제한다.

`/dev:commit` 후 frontend 로 API 명세를 전파하려면 `/dev:spec {슬러그}` 를 실행한다 (선택적·후속 단계). 산출물은 모노레포의 `swtp/frontend/docs/api-specs/{슬러그}/SPEC{N}.md` 에 저장되며, entity·DTO·Controller 스펙 변경 시마다 새 번호로 누적된다. 라이프사이클 상세: [`process/doc-harness/README.md` §frontend 명세 라이프사이클](.claude/rules/process/doc-harness/README.md).

pre-commit 훅은 `.claude/settings.local.json`에 설정되어 있으며, 미완료 TASK에 속한 파일을 자동 unstage한다. 동작 상세·우회 방법: [`.claude/rules/hooks-guide.md`](.claude/rules/hooks-guide.md).

분산 SSOT 모델의 정합성을 주기적으로 점검하려면 `/governance` 를 호출한다 (사용자 주기 호출, 자동 실행 없음). 정량 진단 4항목 + 정합 진단 12항목을 실행하여 `docs/governance/{날짜}/REPORT.md` 에 보고서를 출력한다. **자동 수정 없음** — 발견사항은 사용자가 별도 `/dev` 사이클로 처리한다. 절차 상세: [`.claude/commands/governance.md`](.claude/commands/governance.md).

---

## 코드 작성 규칙

- Java 21, UTF-8, 들여쓰기 4칸
- SOLID 원칙 준수
- Lombok 적극 활용 (getter/setter/생성자 직접 구현 지양) — 패턴 상세: [`entity-patterns.md`](.claude/rules/entity-patterns.md)
- 의존성 주입은 `@RequiredArgsConstructor` 기반 생성자 주입 사용
- 신규 작성 또는 수정한 클래스·필드·메서드에 Javadoc 형식 주석 작성
- Swagger 를 이용하여 API 명세를 남긴다 — 패턴 상세: [`api-patterns.md`](.claude/rules/api-patterns.md)

---

## 규칙 문서 인덱스

| 영역 | 문서 | 참조 시점 |
|------|------|----------|
| 네이밍 컨벤션 | `.claude/rules/naming.md` | 클래스·DB 컬럼 명명 전 |
| **ROOT 어휘 사전 (모노레포 공통)** | `swtp/.claude/rules/dict/README.md` | **진입점** — 모노레포 공통 어휘 SSOT. 데이터 도메인 vs 비즈니스 도메인 구분 + 신규 어휘 등록 진입점 |
| &nbsp;&nbsp;├ 표준 단어 | `swtp/.claude/rules/dict/standard-words.md` | DB 컬럼명 조합 전 일반 단어(`nm`·`dt`·`amt` 등) 확인 |
| &nbsp;&nbsp;├ 표준 데이터 도메인 | `swtp/.claude/rules/dict/standard-data-domains.md` | 엔티티 속성의 값 형식(SQL 타입·길이·Java 타입) 결정 전 |
| &nbsp;&nbsp;└ 비즈니스 도메인 약어 | `swtp/.claude/rules/dict/domain-abbreviations.md` | 비즈니스 도메인 코드(`pump`·`raw`·`ctrl` 등 업무 영역) 등록 전 |
| **ROOT 코딩 디시플린 (모노레포 공통)** | `swtp/.claude/rules/coding-discipline.md` | LLM 행동 규율 4원칙 (구현 전 사고·단순성 우선·정밀한 수정·목표 중심 실행) + §2.5 정량 기준 면책 조항. 신규 코드 작성 / 룰 변경 / REVIEW 자동 점검 전 |
| **표준 용어 사전 (backend DB 컬럼)** | `.claude/rules/dict/README.md` | **진입점** — backend DB 컬럼명 SSOT. 신규 DB 컬럼명 확정 전 |
| &nbsp;&nbsp;└ 표준 용어 | `.claude/rules/dict/standard-terms.md` | DB 컬럼명 1차 정의 (`predc_elpwr_amt`·`use_yn` 등) |
| 엔티티 패턴 | `.claude/rules/entity-patterns.md` | 엔티티 설계·수정 전 |
| API 패턴 | `.claude/rules/api-patterns.md` | Service·Repository·DTO·Swagger 작성 전 |
| 예외·에러 코드 패턴 | `.claude/rules/exception-patterns.md` | `RestApiException`·`ErrorCode` enum 설계 전 |
| **DB 운영 패턴 (분리 인덱스)** | `.claude/rules/db/README.md` | **진입점** — 신규 작업은 아래 자식 3개를 직접 참조. 구 root `db-patterns.md` 는 redirect 잔존 (호환성) |
| &nbsp;&nbsp;├ DB 파티셔닝·보존 | `.claude/rules/db/partitioning-and-retention.md` | 시계열 파티션·데이터 수명주기 설계 전 |
| &nbsp;&nbsp;├ DB 인덱싱·마이그레이션 | `.claude/rules/db/indexing-and-migration.md` | 인덱스·DDL/무중단 스키마 변경 전 |
| &nbsp;&nbsp;└ DB 쿼리 튜닝 | `.claude/rules/db/query-tuning.md` | 트랜잭션 격리·슬로우 쿼리 분석 전 |
| 멀티테넌트 배포 | `.claude/rules/multi-tenant.md` | 지자체별 배포 구조 설계, resources-env 파일 추가 전 |
| OT 연동 가이드 | `.claude/rules/ot-integration.md` | SCADA 수신·PLC 제어 어댑터 설계 전 |
| 테스트 전략 | `.claude/rules/test-strategy.md` | 테스트 작성·도메인 시나리오 구현 전 |
| 테스트 E2E 로드맵 | `.claude/rules/test-strategy-e2e-roadmap.md` | Testcontainers·E2E 도입 ANALYZE 전 |
| **개발 프로세스 (분리 인덱스)** | `.claude/rules/process/README.md` | **진입점** — 커밋·훅·문서 하네스 통합. 구 root `commit-convention.md`·`hooks-guide.md`·`doc-harness.md` 는 redirect 잔존 (호환성) |
| &nbsp;&nbsp;├ 커밋 컨벤션 | `.claude/rules/process/commit-convention.md` | 커밋 메시지 작성 전 |
| &nbsp;&nbsp;├ 훅 동작 가이드 | `.claude/rules/process/hooks-guide.md` | 훅 트리거·차단 동작 확인 전 |
| &nbsp;&nbsp;└ 문서 하네스 (분리 인덱스) | `.claude/rules/process/doc-harness/README.md` | ANALYZE/PLAN/TASK/RESULT/REVIEW + frontend SPEC 문서 작성 전 (자식: `templates.md`·`transitions.md`·`checkbox-rules.md`) |

> **필수**: 엔티티·DTO·DB 테이블을 신규 설계하거나 수정할 때는 `db/README.md` (분리 인덱스 — `db/partitioning-and-retention.md` · `db/indexing-and-migration.md` · `db/query-tuning.md` 진입점) 와 `swtp/.claude/rules/dict/standard-data-domains.md` 를 먼저 확인한다.
>
> **⚠️ "도메인" 용어 구분**: 데이터 도메인 vs 비즈니스 도메인 구분 — `swtp/.claude/rules/dict/README.md` 참조.
>
> **🔍 룰 참조 정책 (on-demand)**: 분리 인덱스 파일(`db/README.md` · `dict/README.md`(backend 표준 용어) · `swtp/.claude/rules/dict/README.md`(ROOT 어휘) · `process/README.md`) 은 **진입점·매핑 표** 역할만 하며 정책 본문은 자식 룰에 있다. CLAUDE.md autoload 시점에 인덱스 표(§규칙 문서 인덱스) 만 인지한 뒤, 작업 중 인덱스 파일에서 자식 매핑을 확인하고 **직접 관련된 자식 룰만** Read 하여 컨텍스트 부담을 최소화한다. 이 패턴은 ROOT dict/(어휘 3층) · backend dict/(DB 컬럼 1층) · db/(수명주기·DDL·런타임 3분리) · process/(커밋·훅·doc-harness 4분할) 가 표준 사례다 (룰 분리 인덱스 패턴, 2026-04-26 명문화).

---

## 패키지 규칙

- 도메인 중심 feature-based 패키지: `com.mo.swtp.{도메인명}` (api/scheduler), `com.mo.swtp.common.{기능}` (공통 인프라)
- 클래스/필드/DB 컬럼 명명 상세: [`.claude/rules/naming.md`](.claude/rules/naming.md), 비즈니스 도메인 약어: `swtp/.claude/rules/dict/domain-abbreviations.md`

> **패키지 도입 현황 / 재설계 예정**
> - **현 도입 완료**: `com.mo.swtp.facility` (부모 + 자식 PWTF/DWT/RSV/PRSF + SensorPoint) · `com.mo.swtp.instrument` (부모 + 자식 PUMP/VALVE/FLWMTR/PRSMTR/LVMTR/ELCMTR) · `com.mo.swtp.tag` (`tag_srl_no` 자연키 PK + `IoCode` + `TagMeasurementType`) · `com.mo.swtp.raw` (`rawdata_1m_h` `raw_val`+`corr_val`+`quality_cd`+BaseEntity 4) — 마스터도메인설계 ANALYZE1 Round 2/3 (2026-05-02/03) 결정 적용 완료
> - **백지화 완료** (pump+AI 백지화 사이클 1 — 2026-05-12): `com.mo.swtp.pump.*` · `com.mo.swtp.ai.*` · `com.mo.swtp.scada.*` 전체 패키지 + 관련 DDL 9개 (V1·V2·V3·V4·V5·V6·V6_3·V7_2·V8_4) + scheduler `com.mo.swtp.scheduler.pump.*` 삭제. instrument.Pump 자식 엔티티는 보존
> - **재설계 예정** (pump+AI 백지화 사이클 2 — `/dev:analyze` 별도 호출): 5인 회의 라운드 알고리즘으로 결정 — 신규 비즈니스 도메인 약어 (pump 운영 도메인 명명·`ai` 약어 폐기 여부) · AI 운전 모드 구조 · 인터록·제어·예측 시퀀스 · 컨트롤러 책임 재정렬 · OT 아웃바운드 구조 · AI 추론 서버 클라이언트
> - **이관 사이클 잔존 결정 자산**: `instrument.domain.Pump` (자식 엔티티) · `instrument.domain.PumpOprtngType` (펌프조작유형 ANALYZE1, 2026-05-12 산출물 — `oprtng_type_cd` 컬럼 매핑)

---

## 예외 및 응답 규칙

- 비즈니스 예외는 `RestApiException` + `ErrorCode` 구현 enum 으로 통일, API 응답은 `CommonResponseDto` 형태 사용 (`code = "SUCCESS"` 또는 `ErrorCode.name()`)
- 상세 (ErrorCode enum 필드 규약·금지 패턴·자동 차단 훅): [`.claude/rules/exception-patterns.md`](.claude/rules/exception-patterns.md)

---

## 커밋 규칙

- 타입: `feat` / `fix` / `refactor` / `docs` / `chore` / `test`, 한국어로 작성
- 상세 (메시지 구조·타입 정의·브레이킹 체인지·훅 상호작용): [`.claude/rules/commit-convention.md`](.claude/rules/commit-convention.md)

---

## 보안 및 설정

- JWT secret, DB 계정, 민감 설정은 코드에 하드코딩하지 않는다.
- 환경별 설정은 `application-{profile}.yml` 또는 환경 변수로 주입한다.
- 실제 자격 증명은 커밋하지 않는다.

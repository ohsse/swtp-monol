# 네이밍 컨벤션

---

## 참조 문서 관계

| 문서 | 이 문서와의 관계 |
|------|----------------|
| `swtp/.claude/rules/dict/standard-words.md` | DB 컬럼 단어 재료 1차 정의 — `nm`·`dt`·`amt` 등 (ROOT) |
| `swtp/.claude/rules/dict/standard-data-domains.md` | Java 필드 타입 매핑 1차 정의 — `DOM_*` 코드 (ROOT) |
| `swtp/.claude/rules/dict/domain-abbreviations.md` | 비즈니스 도메인 약어 prefix 1차 정의 — `pump`·`user` 등 (ROOT) |
| [`entity-patterns.md`](entity-patterns.md) | 컬럼명 suffix 실사용 패턴 (`_yn`·외부 할당 PK) |

---

## 기본 규칙

| 대상 | 규칙 |
|------|------|
| 클래스 | `PascalCase` |
| 메서드/필드 | `camelCase` |
| 상수 | `UPPER_SNAKE_CASE` |
| 패키지 | 소문자 |

## Java 클래스 네이밍

| 역할 | 패턴 |
|------|------|
| 엔티티 | `{도메인명}` |
| 컨트롤러 | `{도메인명}Controller` |
| 서비스 | `{도메인명}Service` |
| 리포지토리 | `{도메인명}Repository` |
| 커스텀 리포지토리 | `{도메인명}CustomRepository` |
| 커스텀 리포지토리 구현체 | `{도메인명}CustomRepositoryImpl` |
| 조회/응답 DTO | `{도메인명}Dto` |
| 생성/수정 요청 DTO | `{도메인명}UpsertDto` |
| 검색 조건 DTO | `{도메인명}SearchDto` |
| 이벤트 | `{도메인명}{동작}Event` |
| 이벤트 발행기 | `{도메인명}EventPublisher` |
| 검증기/보조 컴포넌트 | `{도메인명}Validator`, `{도메인명}FileManager`, `{도메인명}Comp` |
| 에러 코드 | `{도메인명}ErrorCode` |
| 테스트 클래스 | `{대상클래스명}Test` |
| N:M 매핑 엔티티 | `{도메인1명}{도메인2명}` — 예: `MenuRole`. `Mapping`·`Relation`·`Map` 접미사 금지 (의미 중복). [`entity-patterns.md`](entity-patterns.md) §N:M 매핑 엔티티 패턴 |
| 복합 PK (`@Embeddable`) | `{엔티티명}Id` — 예: `MenuRoleId`. N:M 매핑 엔티티 + 1:N 상세 엔티티 (`PumpCmbnDetailId`) 공통 패턴 |

## DB 테이블 / 컬럼 네이밍
- 약어 기반 스네이크케이스
- PK 컬럼: `{도메인약어}_id`
- 테이블명: `{도메인약어}_{suffix}`

| 역할  | suffix |
|-----|--------|
| 마스터 | `m` |
| 내역  | `l` |
| 상세  | `d` |
| 이력  | `h` |
| 코드  | `c` |
| 명세  | `p`|
|관계|`r`|

> **경계 해석**: `_d`(상세)는 마스터의 1:N 구성요소 수직화, `_l`(내역)은 집계·리포트 저장 전용이므로 혼용하지 않는다.
> `_p`(명세)는 운전 규칙·임계값·태그 매핑 등 설정값, `_c`(코드)는 enum 수준의 정적 공통 코드에만 사용한다.
> `_r`(관계)는 N:M 의 매핑 및 관계간 발생하는 요소에 대해 정의한다.

## Java 필드 타입 매핑

DB 컬럼의 Java 필드 타입 매핑은 **표준 데이터 도메인 사전** 이 1차 정의한다.

- 📘 `swtp/.claude/rules/dict/standard-data-domains.md` — SQL 타입·Java 타입·NULL 정책 매핑 (ROOT)

주요 매핑 원칙 · 데이터 도메인 코드 · NULL 정책은 위 사전을 참조한다. 개별 패턴 상세는 [`entity-patterns.md`](entity-patterns.md) 참조.

> 신규 컬럼 설계 시 반드시 표준 데이터 도메인 사전에서 해당 `DOM_*` 코드를 선택한다. 기존 도메인에 적합한 코드가 없으면 `/dev:analyze` 에서 신규 데이터 도메인을 등록한 후 사용한다.

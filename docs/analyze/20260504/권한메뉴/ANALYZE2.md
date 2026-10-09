---
status: approved
created: 2026-05-06
updated: 2026-05-06
---
# 권한메뉴 — N:M 관계 룰 정합 재검토 (Cycle 2)

> **승인 일자: 2026-05-06**. ANALYZE1 과 일괄 approved. 본 ANALYZE2 의 R-1·R-2·R-3 결정이 ANALYZE1 의 안건 6·13 결론을 override 한다. PLAN 단계는 ANALYZE1+2 결합 참조.

## 작업 배경

[직전 ANALYZE: ANALYZE1.md](ANALYZE1.md) 의 안건 6 (`menu_role_p` 매핑 테이블 suffix) · 안건 13 (BaseEntity 상속 정책) · 안건 6 부속 (`@EmbeddedId MenuRoleMappingId` 복합 PK 네이밍) 에 대한 **N:M 관계 룰 정합 재검토** 사이클.

**재검토 사유 (사용자 요청 2026-05-06)**: ANALYZE1 의 N:M 관계 설계가 backend 룰 본문과 직접 매칭에서 의문점 발생. swtp 의 첫 N:M 매핑 테이블이라는 점에서 본 결정이 향후 표준 선례가 됨을 인지하고 신중 재결정.

**재검토 대상 안건** (직전 ANALYZE1 의 안건 1·2·3·4·5·7·8·9·10·11·12 는 그대로 유효 — 본 사이클 외):
- 안건 6 → 본 R-1 (suffix 결정) + R-3 (PK 패턴)
- 안건 13 → 본 R-2 / R-3 결합 (BaseEntity 상속 정책 확정)

**룰 본문 충돌 (핵심)**:
- `swtp/backend/.claude/rules/naming.md` L61: `_r`(관계) = "N:M 의 매핑 및 관계간 발생하는 요소에 대해 정의한다"
- `swtp/backend/.claude/rules/naming.md` L52-54: `_p`(명세) = "운전 규칙·임계값·태그 매핑 등 설정값"
- ANALYZE1 결정: `menu_role_p` (DBA 1차 권고가 "태그 매핑" 인용)

**룰 미정의 영역** (본 ANALYZE2 신설 기회):
- `entity-patterns.md` 에 N:M 매핑 패턴 본문 0건 — 매핑 엔티티 분리·복합 PK·CASCADE·인덱스 정책 모두 미정의
- `naming.md §Java 클래스 네이밍` 표에 N:M 매핑 엔티티·복합 PK 행 0건
- 코드베이스 `_r` suffix 사용 사례 0건 — 본 사례가 첫 표준 선례

**외부 산출물**:
- 사전 분석 plan: `~\.claude\plans\n-m-purrfect-lagoon.md` (사용자 승인 완료)

---

## 회의록 (5인 회의 토픽 주도)

### 안건 R-1: suffix 결정 — `_p`(현 채택) vs `_r`(룰 본문 직접 정합)

- **호출 에이전트**: `wtp-glossary-manager` (1차) + `wtp-dba-reviewer` (자기 평가) + `wtp-backend-engineer` (선례 영향)
- **Round 1 답변 요약**:
  - **wtp-glossary-manager**: `_p` 의 "태그 매핑" 은 SCADA 태그 메타데이터 설정값 맥락 (`tag_m` 의 매핑 설정), 본 사례는 두 독립 엔티티 (메뉴·역할) 사이의 순수 N:M 관계 → `_r` 직접 정합. 선례 `pump_interlock_p`·`ai_drvn_mod_p` 모두 단일 엔티티에 귀속된 설정값/시스템 상태 명세 — N:M 관계 테이블 아님. **R 권고**
  - **wtp-dba-reviewer**: 1차 답변 자기 평가 "변경 필요". "태그 매핑" 인용은 부적절. 선례 두 건 모두 운전 규칙·시스템 상태 명세로 N:M 매핑 아님. `(menu_id, user_role)` 복합 PK 가 중복 차단·forward 인덱스 동시 제공 — UNIQUE 별도 불필요. `psql \dt *_r` 일관 관찰 가치. **R 재권고**
  - **wtp-backend-engineer**: 룰 본문 직접 매칭 + 코드 영향 (ANALYZE1 단계 미구현 — 영향 0). 클래스명 권장 `MenuRole` (Mapping 접미사 불필요), 복합 PK `MenuRoleId` (`PumpCmbnDetailId` 선례 정합). `pump_cmbn_d` 는 1:N 정규화 패턴이라 N:M 매핑 아님 — 첫 `_r` 선례. **R 권고**
- **결론 (만장일치)**: **`menu_role_r` 채택** (suffix `_r`). 클래스명 `MenuRole` + 복합 PK `MenuRoleId` + Repository `MenuRoleRepository`. 향후 N:M 매핑 표준 선례 확립

### 안건 R-2: N:M 매핑 패턴 일반 룰 신설 (`entity-patterns.md` §N:M 패턴)

- **호출 에이전트**: `wtp-backend-engineer` (1차) + `wtp-dba-reviewer` (DDL 정책) + `wtp-glossary-manager` (어휘 정합)
- **Round 1 답변 요약**:
  - **wtp-backend-engineer**: §N:M 매핑 패턴 본문 초안 작성 (코드 예시 + 핵심 규칙 6개). `@ManyToMany` 직접 매핑 **금지** (BaseEntity 4 컬럼 보존 불가·`AuditingEntityListener` 비적용·매핑 단위 삭제 불가로 본 프로젝트 패턴과 충돌). 매핑 엔티티 분리가 기본. FK ON DELETE 양 마스터 모두 RESTRICT 기본, CASCADE 는 PLAN 명시 결정. INSERT/DELETE 전용 매핑 = `rgstr_*` 만 직접 선언, BaseEntity 미상속 (B안). 인덱스 — 카디널리티 작은 컬럼 단독 인덱스 미적용 (`db/indexing-and-migration.md §3.4 DOM_YN` 동일)
  - **wtp-dba-reviewer**: DDL 정책 7개 + 본 사례 DDL 초안 검토. **마스터 FK CASCADE 권고** (마스터 삭제 시 매핑 자동 정리), **코드 마스터 FK RESTRICT 권고** (코드값 삭제는 업무 규칙 위반). 카디널리티 2 단독 인덱스 (`idx_menu_role_r_role`) **보류** (Seq Scan 선호, 행 수 수십 수준). `rgstr_dtm`·`rgstr_id` NOT NULL 의무 (INSERT-only 구조로 결측 경로 없음, `DOM_DTM` 기본 NULL 정책보다 더 엄격 적용). `entity-patterns.md` 신설 또는 `db/indexing-and-migration.md §5` 신설 권고
  - **wtp-glossary-manager**: `naming.md §Java 클래스 네이밍` 표에 **매핑 엔티티 행 + 복합 PK 행 2행 추가 권고**. `role` 표준 단어 신규 등록 필요 — `standard-words.md` 미등록 상태에서 `user_role` 컬럼이 표준 용어로 이미 등록된 SSOT 공백 해소
- **Round 2** (FK ON DELETE 정책 BE/DBA 의견 차이): 본 사례 `menu_role_r` 의 마스터 FK 는 ANALYZE1 결정 (CASCADE) + DBA 권고 (CASCADE) 일관 → CASCADE 채택. 일반 룰 본문은 두 권고 모두 명시 (마스터 CASCADE 기본 + 안전 도메인은 RESTRICT 검토)
- **결론**:
  - `entity-patterns.md` 에 §N:M 매핑 패턴 신설 (본문 초안은 본 ANALYZE2 §PLAN 으로 전달할 결정 사항 §N:M 매핑 패턴 룰 본문 초안 참조)
  - `naming.md §Java 클래스 네이밍` 표에 매핑 엔티티 + 복합 PK 2행 추가
  - `swtp/.claude/rules/dict/standard-words.md` 에 `role` 표준 단어 신규 등록 (한글 "역할", 풀네임 `role`, 기본 데이터 도메인 `DOM_CODE_20`)

### 안건 R-3: PK 패턴 — `@EmbeddedId` 복합 PK vs 단일 BIGINT seq

- **호출 에이전트**: `wtp-backend-engineer` + `wtp-dba-reviewer`
- **Round 1 답변 요약**:
  - **wtp-backend-engineer**: 본 사례는 INSERT/DELETE 전용 + 매핑 자체에 대한 외부 FK 참조 없음 → 복합 PK 가 충분. 단일 seq 의 이점 (단일 컬럼 외부 참조·매핑 단위 이력) 무효. **E 권고**
  - **wtp-dba-reviewer**: 세 조건 (INSERT/DELETE 전용·외부 참조 없음·마스터 행 수 수십) 이 단일 seq 이점 무효화. 복합 PK `(menu_id, user_role)` 카디널리티 순서 정합 (`menu_id`(UUID 수십) > `user_role`(enum 2)). **E 재권고**
- **결론 (만장일치)**: **E 채택** — `@EmbeddedId MenuRoleId(menuId, userRole)` 복합 PK. 단일 BIGINT seq 는 매핑 행에 외부 FK 참조 또는 매핑 단위 갱신 이력 필요 시에만 도입 (룰 본문에 명시)
- **안건 13 (BaseEntity 상속) 결합 결정**: **B안 채택 (만장일치)** — `rgstr_dtm`·`rgstr_id` 직접 선언 + `@CreatedDate`·`@CreatedBy` + `@EntityListeners(AuditingEntityListener.class)`. BaseEntity 미상속. `updt_*` 2컬럼 제거 (INSERT/DELETE 전용 = 데드 컬럼). 매핑 행에 비즈니스적 갱신이 생기는 시점 (예: `valid_period` 등) 의 PLAN 에서 BaseEntity 전환 결정

### 안건 R-4: ANALYZE1 영향 갱신 항목 정리 (메인 Claude 단독 종합)

R-1·R-2·R-3 결과로 ANALYZE1 본문 중 다음 항목이 후속 PLAN 단계에서 본 ANALYZE2 결정으로 override 된다 (ANALYZE1 본문 자체는 수정 없음).

| ANALYZE1 위치 | 갱신 내용 | 근거 |
|-------------|---------|------|
| 안건 6 결론 | `menu_role_p` → `menu_role_r` (suffix `_r` 채택). `MenuRoleMapping` → `MenuRole`. `MenuRoleMappingId` → `MenuRoleId` | R-1 만장일치 |
| 안건 13 결론 (PLAN 위임) | **B안 확정** — `rgstr_*` 만 직접 선언, BaseEntity 미상속. `rgstr_dtm`·`rgstr_id` NOT NULL 의무 | R-2/R-3 만장일치 |
| 표준 사전 카탈로그 §신규 표준 단어 | **`role` 추가 등록** — Glossary 권고로 SSOT 공백 해소 | R-2 추가 발견 |
| 표준 사전 카탈로그 §신규 표준 용어 — `user_role` 행 | 사용 테이블 `user_m, menu_role_p` → `user_m, menu_role_r` | R-1 결과 |
| §신규 엔티티/DB 컬럼 — `menu_role_p` | 테이블명 `menu_role_r` · BaseEntity B안 (`rgstr_*` 직접) · `rgstr_*` NOT NULL 의무 · 인덱스 `idx_menu_role_r_role` **보류** | R-1·R-2·R-3 종합 |
| §도메인 모델 초안 | `MenuRoleMapping.java` → `MenuRole.java`, `MenuRoleMappingId.java` → `MenuRoleId.java`, `MenuRoleMappingRepository.java` → `MenuRoleRepository.java`, `MenuRoleMappingUpsertDto.java` → `MenuRoleUpsertDto.java` | R-1 부속 |
| §기존 사전·패턴과의 충돌 표 — `_p` vs `_d` 행 | `_p`(명세) vs `_r`(관계) suffix — `_r` 채택. `_p` 의 "태그 매핑" 은 SCADA 태그 메타데이터 설정값 맥락이며 순수 N:M 매핑은 `_r` 정의에 직접 정합 | R-1 결과 |
| §가정 및 미해결 질문 — `MenuRoleMappingId` 네이밍 행 | 미해결 → 결정 (`MenuRoleId`) | R-1 부속 |
| §가정 및 미해결 질문 — `idx_menu_m_parent` 행 | 미해결 → ANALYZE1 그대로 유효 (본 ANALYZE2 외) |  |
| §가정 및 미해결 질문 — `idx_menu_role_p_role` 행 (있다면) | ANALYZE1 본문에 명시 부재. 본 ANALYZE2 에서 `idx_menu_role_r_role` 보류 결정 |  |
| §룰 갱신 지시서 — 신규 추가 항목 | 4건 추가 (R-2 결과 — 아래 §룰 갱신 지시서 참조) |  |
| V6_6 SQL 영향 | 파일명 (`V6_6__menu_master_table.sql`) 유지. `menu_role_p` → `menu_role_r` 테이블명·CONSTRAINT 명·COMMENT 갱신. `rgstr_*` NOT NULL 추가. `updt_*` 2컬럼 제거 | R-1·R-2·R-3 종합 |

---

## 표준 사전 카탈로그

### 신규 표준 단어
(DB 컬럼 조합의 재료 — 의미의 최소 단위. 1차 정의: `swtp/.claude/rules/dict/standard-words.md` — ROOT)

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `role` | 역할 | 신규 | `standard-words.md` 미등록 상태에서 `user_role` 컬럼이 backend 표준 용어 사전에 이미 등재된 SSOT 공백 해소. 풀네임 `role`. 기본 데이터 도메인 `DOM_CODE_20`. `auth` 비즈니스 도메인 약어와 층위 다름 (단어 vs 비즈니스 도메인) — 충돌 없음. swtp 4-5자 약어 컨벤션 정합 (`ord`·`mod`·`seq` 선례) |

### 신규 표준 데이터 도메인
(값 형식 — SQL 타입·길이·Java 타입. 1차 정의: `swtp/.claude/rules/dict/standard-data-domains.md` — ROOT)

| 도메인 코드 | SQL 타입 | Java 타입 | NULL | 분류 | 결정 근거 |
|-----------|---------|---------|------|------|----------|
| (없음) | — | — | — | — | 본 ANALYZE2 범위 내 신규 데이터 도메인 없음 |

### 신규 표준 용어
(단어 + 데이터 도메인 → DB 컬럼명. 1차 정의: `backend/.claude/rules/dict/standard-terms.md`)

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `user_role` | `user`(비즈니스 도메인) + `role` | `DOM_CODE_20` | 기존 재사용 + 사용 테이블 갱신 | ANALYZE1 결정의 사용 테이블 `user_m, menu_role_p` 를 본 ANALYZE2 R-1 결정에 따라 `user_m, menu_role_r` 로 정정 |

분류값 (3층 공통): **신규 / 기존 재사용 / 유사 충돌 / 폐기·통합**

---

## 신규 엔티티 / DB 컬럼

### `menu_role_r` (메뉴-권한 매핑) — ANALYZE1 `menu_role_p` 갱신

- 패키지: `com.mo.swtp.menu` (ANALYZE1 결정 유지)
- suffix: **`_r` (관계)** — `naming.md` L61 정의 직접 정합. `pump_interlock_p`·`ai_drvn_mod_p` 선례는 운전 규칙·시스템 상태 명세이므로 본 사례에 비교 부적절
- 클래스명: **`MenuRole`** — `Mapping`·`Relation`·`Map` 접미사 금지 (`naming.md §Java 클래스 네이밍` 표 신규 행 적용)
- 복합 PK: **`MenuRoleId`** (`@EmbeddedId`) — `{엔티티명}Id` 패턴 (`PumpCmbnDetailId` 선례 정합)
- Repository: **`MenuRoleRepository`** + (필요 시) `MenuRoleCustomRepository` + `MenuRoleCustomRepositoryImpl`
- DTO: `MenuRoleUpsertDto`
- 복합 PK 컬럼: `(menu_id VARCHAR(36) NOT NULL, user_role VARCHAR(20) NOT NULL)` — 카디널리티 순서 (`menu_id` 선행) 정합
- FK: `menu_id` → `menu_m.menu_id` **ON DELETE CASCADE** (마스터 삭제 시 매핑 자동 정리, DBA 권고). `user_role` 은 현재 enum 직접 컬럼 (코드 마스터 미존재) — 향후 `user_role_c` 도입 시 ON DELETE RESTRICT 권고
- BaseEntity 적용: **B안 확정** — `BaseEntity` 미상속. `rgstr_dtm`(`@CreatedDate`)·`rgstr_id`(`@CreatedBy`) 만 직접 선언. `@EntityListeners(AuditingEntityListener.class)` 적용. `updt_*` 2컬럼 미보유 (INSERT/DELETE 전용 = 데드 컬럼 회피)
- `rgstr_dtm`·`rgstr_id` **NOT NULL 의무** — INSERT-only 구조로 결측 경로 없음 (`DOM_DTM` 기본 NULL 정책보다 더 엄격 적용)
- 인덱스: `idx_menu_role_r_role (user_role)` **보류** — 카디널리티 2 (ADMIN/USER) 단독 B-Tree 는 옵티마이저 Seq Scan 선호 (`db/indexing-and-migration.md §3.4 DOM_YN` 동일 정책). 행 수 수십 수준에서 Seq Scan 비용 무시 가능. 행 수 수백 이상으로 증가 + 역방향 조회 빈번 시 `CREATE INDEX CONCURRENTLY` 로 추가
- `@ManyToMany` 직접 매핑 **금지** — 매핑 엔티티 분리 (BaseEntity 4 컬럼 / `AuditingEntityListener` / 매핑 단위 삭제 등 본 프로젝트 패턴과 충돌)

### 도메인 모델 초안 갱신 (ANALYZE1 §PLAN 으로 전달할 결정 사항 갱신분)

```
com.mo.swtp.menu (신설 패키지 — ANALYZE1 결정 유지)
├── domain/
│   ├── Menu.java                          # ANALYZE1 결정 유지
│   ├── MenuRole.java                      # ⬅ MenuRoleMapping.java 에서 변경 (R-1)
│   └── MenuRoleId.java                    # ⬅ MenuRoleMappingId.java 에서 변경 (R-1, @Embeddable)
├── repository/
│   ├── MenuRepository.java                # ANALYZE1 결정 유지
│   ├── MenuCustomRepository.java          # ANALYZE1 결정 유지
│   ├── MenuCustomRepositoryImpl.java      # ANALYZE1 결정 유지
│   └── MenuRoleRepository.java            # ⬅ MenuRoleMappingRepository.java 에서 변경 (R-1)
├── service/
│   ├── MenuService.java                   # ANALYZE1 결정 유지 (CRUD ADMIN-only)
│   └── MenuQueryService.java              # ANALYZE1 결정 유지 (권한별 트리 조회)
├── dto/
│   ├── MenuUpsertDto.java                 # ANALYZE1 결정 유지
│   ├── MenuTreeDto.java                   # ANALYZE1 결정 유지
│   └── MenuRoleUpsertDto.java             # ⬅ MenuRoleMappingUpsertDto.java 에서 변경 (R-1)
├── controller/
│   ├── MenuController.java                # ANALYZE1 결정 유지
│   └── MyMenuController.java              # ANALYZE1 결정 유지
└── exception/
    └── MenuErrorCode.java                 # ANALYZE1 결정 유지

com.mo.swtp.auth.service                   # ANALYZE1 결정 유지
└── LoginFacadeService.java

com.mo.swtp.auth.dto                       # ANALYZE1 결정 유지
└── LoginResponseDto.java
```

---

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 회의 결론 |
|----------|----------|
| `_p`(명세) vs `_r`(관계) suffix — N:M 매핑 적용 | **`_r` 채택 (R-1 만장일치)**. `_p` 의 "태그 매핑" 은 SCADA 태그 ↔ 설비 메타데이터 설정값 맥락 (`tag_m` 의 매핑 설정), 본 사례는 두 독립 마스터 (메뉴·역할) 의 순수 N:M 관계 → `_r` L61 정의 직접 정합. 선례 `pump_interlock_p`·`ai_drvn_mod_p` 는 운전 규칙·시스템 상태 명세로 N:M 매핑 아님 |
| `entity-patterns.md` 에 N:M 매핑 패턴 미정의 | 본 ANALYZE2 R-2 에서 §N:M 매핑 패턴 신설 (룰 갱신 지시서 항목) — 매핑 엔티티 분리·`@EmbeddedId`·CASCADE/RESTRICT·BaseEntity B안·인덱스 정책 |
| `naming.md §Java 클래스 네이밍` 표에 매핑 엔티티 행 미정의 | 본 ANALYZE2 R-2 에서 매핑 엔티티 행 + 복합 PK 행 추가 (룰 갱신 지시서 항목) |
| `role` 표준 단어 미등록 + `user_role` 표준 용어 등록 SSOT 공백 | 본 ANALYZE2 R-2 에서 `role` 표준 단어 신규 등록 (Glossary 권고) |
| `MenuRoleMappingId` 복합 PK 네이밍 (ANALYZE1 가정 섹션) | **`MenuRoleId` 확정 (R-1)** — `{엔티티명}Id` 패턴, `PumpCmbnDetailId` 선례 정합 |
| `MenuRoleMapping` 클래스명 의미 중복 | **`MenuRole` 확정 (R-1)** — Mapping 접미사 금지 |
| BaseEntity 상속 정책 (ANALYZE1 안건 13 PLAN 위임) | **B안 확정 (R-2/R-3 만장일치)** — `rgstr_*` 직접 선언, BaseEntity 미상속, `rgstr_*` NOT NULL 의무 |

---

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안

§신규 엔티티 / DB 컬럼 의 도메인 모델 초안 갱신 참조 — 4개 파일명 변경 (`MenuRoleMapping*` → `MenuRole*`).

### DB 설계 변경

- 신규 SQL: `common/src/main/resources/db/init/V6_6__menu_master_table.sql` (ANALYZE1 결정 파일명 유지)
- 본 SQL 의 `menu_role_p` → `menu_role_r` 갱신 항목:
  - 테이블명 `menu_role_p` → `menu_role_r`
  - PK 명 `pk_menu_role_p` (있다면) → `pk_menu_role_r`
  - FK 명 `fk_menu_role_p_menu` → `fk_menu_role_r_menu`
  - INDEX 명 (보류 결정으로 미생성)
  - COMMENT 갱신 — `menu_role_r.menu_id`·`menu_role_r.user_role` 컬럼 라벨 갱신
- DDL 본문 갱신:
  - `rgstr_dtm TIMESTAMP NOT NULL` (NOT NULL 추가 — DBA 권고)
  - `rgstr_id VARCHAR(50) NOT NULL` (NOT NULL 추가 — DBA 권고)
  - `updt_dtm`·`updt_id` 2컬럼 **제거** (B안 확정 — INSERT/DELETE 전용 = 데드 컬럼)
- CHECK 제약 일체 미적용 (Java enum 단일 방어선) — ANALYZE1 안건 9 결정 유지
- COMMENT ON COLUMN 전면 의무 — ANALYZE1 안건 10 결정 유지 (BaseEntity 4 → `rgstr_*` 2 컬럼만 표준 라벨 적용)
- self-FK ON DELETE RESTRICT (`menu_m.parent_menu_id`) — ANALYZE1 결정 유지
- 매핑 ON DELETE CASCADE (`menu_role_r.menu_id` → `menu_m.menu_id`) — ANALYZE1 결정 유지
- `menu_nm` 시스템 전체 UNIQUE — ANALYZE1 결정 유지

### N:M 매핑 패턴 룰 본문 초안 (`entity-patterns.md` §N:M 매핑 패턴 신설용 SSOT)

PLAN 단계에서 본 초안을 `entity-patterns.md` 에 반영한다.

```markdown
## N:M 매핑 엔티티 패턴

두 독립 마스터 사이의 진정한 N:M 관계를 매핑 엔티티로 분리한다. 단일 마스터의 1:N 구성요소 정규화 (`pump_cmbn_d` 같은 `_d`(상세) 패턴) 는 본 패턴 대상이 아니다.

### 사용 시점

- 두 독립 마스터·코드값 사이의 진정한 N:M 관계 (예: `menu_role_r` — 메뉴 ↔ 권한)
- 매핑 자체에 감사 메타 (`rgstr_*`) 또는 도메인 컬럼 추가 가능성이 1% 라도 존재하는 경우

### 매핑 엔티티 분리 패턴 (권장)

```java
@Entity
@Table(name = "menu_role_r")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MenuRole {

    @EmbeddedId
    private MenuRoleId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("menuId")
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    // user_role 은 @EmbeddedId 내 @Enumerated 컬럼으로 관리 — 별도 @ManyToOne 불필요

    @CreatedDate
    @Column(name = "rgstr_dtm", nullable = false, updatable = false)
    private LocalDateTime rgstrDtm;

    @CreatedBy
    @Column(name = "rgstr_id", length = 50, nullable = false, updatable = false)
    private String rgstrId;

    public static MenuRole create(Menu menu, UserRole userRole) {
        return new MenuRole(new MenuRoleId(menu.getMenuId(), userRole), menu);
    }
}

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class MenuRoleId implements Serializable {

    @Column(name = "menu_id", length = 36, nullable = false)
    private String menuId;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", length = 20, nullable = false)
    private UserRole userRole;
}
```

### 핵심 규칙

1. **테이블 suffix `_r`** — `naming.md` L61 N:M 관계 정의 직접 정합. `_p`(명세) 의 "태그 매핑" 인용 금지 (설정값 맥락 한정)
2. **클래스 네이밍** — `{도메인1}{도메인2}` 단순 결합 (`MenuRole`·`UserPump`·`RoleFacility` 등). **`Mapping`·`Relation`·`Map` 접미사 금지** (의미 중복). 복합 PK 클래스명 = `{엔티티명}Id` (`MenuRoleId`)
3. **`@ManyToMany` 직접 매핑 금지** — 매핑 엔티티 분리가 기본. `@ManyToMany` 는 BaseEntity 4 컬럼 / `AuditingEntityListener` / 매핑 단위 삭제 / 도메인 컬럼 추가 모두 불가능 → 본 프로젝트 패턴과 충돌
4. **복합 PK — `@EmbeddedId` 기본** — 자연키 의미 명확 + 중복 INSERT 자동 차단 + forward 인덱스 자동. `@IdClass` 는 레거시 호환 또는 JPA 프레임워크가 `@EmbeddedId` 미지원 시 예외적 케이스. 단일 `BIGINT seq` PK + UNIQUE `(매핑컬럼1, 매핑컬럼2)` 는 매핑 행에 외부 FK 참조 또는 매핑 단위 갱신 이력 필요 시에만 도입 (`DOM_SEQ_BIGINT` + `@GeneratedValue(SEQUENCE)`)
5. **FK ON DELETE 정책**:
   - 마스터 FK (`menu_id` → `menu_m`): **CASCADE 기본** — 마스터 삭제 시 매핑 자동 정리. 도메인 안전 영역 (인터록·운전 모드 등 `ot-integration.md §5` 직결) 은 RESTRICT 검토를 PLAN 명시
   - 코드 마스터 FK (예: `user_role` → 향후 `user_role_c`): **RESTRICT 기본** — 코드값 삭제는 업무 규칙 위반이므로 CASCADE 금지
6. **BaseEntity 상속 정책**:
   - **INSERT/DELETE 전용 매핑** (UPDATE 시나리오 없음 — `menu_role_r` 사례) → **B안: BaseEntity 미상속**, `rgstr_dtm`·`rgstr_id` 만 직접 선언 + `@CreatedDate`·`@CreatedBy` + `@EntityListeners(AuditingEntityListener.class)`. `updt_*` 2컬럼은 데드 컬럼이므로 보유 금지
   - **매핑 단위 갱신 가능** (예: `valid_period`·`grant_reason` 등 변경 가능 컬럼 추가 시) → **A안: BaseEntity 4 컬럼 상속**
   - 두 후보 중 본 사례 시점의 PLAN 에서 명시 결정 의무
7. **`rgstr_*` NOT NULL 의무** — INSERT-only 구조에서 결측 경로 없음. `DOM_DTM` 기본 NULL 정책보다 더 엄격 적용. INSERT 시 `AuditingEntityListener` 자동 주입 보장
8. **인덱스 정책**:
   - 복합 PK `(menu_id, user_role)` 자체가 forward 방향 (선행 컬럼) 인덱스 자동 제공
   - 역방향 단독 조회 빈번 + 행 수 수백 이상 → `CREATE INDEX CONCURRENTLY idx_{테이블}_{컬럼}` 추가
   - **카디널리티 작은 컬럼 단독 인덱스 미적용** — `db/indexing-and-migration.md §3.4 DOM_YN` 정책 동일. 옵티마이저 Seq Scan 선호 시 도입 보류
   - 카디널리티 순서 정합 — 복합 PK 컬럼 순서는 카디널리티 높은 컬럼 선행 (`indexing-and-migration.md §1` 정책)

### 도메인 룰 — N:M 매핑 vs 1:N 정규화 구분

- `_r` (관계): 두 독립 마스터·코드값 사이의 진정한 N:M (예: `menu_role_r`·향후 `user_facility_r`·`role_instrument_r`)
- `_d` (상세): 단일 마스터의 1:N 구성요소 정규화 (예: `pump_cmbn_d` — 펌프 조합 마스터의 상세 정규화). `entity-patterns.md §외부 할당 PK 엔티티 패턴` 의 `Persistable<ID>` 적용 등 별도 패턴
```

### 적용할 패턴

- `entity-patterns.md §N:M 매핑 패턴` — **신설** (본 ANALYZE2 룰 갱신 지시서)
- `entity-patterns.md §기본 엔티티 패턴` — Menu UUID 자동 생성 PK (ANALYZE1 결정 유지)
- `entity-patterns.md §여부(Y/N) 필드 패턴` — Menu `use_yn` (ANALYZE1 결정 유지)
- `naming.md §Java 클래스 네이밍` — N:M 매핑 엔티티 + 복합 PK 행 (R-2 추가)
- `api-patterns.md §Repository 패턴` — `MenuRoleRepository` 표준
- `api-patterns.md §Swagger/OpenAPI 패턴` — 메뉴-권한 매핑 API (ADMIN-only)
- `exception-patterns.md` — `MenuErrorCode` (ANALYZE1 결정 유지)
- `coding-discipline.md §2.1` — 매핑 엔티티 단순성 50줄 이하 (분리 정당성)

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 자기참조 메뉴 깊이 N=3 (애플리케이션 검증) | 가정 | ANALYZE1 결정 유지 |
| `menu_role_r` BaseEntity 상속 정책 | **결정** | 본 ANALYZE2 R-2/R-3: **B안** (rgstr_* 직접 선언, 미상속). `rgstr_*` NOT NULL 의무 |
| `MenuRoleMappingId` 복합 PK 클래스 네이밍 | **결정** | 본 ANALYZE2 R-1: **`MenuRoleId`** (`{엔티티명}Id` 패턴) |
| `idx_menu_m_parent (parent_menu_id, disp_ord)` 인덱스 도입 여부 | 미해결 | ANALYZE1 결정 유지 — PLAN 단계 결정 (단일 SELECT 패턴 채택 시 실효성 낮음) |
| `idx_menu_role_r_role (user_role)` 역방향 인덱스 도입 여부 | **결정** | 본 ANALYZE2 R-2: **보류** — 카디널리티 2 단독 B-Tree Seq Scan 선호. 행 수 수백 이상 + 역방향 조회 빈번 시 추가 |
| 자기참조 부모 비활성(`use_yn = N`) 시 자식 CASCADE 정책 | 미해결 | ANALYZE1 결정 유지 — PLAN 단계 결정 |
| AI 운전 모드 전환 화면 메뉴의 접근 role 정책 | 미해결 | ANALYZE1 결정 유지 — PLAN 단계 시드 데이터 결정 |
| 권한 변경 시 stale JWT 토큰 무효화 정책 | 미해결 → 별도 사이클 분리 | ANALYZE1 결정 유지 (사용자 결정 2026-05-06 — 별도 ANALYZE) |
| `user_role_c` 코드 마스터 도입 여부 + FK ON DELETE RESTRICT | 미해결 | 향후 권한 종류 확장 시 검토. 현재는 `menu_role_r.user_role` 단순 enum 컬럼 (FK 없음) 유지 |
| 향후 매핑 단위 갱신 가능 컬럼 추가 시 BaseEntity 전환 | 가정 | `valid_period`·`grant_reason` 등 변경 가능 컬럼 추가 시 PLAN 에서 A안 (BaseEntity 4 컬럼) 전환 결정 |

분류값: 가정 / 미해결 / 결정 (본 ANALYZE2 에서 ANALYZE1 의 미해결 3건이 결정으로 변환)

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

> ANALYZE1 의 룰 갱신 지시서 (10건) 은 그대로 유효 — 단 `user_role` 사용 테이블 행은 본 ANALYZE2 R-1 결과로 정정 필요. 아래는 본 ANALYZE2 신규 추가 항목.

- [x] `swtp/.claude/rules/dict/standard-words.md` — `role` 표준 단어 신규 등록 (한글 논리명: 역할, 풀네임: role, 기본 데이터 도메인: `DOM_CODE_20`, 등록일: 2026-05-06, 비고: `user_role`·`menu_role_r` 등 컬럼 조합 재료. `auth` 비즈니스 도메인 약어와 층위 다름 — 충돌 없음)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `user_role` 행 사용 테이블 정정 (`user_m, menu_role_p` → `user_m, menu_role_r`, 비고: 본 ANALYZE2 R-1 suffix 변경 반영)
- [x] `swtp/backend/.claude/rules/naming.md` — §Java 클래스 네이밍 표에 N:M 매핑 엔티티 행 추가 (`{도메인1명}{도메인2명}` — 예: `MenuRole`. `Mapping`·`Relation`·`Map` 접미사 금지 명시)
- [x] `swtp/backend/.claude/rules/naming.md` — §Java 클래스 네이밍 표에 복합 PK (`@Embeddable`) 행 추가 (`{엔티티명}Id` — 예: `MenuRoleId`. N:M 매핑 엔티티 + 1:N 상세 엔티티 공통 패턴)
- [x] `swtp/backend/.claude/rules/entity-patterns.md` — §N:M 매핑 엔티티 패턴 신설 (본 ANALYZE2 §PLAN 으로 전달할 결정 사항 §N:M 매핑 패턴 룰 본문 초안 반영). 핵심 규칙 8개: 테이블 suffix `_r` / 클래스 네이밍 / `@ManyToMany` 금지 / `@EmbeddedId` 기본 / FK ON DELETE 정책 (마스터 CASCADE·코드 마스터 RESTRICT) / BaseEntity 상속 정책 (B안 INSERT/DELETE 전용 vs A안 갱신 가능) / `rgstr_*` NOT NULL 의무 / 인덱스 정책 (복합 PK forward 자동·역방향 단독·카디널리티 작은 컬럼 단독 인덱스 미적용). §규칙 요약 한 줄 요약도 동시 추가
- [x] ANALYZE1 의 룰 갱신 지시서 10번째 행 (`user_role` 사용 테이블) — `menu_role_p` 표기를 `menu_role_r` 로 정정 (본 ANALYZE2 R-1 결과 반영)

> ANALYZE1 의 다른 9개 행 (`url` 표준 단어 + `DOM_URL_200` 거부 + `menu` 비즈니스 도메인 + `menu_id`·`menu_nm`·`menu_url`·`menu_desc`·`parent_menu_id`·`disp_ord` 표준 용어) 은 본 ANALYZE2 영향 없음 — 그대로 유효.

---

## 산출물

- [계획안](../../../plan/20260504/권한메뉴/PLAN1.md) — ANALYZE1 + ANALYZE2 모두 approved 후 자동 생성. ANALYZE2 가 ANALYZE1 의 안건 6 / 13 결론을 override

---
status: approved
created: 2026-05-04
updated: 2026-05-06
---
# 권한메뉴 — 도메인 분석

> **승인 일자: 2026-05-06**. ANALYZE2 (N:M 관계 룰 정합 재검토) 와 일괄 approved. 본 ANALYZE1 의 안건 6 (`menu_role_p`) · 안건 13 (BaseEntity 상속 PLAN 위임) · 부속 (`MenuRoleMappingId` 네이밍) 결론은 [`ANALYZE2.md`](ANALYZE2.md) 에 의해 override 됨 (R-1: `menu_role_r` + `MenuRole` + `MenuRoleId`, R-2/R-3: BaseEntity B안). PLAN 단계는 ANALYZE1+2 결합 참조.

## 작업 배경

스마트정수장 backend 에 신규 비즈니스 도메인 `menu` 를 추가한다.

**사용자 요청 (요약):**
- 메뉴 도메인 신설 — 자기참조 재귀 (메뉴ID·메뉴명·메뉴URL·메뉴설명·표출순서·상위메뉴ID)
- 권한별(ADMIN/USER) 라우팅 메뉴 분리
- 로그인·메뉴 변경 시 클라이언트가 권한별 메뉴 트리를 수신할 수 있는 인증·전달 흐름

**외부 산출물:**
- 사전 분석 plan 파일: `~\.claude\plans\happy-toasting-canyon.md` (사용자 승인 완료)
- backend 탐색 보고: 본 ANALYZE Round 0 사전 분석 (메인 Claude 단독)

**사전 결정 (사용자 plan 모드 승인 — 재논의 X):**
- 메뉴 정보 위치: 로그인 응답 body 분리 (JWT 페이로드는 `role` 만 유지, 현행 그대로)
- 메뉴 변경 동기화: frontend `GET /api/menus/me` 재조회 (토큰 재발급 X)

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 비즈니스 도메인 약어 `menu` 신규 등록

- **호출 에이전트**: `wtp-glossary-manager`
- **Round 1 답변 요약**:
  - **wtp-glossary-manager**: `menu` 는 `domain-abbreviations.md` 미등록. 기존 마스터(`user`·`auth`·`pump`·`ctrl`)·도입 예정(`facility`·`instrument`·`raw`·`tag`·`alarm`·`diag`·`opt`·`pwtf`·`dwt`·`ai`) 과 충돌 없음. 동의어/유사 의미 약어 충돌 0건. `com.mo.swtp.menu` 패키지 도입 전제로 "도입 예정" 섹션에 신규 등록 가능
- **결론**: `menu` 비즈니스 도메인 약어 신규 등록 (도입 예정 섹션). 패키지 도입 시 마스터 도메인 섹션으로 승격

### 안건 2: 표준 데이터 도메인 `DOM_URL_200` 신규 등록 (DBA 2차 승인 안건)

- **호출 에이전트**: `wtp-glossary-manager` (1차) + `wtp-dba-reviewer` (2차 승인)
- **Round 1 답변 요약**:
  - **wtp-glossary-manager**: `DOM_TEXT`(TEXT 무제한) 와 타입·길이 다르므로 유사 충돌 아님. RFC 2396 URL 상한 2048자이나 실용 200자 충분. `DOM_NAME_100` 은 100자 짧음·이름 용도 전용으로 재사용 불가. **등록 권고 (DBA 2차 승인 필수)**
  - **wtp-dba-reviewer**: **REJECT** — `DOM_TEXT` 와 유사 충돌 판정. `menu_url VARCHAR(200)` 은 `DOM_TEXT` 의 업무 특수화 사례에 불과, URL 전용 별도 도메인 분리 실익 없음. 200자 신뢰 근거 불충분 (RFC 3986 상한 없음, 실무 URL/쿼리스트링 200자 초과 빈번). **권고: `menu_url VARCHAR(255) NULL` + `DOM_*` 미지정 처리. 향후 URL 전용 도메인이 3건 이상 집적되면 `DOM_URL_255` 로 일괄 등록 재검토**
- **결론**: **`DOM_URL_200` 등록 거부 (DBA 2차 승인 권한 우선)**. `menu_url` 은 `VARCHAR(255) NULL` + 데이터 도메인 미지정 처리. ROOT `standard-data-domains.md` 의 등록 거부 이력 섹션에 기록

### 안건 3: 표준 단어 `url` 신규 등록

- **호출 에이전트**: `wtp-glossary-manager`
- **Round 1 답변 요약**:
  - **wtp-glossary-manager**: `url`(Uniform Resource Locator) 자체가 보통명사화된 약어. 기존 선례 `io`(input/output)·`srl`(serial) 등 약어가 표준 단어로 등록된 사례 있음. swtp 4-5자 약어 컨벤션 중심이지만 강제 규정 아님 (`nm`·`dt`·`cd` 등 2자 단어 다수 존재). 등록 가능
- **결론**: `url` 표준 단어 신규 등록 (기본 데이터 도메인은 안건 2 결과로 미지정 — `(조합)` 처리). 풀네임 `uniform resource locator`

### 안건 4: 표준 용어 5건 신규 등록 + 2건 재사용

- **호출 에이전트**: `wtp-glossary-manager`
- **Round 1 답변 요약**:
  - **wtp-glossary-manager**: 신규 5건(`menu_id`·`menu_nm`·`menu_url`·`menu_desc`·`parent_menu_id`), 재사용 2건(`disp_ord`·`user_role`). `menu_nm` UNIQUE 인덱스 권고 — `facility_nm`·`instrument_nm` 선례 동일 (마스터도메인설계 ANALYZE1 Round 3, 시스템 전체 UNIQUE)
- **결론**: 위 7건 카탈로그 §표준 사전 카탈로그 의 표준 용어 표 참조. `menu_url` 의 데이터 도메인은 안건 2 결과를 반영하여 미지정(빈 칸 또는 `(VARCHAR(255))`)

### 안건 5: 자기참조 메뉴 깊이 제한 정책

- **호출 에이전트**: `wtp-domain-expert` + `wtp-backend-engineer`
- **Round 1 답변 요약**:
  - **wtp-domain-expert**: 정수장 운영 화면은 기능 중심 단순 구조 (계측·제어·알람·설정 4대 분류). 2단(대분류 → 항목) 적정, 3단 허용, 무제한 불필요. **N=3 권고**. 권고(중간)
  - **wtp-backend-engineer**: 검증 위치는 **(b) Java 애플리케이션 레벨**. DB CHECK 트리거는 PostgreSQL 지원 제한 + DDL 복잡도 과잉. `MenuService.create()` 진입 시 부모 깊이 검증. 제한 상수 `MAX_MENU_DEPTH = 3` 애플리케이션 레벨 관리. ANALYZE 가정 섹션에 1건 등록
- **결론**: 메뉴 자기참조 깊이 **N=3** 으로 애플리케이션 레벨 검증. `MenuService.create()`·`changeInfo()` 진입 시 부모 체인 깊이 산정 후 차단. DB 레벨 강제 미적용. 본 ANALYZE 가정 섹션에 등록

### 안건 6: 권한 매핑 구조 (M:N 매핑 테이블 vs 단일 컬럼)

- **호출 에이전트**: `wtp-dba-reviewer` + `wtp-backend-engineer`
- **Round 1 답변 요약**:
  - **wtp-dba-reviewer**: 현재 ADMIN/USER 2종 고정이지만 사용자 결정(향후 권한 추가 가능성)이 있으므로 **매핑 테이블 채택 정당**. `_p`(명세) suffix 적합 — `naming.md` 의 "운전 규칙·임계값·**태그 매핑** 등 설정값" 정합. 단 `menu_role_p` 는 INSERT/DELETE 전용 (매핑 행 자체 UPDATE 시나리오 없음) → `updt_*` 2컬럼 제거 권고, `rgstr_*` 만 유지. 권고(중간)
  - **wtp-backend-engineer**: 매핑 엔티티 명시 분리 + `@EmbeddedId MenuRoleMappingId` 복합 PK. `@ManyToMany` 직접 매핑은 BaseEntity 4 컬럼 보존 불가 — 추후 매핑 단위 이력·감사 추적 요구 발생 시 재설계 필요. **매핑 엔티티 분리 권장**
- **결론**: M:N 매핑 테이블 `menu_role_p` 채택. suffix `_p`(명세) 사용. `updt_*` 2컬럼 제거 정책은 PLAN 단계 결정 (BaseEntity 상속 vs `rgstr_*` 직접 보유 — 가정 섹션 등록)

### 안건 7: JWT 페이로드 vs 응답 body 분리 정책 도메인 정합성

- **호출 에이전트**: `wtp-domain-expert`
- **Round 1 답변 요약**:
  - **wtp-domain-expert**: 메뉴/라우팅은 OT 운전 흐름과 무관한 UI 표출 제어 계층. 도메인 4영역(알람·인터록·운전 모드·이력) 충돌 0건. JWT stateless 원칙 유지는 `ai_drvn_mod_p` "사용자 의도 vs 시스템 상태 분리" 와도 구조적 정합. 통과(낮음)
- **결론**: 사용자 사전 결정(응답 body 분리) 도메인 정합성 통과. 별도 변경 없음

### 안건 8: 권한 변경 시 stale JWT 토큰 무효화 정책 (보안 블로커 — 사용자 결정)

- **호출 에이전트**: `wtp-domain-expert`
- **Round 1 답변 요약**:
  - **wtp-domain-expert**: ADMIN 이 타 사용자 role 변경 시 활성 JWT `role` 클레임이 stale → privilege escalation 위험. `coding-discipline.md §2.1` 보안 블로커 격상 조항. **권고: 본 작업 범위 포함 + refresh token 블랙리스트 또는 토큰 버전 관리 도입.** 단 본 작업과 분리 가능 (사용자 판단)
- **사용자 결정 (2026-05-06)**: **별도 사이클로 분리** — 본 작업은 권한메뉴 도메인 신설에 집중. 토큰 무효화는 인증 흐름 전반 보안 명세로 별도 ANALYZE 사이클에서 검토. 단기적 위험은 본 ANALYZE §가정 및 미해결 질문 섹션에 명시 + 후속 작업으로 등록
- **결론**: 본 작업 범위에서 제외. ANALYZE §가정 섹션 명시 + RESULT 단계의 후속 작업 권고에 명시

### 안건 9: DDL CHECK 제약 정책 — 신규 테이블에 미적용

- **호출 에이전트**: `wtp-dba-reviewer` + `wtp-domain-expert`
- **Round 1 답변 요약**:
  - **wtp-dba-reviewer**: `ck_menu_m_use_yn`·`ck_menu_role_p_user_role` CHECK 제약 추가는 `indexing-and-migration.md §3.2` 정책 위반 — 신규 테이블에 CHECK 제약 추가 금지. Java `@Enumerated(EnumType.STRING)` + `YnType` 단일 방어선. 블로커(높음)
  - **wtp-domain-expert**: `user_role` CHECK 제약 도입 시 향후 VIEWER/OPERATOR 추가에 ALTER TABLE 필요 (운영 락 위험). 무중단 변경 원칙 위반. CHECK 미도입 권고
- **결론**: V6_6 신규 테이블에 CHECK 제약 일체 미적용. Java enum 단일 방어선 채택. 단 기존 `user_m.ck_user_m_use_yn` 은 admin 초기 INSERT 안전망으로 현상 유지 (별개 정책)

### 안건 10: COMMENT ON COLUMN 전면 의무화

- **호출 에이전트**: `wtp-dba-reviewer`
- **Round 1 답변 요약**:
  - **wtp-dba-reviewer**: `indexing-and-migration.md §4` 전면 컬럼 코멘트 의무. `check-ddl-column-comment.sh` 훅 자동 차단 대상. BaseEntity 4 컬럼도 표준 라벨 적용. 블로커(높음)
- **결론**: V6_6 SQL 파일에 모든 컬럼 (BaseEntity 4 컬럼 포함) `COMMENT ON COLUMN` 작성 의무. PLAN 의 DDL 작성 시 강제

### 안건 11: 자기참조 트리 N+1 회피 — 단일 SELECT + 메모리 트리 빌드

- **호출 에이전트**: `wtp-backend-engineer` + `wtp-dba-reviewer`
- **Round 1 답변 요약**:
  - **wtp-backend-engineer**: `Menu.children` `@OneToMany(fetch=LAZY)` 사용 시 N+1 발생. 단일 `SELECT * FROM menu_m WHERE use_yn='Y' ORDER BY disp_ord` 후 Java 메모리에서 `parent_menu_id` 기준 트리 빌드. `MenuCustomRepositoryImpl` 패턴 적용. 통과
  - **wtp-dba-reviewer**: 메뉴 마스터는 수십~수백 건 수준 — 단일 조회 후 메모리 트리 빌드가 `JOIN FETCH` 재귀보다 단순·예측 가능. 통과
- **결론**: 단일 SELECT + 메모리 트리 빌드 패턴 채택. `Menu.children` 양방향 매핑 미선언 (단방향 `@ManyToOne(parent_menu_id)` 만)

### 안건 12: `LoginFacadeService` + `LoginResponseDto` 분리

- **호출 에이전트**: `wtp-backend-engineer`
- **Round 1 답변 요약**:
  - **wtp-backend-engineer**: 두 건 블로커(높음)
    1. `AuthService` → `MenuQueryService` 직접 의존은 패키지 간 의존 방향 단방향 고정 위반. **`LoginFacadeService` 신설** — `AuthService`(토큰 발급) + `MenuQueryService`(메뉴 조회) 호출 → `LoginResponseDto` 조립
    2. 기존 `TokenResponseDto` 에 `menus` 추가 시 refresh 응답에도 메뉴 직렬화 → 페이로드 증가 + 관심사 혼재. **`LoginResponseDto` 신설** (`token` + `menus`), `TokenResponseDto` 는 refresh 전용 유지
- **결론**: `LoginFacadeService` 신설 + `LoginResponseDto` 신설. PLAN 단계에서 패키지 위치(`com.mo.swtp.auth.service`) 와 DTO 필드 명세 확정

### 안건 13: 매핑 엔티티 BaseEntity 적용 정책 (PLAN 위임)

- **호출 에이전트**: 미호출 — Round 1 답변 종합으로 결론
- **Round 1 답변 종합**:
  - **wtp-dba-reviewer**: `menu_role_p.updt_*` 제거 권고 (INSERT/DELETE 전용)
  - **wtp-backend-engineer**: 매핑 단위 이력·감사 추적 가치 보존 — 매핑 엔티티는 BaseEntity 4 컬럼 보존 권장
- **결론**: 두 의견 상충 — PLAN 단계에서 결정 (가정 섹션 등록). 두 후보:
  - (A) BaseEntity 상속 — 표준 일관성, `updt_*` 미사용이지만 보유
  - (B) BaseEntity 미상속 + `rgstr_*` 만 명시 보유 — `indexing-and-migration.md §4.3` 이력 immutable 테이블 예외 패턴 차용

---

## 표준 사전 카탈로그

### 신규 표준 단어
(DB 컬럼 조합의 재료 — 의미의 최소 단위. 1차 정의: `swtp/.claude/rules/dict/standard-words.md` — ROOT)

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `url` | URL | 신규 | `standard-words.md` 미등록. 풀네임 `uniform resource locator`. 약어 자체가 보통명사화 — `io`·`srl` 선례 동일. 기본 데이터 도메인 미지정(`(조합)`) — 안건 2 `DOM_URL_200` REJECT 결과 반영 |

### 신규 표준 데이터 도메인
(값 형식 — SQL 타입·길이·Java 타입. 1차 정의: `swtp/.claude/rules/dict/standard-data-domains.md` — ROOT)

| 도메인 코드 | SQL 타입 | Java 타입 | NULL | 분류 | 결정 근거 |
|-----------|---------|---------|------|------|----------|
| (없음) | — | — | — | — | `DOM_URL_200` 등록 시도 → DBA 2차 승인 REJECT (`DOM_TEXT` 와 유사 충돌, 200자 신뢰 근거 부족). 등록 거부 이력에 기록 |

### 신규 표준 용어
(단어 + 데이터 도메인 → DB 컬럼명. 1차 정의: `backend/.claude/rules/dict/standard-terms.md`)

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `menu_id` | `menu`(비즈니스 도메인) + `id` | `DOM_ID_36` | 신규 | UUID 자동 생성 PK — `facility_id`·`instrument_id` 선례 동일. `Persistable` 미구현 |
| `menu_nm` | `menu` + `nm` | `DOM_NAME_100` | 신규 | **시스템 전체 UNIQUE 인덱스** — `facility_nm`·`instrument_nm` 선례 동일 패턴 (마스터도메인설계 ANALYZE1 Round 3) |
| `menu_url` | `menu` + `url` | (`VARCHAR(255)`, DOM 미지정) | 신규 | NULL 허용 (부모 그룹 메뉴는 URL 없음). `DOM_URL_200` REJECT 결과로 도메인 미지정 |
| `menu_desc` | `menu` + `desc` | `DOM_TEXT` | 신규 | `tag_desc` 선례 동일 패턴. `desc` 기존 재사용 |
| `parent_menu_id` | `parent` + `menu` + `id` | `DOM_ID_36` | 신규 | self-FK NULL 허용. `parent_facility_id` 선례 동일 패턴. 깊이 제한 N=3 (애플리케이션 레벨) |
| `disp_ord` | `disp` + `ord` | INTEGER | 기존 재사용 | 사용 테이블에 `menu_m` 추가 (기존 `facility_m`·`instrument_m`(예정)) |
| `user_role` | `user` + `role` | `DOM_CODE_20` | 기존 재사용 | 사용 테이블에 `menu_role_p` 추가 (기존 `user_m`). 매핑 테이블 컬럼으로 동일 의미·동일 값 집합(ADMIN/USER) 재사용 |

분류값 (3층 공통): **신규 / 기존 재사용 / 유사 충돌 / 폐기·통합**

---

## 신규 엔티티 / DB 컬럼

### `menu_m` (메뉴 마스터)

- 패키지: `com.mo.swtp.menu` (신설)
- suffix: `_m` (마스터)
- PK: `menu_id` `DOM_ID_36` UUID 자동 생성 (`@GeneratedValue(strategy = GenerationType.UUID)`, `Persistable` 미구현)
- 자기참조 self-FK: `parent_menu_id` `DOM_ID_36` NULL 허용. ON DELETE RESTRICT. 깊이 N=3 애플리케이션 검증
- BaseEntity 4 컬럼 상속 + `@EntityListeners(AuditingEntityListener.class)`
- 필드: `menu_id`·`menu_nm`(UNIQUE)·`menu_url`(NULL)·`menu_desc`(NULL)·`disp_ord`·`parent_menu_id`(NULL)·`use_yn`(YnType)
- 인덱스 후보:
  - `idx_menu_m_parent (parent_menu_id, disp_ord)` — 트리 정렬 조회용. 다만 단일 SELECT 후 메모리 트리 빌드 패턴이므로 실효성 낮음 — PLAN 단계에서 부담 없으면 유지

### `menu_role_p` (메뉴-권한 매핑)

- 패키지: `com.mo.swtp.menu` (동일)
- suffix: `_p` (명세) — `naming.md` 의 "태그 매핑 등 설정값" 정합. `pump_interlock_p`·`ai_drvn_mod_p` 선례
- 복합 PK: `menu_id` + `user_role` (`@EmbeddedId MenuRoleMappingId`)
- FK: `menu_id` → `menu_m.menu_id` ON DELETE CASCADE (마스터 삭제 시 매핑 자동 정리)
- BaseEntity 적용 정책: **PLAN 단계 결정** (안건 13 — A안 BaseEntity 상속 vs B안 `rgstr_*` 명시 보유)
- 인덱스: `(user_role, menu_id)` 별도 인덱스 미적용 (카디널리티 2 단독 인덱스 옵티마이저 Seq Scan 선호 — `DOM_YN` DDL 정책 §3.4 동일 판단). 권한별 메뉴 조회 핫패스 실증 후 재검토

### `LoginResponseDto` (응답 DTO 신설)

- 패키지: `com.mo.swtp.auth.dto`
- 필드: 토큰 정보 (`accessToken`·`refreshToken`·`accessExprDtm`·`refreshExprDtm`·`role`) + `menus: List<MenuTreeDto>`
- 사용처: `AuthController.login()` 응답
- 기존 `TokenResponseDto` 는 `/auth/refresh` 전용으로 유지

### `MenuTreeDto` (트리 응답 DTO)

- 패키지: `com.mo.swtp.menu.dto`
- 필드: `menuId`·`menuNm`·`menuUrl`·`menuDesc`·`dispOrd`·`children: List<MenuTreeDto>`
- 자기참조 재귀 DTO. JSON 직렬화 무한 루프 방지 — Jackson `@JsonManagedReference` 미사용 (양방향 X). 단방향 children 자동 직렬화

---

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 회의 결론 |
|----------|----------|
| `DOM_URL_200` 신규 등록 vs `DOM_TEXT` 유사 충돌 | DBA 2차 승인 REJECT — `menu_url VARCHAR(255)` + 도메인 미지정 처리. ROOT `standard-data-domains.md` 등록 거부 이력에 기록 |
| `menu_role_p.user_role` vs `user_m.user_role` 의미 동일 | 동일 물리명 재사용 — 표준 용어 표 §사용 테이블 컬럼에 `menu_role_p` 추가 |
| `_p`(명세) vs `_d`(상세) suffix 매핑 테이블 적합성 | `_p` 채택 — `naming.md` "태그 매핑 등 설정값" 정합 + `pump_interlock_p` 선례 |
| 신규 테이블 CHECK 제약 추가 vs `indexing-and-migration.md §3.2` 정책 | CHECK 제약 일체 미적용 — Java enum 단일 방어선 |
| AuthService → MenuQueryService 직접 의존 vs 패키지 의존 방향 단방향 | `LoginFacadeService` 신설 — `auth` 도메인 facade 가 `menu` 도메인 호출. 명시적 오케스트레이션 계층 |

---

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안

```
com.mo.swtp.menu (신설 패키지)
├── domain/
│   ├── Menu.java                          # @Inheritance 미사용. UUID PK. self-FK parent_menu_id (단방향)
│   ├── MenuRoleMapping.java               # @EmbeddedId MenuRoleMappingId
│   └── MenuRoleMappingId.java             # @Embeddable 복합 PK (menu_id + user_role)
├── repository/
│   ├── MenuRepository.java                # JpaRepository + MenuCustomRepository
│   ├── MenuCustomRepository.java
│   ├── MenuCustomRepositoryImpl.java      # 단일 SELECT + 메모리 트리 빌드
│   └── MenuRoleMappingRepository.java
├── service/
│   ├── MenuService.java                   # CRUD ADMIN-only. @Transactional
│   └── MenuQueryService.java              # 권한별 트리 조회. @Transactional(readOnly=true)
├── dto/
│   ├── MenuUpsertDto.java
│   ├── MenuTreeDto.java                   # children: List<MenuTreeDto> 자기참조
│   └── MenuRoleMappingUpsertDto.java
├── controller/
│   ├── MenuController.java                # @Tag("XX. 메뉴 관리"), ADMIN-only
│   └── MyMenuController.java              # @Tag("XX. 내 메뉴 조회"), /api/menus/me
└── exception/
    └── MenuErrorCode.java                 # implements ErrorCode, httpStatus 만

com.mo.swtp.auth.service (기존 패키지 보강)
└── LoginFacadeService.java                # AuthService + MenuQueryService 조립 → LoginResponseDto

com.mo.swtp.auth.dto (기존 패키지 보강)
└── LoginResponseDto.java                  # token + menus 신설
```

### DB 설계 변경

- 신규 SQL: `common/src/main/resources/db/init/V6_6__menu_master_table.sql`
- 테이블 2개: `menu_m`·`menu_role_p`
- CHECK 제약 일체 미적용 (Java enum 단일 방어선)
- COMMENT ON COLUMN 전면 의무 (BaseEntity 4 컬럼 표준 라벨 포함)
- self-FK ON DELETE RESTRICT + 애플리케이션 사전 검사 병행
- 매핑 ON DELETE CASCADE (마스터 삭제 → 매핑 자동 정리)
- `menu_nm` 시스템 전체 UNIQUE
- `menu_role_p` BaseEntity 상속 정책 PLAN 결정 (A vs B)

### 적용할 패턴

- `entity-patterns.md §기본 엔티티 패턴` — UUID 자동 생성 PK
- `entity-patterns.md §여부(Y/N) 필드 패턴` — `use_yn` `YnType` enum + `@Enumerated(EnumType.STRING)`
- `naming.md §Java 클래스 네이밍` — `MenuController`·`MenuService`·`MenuRepository`·`MenuUpsertDto`·`MenuErrorCode`
- `api-patterns.md §Repository 패턴` — `Custom*Repository` + `*Impl` 분리 (트리 빌드 로직)
- `api-patterns.md §Swagger/OpenAPI 패턴` — `@Tag`·`@Operation`·`@ApiResponses`
- `exception-patterns.md` — `MenuErrorCode` `httpStatus(int)` 만 선언 (자동 차단 훅 통과)
- `coding-discipline.md §2.1` — 메뉴 트리 빌드 50줄 초과 시 `MenuTreeAssembler` (`{도메인명}Comp`) 분리

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 자기참조 메뉴 깊이 N=3 (애플리케이션 검증) | 가정 | 운영 검증 후 조정 가능. `MAX_MENU_DEPTH = 3` 상수로 관리 |
| `menu_role_p` BaseEntity 상속 vs `rgstr_*` 직접 보유 | 미해결 | PLAN 단계 결정. 안건 13 의 두 후보 (A·B) 중 1택 |
| 자기참조 부모 비활성(`use_yn = N`) 시 자식 CASCADE 정책 | 미해결 | PLAN 단계 결정. CASCADE 비활성 vs 부모 활성 사전 검증 (wtp-domain-expert 권고 — 고아 자식 노출 방지) |
| AI 운전 모드 전환 화면 메뉴의 접근 role 정책 | 미해결 | PLAN 단계 시드 데이터 결정. USER 가 AI 운전 모드 변경 가능 시 인터록 우회 경로 위험 (`ot-integration.md §5`) — ADMIN-only 권고 |
| 권한 변경 시 stale JWT 토큰 무효화 정책 | 미해결 → 별도 사이클 분리 | 사용자 결정(2026-05-06): 본 작업 범위 제외. 인증 흐름 전반 보안 명세로 별도 ANALYZE. RESULT §후속 작업 권고에 명시 의무 |
| `MenuRoleMappingId` 복합 PK 클래스 네이밍 | 미해결 | 기존 선례(`PumpCmbnDetailId`) 와 동일 패턴 적용 — PLAN 단계에서 코드 작성 시 확정 |
| `idx_menu_m_parent (parent_menu_id, disp_ord)` 인덱스 도입 여부 | 미해결 | 단일 SELECT 패턴 채택 시 실효성 낮음. PLAN 단계에서 부담 없으면 유지, 우선순위 낮으면 미생성 |

분류값: 가정 / 미해결 / 결정 (미해결은 PLAN 단계에서 결정으로 변환)

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `url` 표준 단어 신규 등록 (한글 논리명: URL, 풀네임: uniform resource locator, 기본 데이터 도메인: `(조합)`, 등록일: 2026-05-06, 비고: 안건 2 `DOM_URL_200` REJECT 결과로 도메인 미지정)
- [x] `swtp/.claude/rules/dict/standard-data-domains.md` — `## 폐기 이력` 하위 신규 §등록 거부 이력 섹션 추가 + `DOM_URL_200` 거부 기록 (사유: `DOM_TEXT` 유사 충돌 + 200자 신뢰 근거 부족, 거부일: 2026-05-06, wtp-dba-reviewer 2차 승인 REJECT)
- [x] `swtp/.claude/rules/dict/domain-abbreviations.md` — `menu` 비즈니스 도메인 약어 도입 예정 섹션에 등록 (풀네임: menu, 설명: 권한 메뉴 마스터, `com.mo.swtp.menu` 패키지 도입 전제, 도입일: PLAN approved 후)
- [x] `backend/.claude/rules/dict/standard-terms.md` — `menu_id` 신규 등록 (`menu` + `id`, `DOM_ID_36`, `menu_m` PK, UUID 자동 생성)
- [x] `backend/.claude/rules/dict/standard-terms.md` — `menu_nm` 신규 등록 (`menu` + `nm`, `DOM_NAME_100`, `menu_m`, 시스템 전체 UNIQUE)
- [x] `backend/.claude/rules/dict/standard-terms.md` — `menu_url` 신규 등록 (`menu` + `url`, VARCHAR(255), `menu_m`, NULL 허용 — 부모 그룹 메뉴 URL 없음, DOM 미지정)
- [x] `backend/.claude/rules/dict/standard-terms.md` — `menu_desc` 신규 등록 (`menu` + `desc`, `DOM_TEXT`, `menu_m`, NULL 허용)
- [x] `backend/.claude/rules/dict/standard-terms.md` — `parent_menu_id` 신규 등록 (`parent` + `menu` + `id`, `DOM_ID_36`, `menu_m`, self-FK NULL 허용, 깊이 N=3 애플리케이션 검증)
- [x] `backend/.claude/rules/dict/standard-terms.md` — `disp_ord` 용어 사용 테이블 갱신 (기존 `facility_m`·`instrument_m`(예정) 에 `menu_m` 추가)
- [x] `backend/.claude/rules/dict/standard-terms.md` — `user_role` 용어 사용 테이블 갱신 (기존 `user_m` 에 `menu_role_r` 추가 — ANALYZE2 R-1 결과로 `menu_role_p` → `menu_role_r` 정정, 비고: N:M 매핑 테이블 컬럼 재사용, 값 집합 ADMIN/USER 동일)

---

## 산출물

- [계획안](../../../plan/20260504/권한메뉴/PLAN1.md) (ANALYZE approved 후 자동 생성)

---
status: approved
created: 2026-05-20
updated: 2026-05-20
---
# 송수펌프제어분석 — 7번 섹션 (시설 예측 데이터 표출) — REVIEW1

## 관련 결과
- [결과](../../../results/20260518/송수펌프제어분석-7번섹션/RESULT1.md)

## 리뷰 범위

송수펌프제어분석-7번섹션 PLAN1 → TASK1 → 구현 → RESULT1 사이클의 코드·DDL·테스트·문서 산출물 전체. 13개 신규 파일 + 2개 수정 파일.

리뷰 절차 — `wtp-backend-engineer` · `wtp-dba-reviewer` · `wtp-domain-expert` 3 에이전트 병렬 단답 점검 후 발견 사항 통합 + 블로커 해소.

## 발견 사항

ROOT [`coding-discipline.md` §2.1·§2.5·§3·§4](../../../../.claude/rules/coding-discipline.md) 카테고리 기준.

### 카테고리: 복잡도 과잉 (Overengineering)

| 항목 | 위치 | 심각도 | 점검 결과 |
|------|------|------|---------|
| 메서드 50줄 / 추상화 3단 / DTO 상속 3단 초과 | 전체 | — | **위반 없음**. `FacilityPredictionService.findFacilityPrediction` 37줄. `mapFlwmtrPrediction`·`mapPumpPrediction` private 분해로 본문 단순화. DTO 상속 무 (`BaseAuditResponseDto` 미적용 — 실시간/예측 통지성 응답 분류). |
| §2.5 면책 인용 근거 표기 | `TagPredictionCustomRepositoryImpl` | — | **합격**. 클래스 Javadoc + 인라인 주석 양쪽 `query-tuning.md §2 + coding-discipline.md §2.5` 명기 |

### 카테고리: 도메인 룰 위반

`wtp-domain-expert` 점검 결과 (4영역 무접촉 ANALYZE2 §5b 게이트 통과 근거 실물 정합):

| 영역 | 심각도 | 점검 결과 |
|------|------|---------|
| 알람 4단계 (`ot-integration.md §5`) | — | **비해당 정당**. `quality_cd` 컬럼 구조적 부재 + 알람 임계 비교 경로 부재 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | — | **비해당 정당**. 조회 전용 4-step + `sendControlCommand`·`pump_interlock_p` 평가 부재 |
| AI 운전 모드 (`ot-integration.md §5`) | — | **비해당 정당**. `ai_drvn_mod_p`·`ai_mode_cd`·`ai_drvn_mod_h` 미참조. `oprtngType` `@Schema description` "AI 운전 모드와 무관" 명기로 혼동 차단 |
| 이력 기록 의무 (`ot-integration.md §5`) | — | **비해당 정당**. `ai_drvn_mod_h.transition_reason`·`pump_ctrl_h` 무관 — `predc_1m_h` 는 AI 산출 적재 저장소 (감사 이력 아님) |
| `predcIsRunning` Boolean 변환 결합 위험 | — | **없음**. `toBoolean(BigDecimal)` 단순 변환, 알람/인터록/운전 모드 어느 경로와도 결합 없음 |
| 시설 종류별 별도 평가 의무 위반 | — | **없음**. 조회 전용 + `EquipType.PUMP` 필터 명시 |

### 카테고리: 보안

| 항목 | 심각도 | 점검 결과 |
|------|------|---------|
| JWT secret / DB 자격 하드코딩 | — | **없음**. `application-common.yml` 신규 키 `opt.prediction.match-window-minutes: 5` 만 추가 |
| OWASP Top 10 (SQL Injection · XSS 등) | — | **위반 없음**. native SQL 의 모든 동적값은 `setParameter(...)` 바인딩 (`:tagSrlNos`·`:windowMinutes`) |
| 인증 진입점 변경 | — | **없음**. `/prediction` 엔드포인트는 `JwtAuthenticationFilter` 기본 보호 경로 |

### 카테고리: 성능

`wtp-dba-reviewer` 점검 결과:

| 항목 | 심각도 | 점검 결과 |
|------|------|---------|
| 시계열 파티셔닝 (월 RANGE, 6개월 선행) | — | **합격**. `partitioning-and-retention.md §1·§2` 정합 |
| 인덱스 카디널리티 순서 (`tag_srl_no` 선행, `predc_dtm` 후위) | — | **합격**. `indexing-and-migration.md §1` 정합 |
| 인덱스 방향 ASC (근접 대칭 범위 스캔) | — | **합격**. `rawdata DESC` 와 의도된 혼재 — DDL 주석 + Javadoc 명기 |
| BRIN 미생성 근거 | — | **합격**. 본 사이클 INSERT 경로 부재 + 복합 B-Tree 후위 컬럼 역할 분리 |
| 시계열→마스터 FK 금지 | — | **합격**. `tag_srl_no` 논리 참조만 |
| immutable 이력 예외 (`BaseEntity` 미상속) | — | **합격**. `indexing-and-migration.md §4.3` 표준 라벨 |
| COMMENT ON COLUMN 의무 | — | **합격**. 6컬럼 전부 (`check-ddl-column-comment.sh` 훅 정합) |
| N+1 / 슬로우 쿼리 / 파티션 프루닝 / LATERAL 동작 | — | **합격**. `CROSS JOIN LATERAL` = 태그별 1회 인덱스 스캔 (JPA 루프 N+1 아님). `BETWEEN target±windowMinutes` 로 파티션 프루닝 작동 |
| §2.5 면책 인용 근거 | — | **합격**. 명기 완전 |

### 카테고리: 테스트 누락

`test-strategy.md §5.2` 도메인 시나리오 필수 3종 (알람 4단계 · 인터록 · 운전 모드) — 본 사이클은 4영역 무접촉이므로 적용 외. 본 사이클의 시나리오 테스트:

| 시나리오 | 검증 결과 |
|---------|---------|
| 시설 미존재 → `FACILITY_NOT_FOUND` | PASS |
| 비활성 시설 → `FACILITY_NOT_FOUND` | PASS |
| 활성 instrument 없으면 빈 List | PASS |
| 유량계 FRI/PRI 예측값 분리 매핑 | PASS |
| OPS 예측 1.0/0.0 → `predcIsRunning` true/false | PASS |
| 윈도우 밖 태그 null 매핑 | PASS |

통합 테스트 3 시나리오는 `@Disabled` (PostgreSQL 전제). 본 RESULT §테스트 결과 정합.

### 카테고리: 기타 (해소된 블로커 + 잔존 권고/참고)

`wtp-backend-engineer` 첫 점검 결과 블로커 1건 + 권고 1건 + 참고 1건 지적. 블로커는 본 REVIEW 단계에서 해소 + 권고/참고는 잔존:

| 항목 | 위치 | 심각도 (초기) | 처리 | 사후 상태 |
|------|------|------------|------|---------|
| `@Repository` 어노테이션 누락 | `TagPredictionCustomRepositoryImpl` | 높음 (초기) | **해소** — `@Repository` 추가 commit (구현 시점 회귀). RawData 선례는 미명시이나 안전 강화 차원 채택 | 합격 |
| `TagPredictionId.@EqualsAndHashCode` Lombok 미활용 (TASK 체크박스와 불일치) | `TagPredictionId.java` | 중간 | **현행 유지** — `RawDataId` 선례 동일 (수동 `equals/hashCode`). 시계열 복합키 패턴 일관성 우선 | 권고만 RESULT 명기 |
| `FacilityPredictionDto.@NoArgsConstructor(PROTECTED)` 미사용 (private) | `FacilityPredictionDto.java` | 낮음 | **현행 유지** — `FacilityStateDto`·`PumpStateDto` 등 기존 응답 DTO 선례 정합 (private 사용) | 참고만 |
| SCADA 단절 태그 누락 시 서비스 로깅 정책 미결정 | `FacilityPredictionService` | 낮음 | **사이클 2 인계** — 운영 시 BAD 품질 코드 발생 시의 응답 누락 로깅 정책은 별도 사이클 결정 | 참고만 |
| 사이클 2 재평가 트리거 Javadoc 인계 미명기 | `TagPrediction.java` Javadoc | 낮음 | **사이클 2 인계** — `ai_drvn_mod_p` 재도입 시 4영역 재평가 의무. PLAN1 §가정 표 마지막 행에 이미 명시되어 있으나 엔티티 Javadoc 보강 권고 (운영 추적성) | 참고만 |

## 개선 제안

본 REVIEW 에서 블로커는 모두 해소. 잔존 권고/참고 항목 4건은 다음과 같이 처리:

1. **RawDataCustomRepositoryImpl 에도 `@Repository` 추가 검토** — 본 사이클은 작업 범위 외이나, `*CustomRepositoryImpl` 패턴 통일성 강화 차원의 후속 작업. 별도 작은 사이클 (`/dev` Small) 또는 차후 작업에 인계 권고
2. **`TagPredictionId` `@EqualsAndHashCode` vs 수동 패턴 일관 정책 명문화** — 시계열 복합키 ID 클래스 패턴은 `RawDataId`·`TagPredictionId` 모두 수동 구현. `entity-patterns.md` 또는 `naming.md` 에 시계열 복합키 ID 표준 패턴 명문화 검토 (별도 `/dev:analyze` 안건 후보)
3. **SCADA 단절 / 예측 결측 시 서비스 로깅 정책** — 사이클 2 AI 추론 파이프라인 도입 시 운영 정책으로 결정 (PLAN1 §가정 표 미해결 항목 인계)
4. **`TagPrediction.java` Javadoc 재평가 트리거 인계 주석 추가** — 운영 추적성 강화 차원의 미세 권고. 본 사이클에서 적용해도 무방하나, 다음 commit/PR 단계에서 통합 가능

## 결론

- **블로커 (높음): 0건** — 초기 1건은 본 REVIEW 단계에서 `@Repository` 추가로 해소
- 권고 (중간): 0건 — 초기 1건은 RawDataId 선례 정합으로 현행 유지 (RESULT/REVIEW 명기로 추적성 확보)
- 참고 (낮음): 3건 — 모두 본 사이클 외 후속 작업 인계 항목 (개선 제안 §1·§3·§4)

본 사이클 PLAN1 §부록 §wtp-domain-expert · §wtp-dba-reviewer 의 블로커 0건 + 권고 2건 반영 상태와 구현 결과가 정합. 단위 테스트 6건 PASS + Spring ApplicationContext 정상 로드 확인 + 컴파일 BUILD SUCCESSFUL.

→ Fix Cycle 진입 사유 없음. `/dev:commit` 안내 단계로 진행 가능.

---
status: approved
created: 2026-05-20
updated: 2026-05-20
---
# 주파수 측정 유형 enum 정식 등록 — 구현 계획

## 목적

`TagMeasurementType` enum 에 주파수 측정 유형 `FQI("주파수", "Hz")` 신규 등재 + 양쪽 DB (local + dev) 의 임시 `SPI` 코드값 4건씩을 `FQI` 로 마이그레이션. ANALYZE1 결정 사항 (안건 1~5) 의 구현 실행.

## 배경

- ANALYZE: [`../../../analyze/20260520/주파수측정유형등록/ANALYZE1.md`](../../../analyze/20260520/주파수측정유형등록/ANALYZE1.md) (status: approved, 2026-05-20)
- ROOT 룰 갱신 4건 ANALYZE 단계에서 완료 (standard-words.md `frq` / standard-terms.md `tag_se_cd` 비고 / ot-integration.md §3 표 + SSOT 안내)
- 본 PLAN 은 enum 코드 + DDL + DB UPDATE + 테스트 갱신의 구현 실행 계획

## 범위

| 모듈 | 변경 |
|------|------|
| `common` | `TagMeasurementType` enum 1행 추가 + Javadoc 갱신 + `TagMeasurementTypeTest` `hasSize(7→8)` + FQI 단위 검증 행 |
| `common` (DDL) | `db/migration/V8_1__tag_patch.sql` 신규 작성 (UPDATE + COMMENT 갱신) |
| `backend/docs/ddl` | `tag.sql` 하단 ALTER/UPDATE 누적 (§5.3 양쪽 동시 갱신 의무) |
| 양쪽 DB | `tag_m` SPI 4건 → FQI UPDATE (local + dev 각 4건) |
| `api`·`scheduler` | 영향 없음 (Service 4종 `EnumSet.of(...)` 화이트리스트 패턴이라 enum 신규값 자동 미포함 — wtp-backend-engineer 안건 5 결론) |

## 구현 방향

### enum 추가 (Phase 1)

`common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java` 의 7행 enum 정의 다음에 `FQI("주파수", "Hz")` 1행 추가. Lombok `@Getter @RequiredArgsConstructor` 패턴 100% 유지. Javadoc 도입 이력 항목 1행 추가 (`주파수측정유형등록 ANALYZE1 (2026-05-20) — FQI 신규 추가 (인버터 펌프 운전 주파수)`).

### DDL 패치 작성 (Phase 2)

`common/src/main/resources/db/migration/V8_1__tag_patch.sql` 신규 파일. 본문:
- `UPDATE tag_m SET tag_se_cd = 'FQI' WHERE tag_se_cd = 'SPI'` (12행 미만 소량, `ROW EXCLUSIVE` 락 밀리초 단위)
- `COMMENT ON COLUMN tag_m.tag_se_cd IS '태그 측정 유형 코드 (DOM_CODE_20 — TagMeasurementType enum 매핑 FRI/PRI/LEI/PWI/RMS/OPS/VOI/FQI 8종, ot-integration.md §3 정합 — 단위 매핑은 enum 의 unit 필드로 흡수)'` (8종 열거 갱신)
- 파일 상단 주석: 작업 배경 + 역방향 UPDATE 롤백 SQL 명시 (wtp-dba-reviewer 권고 낮음)

`backend/docs/ddl/tag.sql` 하단에 `-- =====` 구분선 + V8_1 패치 본문 누적 (양쪽 동시 갱신).

### 테스트 갱신 (Phase 3)

`common/src/test/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementTypeTest.java` 의 `hasSize(7)` → `hasSize(8)`. FQI 검증 신규 메서드 추가:
```java
@Test
void FQI_주파수_단위는_Hz다() {
    assertThat(TagMeasurementType.FQI.getDescription()).isEqualTo("주파수");
    assertThat(TagMeasurementType.FQI.getUnit()).isEqualTo("Hz");
}
```

### DB 마이그레이션 (Phase 4)

양쪽 DB (local + dev) 적용. **사용자 결정 사항 Q3** (PLAN 단계 결정 위임) — 적용 방식 선택지:
- (a) Claude 세션 직접 적용 (이전 시드 데이터 INSERT 사이클 동형) — 단순, 즉시 정합
- (b) 운영자 `psql -f V8_1__tag_patch.sql` 적용 (wtp-dba-reviewer 권고 운영본 경유)

본 사이클은 시드 데이터 정정 목적이고 이전 사이클이 (a) 패턴 채택했으므로 (a) 권장 (PLAN approved 시점에 사용자 최종 확인).

### 빌드 검증 (Phase 5)

`./gradlew.bat clean build` 전체 통과 확인. 특히 `:common:test` 의 `TagMeasurementTypeTest` GREEN + 신규 메서드 GREEN.

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 명령 |
|------|---------|
| `TagMeasurementType` enum 값 8종 (FQI 포함) | `./gradlew.bat :common:test --tests TagMeasurementTypeTest` BUILD SUCCESSFUL |
| FQI 단위 = "Hz" | `assertThat(TagMeasurementType.FQI.getUnit()).isEqualTo("Hz")` 신규 테스트 PASS |
| `V8_1__tag_patch.sql` 신규 파일 존재 + 본문 UPDATE + COMMENT 갱신 포함 | `ls common/src/main/resources/db/migration/V8_1__tag_patch.sql` + `grep "FQI" V8_1__tag_patch.sql` 매칭 ≥ 2 (UPDATE 1 + COMMENT 1) |
| `docs/ddl/tag.sql` 하단 패치 누적 (§5.3 양쪽 동시 갱신) | `tail -30 docs/ddl/tag.sql` 의 마지막 블록에 V8_1 본문 포함 확인 |
| 양쪽 DB tag_m 의 tag_se_cd = 'FQI' 행 수 = 4 | `SELECT COUNT(*) FROM tag_m WHERE tag_se_cd = 'FQI'` local·dev 양쪽 = 4 일치 |
| 양쪽 DB tag_m 의 tag_se_cd = 'SPI' 행 수 = 0 (잔재 제거) | `SELECT COUNT(*) FROM tag_m WHERE tag_se_cd = 'SPI'` 양쪽 = 0 |
| DDL COMMENT 본문 8종 열거 | `psql \d+ tag_m` 또는 `grep "FQI" V8_1__tag_patch.sql` 매칭 |
| `check-ddl-column-comment.sh` 훅 통과 | `Write`·`Edit` 후 hook PASS (자동 차단 없음) |
| 전체 빌드 통과 | `./gradlew.bat clean build` BUILD SUCCESSFUL |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| ANALYZE Q2: 인버터 펌프(`drive_type_cd = INVERTER_DRIVE`) 한정 주파수 측정 — Service 분기 시 필터 강제? | 가정 → 결정 | **본 사이클 미적용** — Service 4종 `EnumSet.of(...)` 화이트리스트 패턴이 신규 enum 자동 미포함 (wtp-backend-engineer 안건 5 결론). FQI 측정값 활용 Service 가 향후 도입될 시점에 PLAN 명시 (현 사이클 코드 변경 0) |
| ANALYZE Q3/Q4/Q6: 주파수 인터록 연계 / AI 자동 setpoint / 알람 임계값 | 미해결 → 보류 | 사이클 2 (`ot_integration` 별도 ANALYZE) 위임 — `ot-integration.md §5` 전체 보류 상태 정합 |
| ANALYZE Q5: Hz 단위 환산 (rad/s, RPM 등) 불필요 | 가정 → 결정 | 본 사이클 미적용 — `TagMeasurementType.FQI.getUnit()` = "Hz" 고정. 한국 지자체 운영 시나리오 환산 요구 없음 |
| **Q3 (PLAN 신규)**. 양쪽 DB UPDATE 적용 방식 — Claude 세션 직접 적용 vs 운영자 `psql -f` 적용 | **결정** | **(a) Claude 세션 직접 적용 확정** (사용자 결정, 2026-05-20). 이전 시드 INSERT 사이클 동형 — TASK Phase 4 에서 local + dev 양쪽 MCP 도구로 UPDATE 4건씩 실행 |
| **Q4 (PLAN 신규)**. `V8_1__tag_patch.sql` 의 UPDATE 가 `WHERE tag_se_cd = 'SPI'` 만 매칭 — 향후 동일 DB 에 운영 환경 재적용 시 멱등성 | 가정 → 결정 | 멱등성 보장 — SPI 잔재 0건 환경에서 재실행 시 UPDATE 0행 (오류 없음). `COMMENT ON COLUMN` 도 멱등 (덮어쓰기) |
| **Q5 (PLAN 신규)**. `V8_1__tag_patch.sql` 의 COMMENT 갱신은 `V8__tag.sql` 의 기존 COMMENT 와 충돌? | 가정 → 결정 | PostgreSQL `COMMENT ON COLUMN` 은 마지막 실행값으로 단일 정의 (충돌 없음). V8 → V8_1 순차 적용 시 V8_1 의 8종 열거 COMMENT 가 최종 상태 |

## 제외 사항

- **AI 운전 모드 / 인터록 / 알람 4단계 임계값 신설** — `ot-integration.md §2·§5` 전체 보류 상태. 본 사이클 무관. 향후 사이클 2 (`ot_integration` 별도 ANALYZE) 결정.
- **Service 분기 로직 (FQI 측정값 활용 Service)** — 본 사이클은 enum 정식 등재 + DB 정정만. FQI 측정값 화면 표출·예측 모델 입력 등은 향후 별도 사이클.
- **`predc_frq_val`·`frq_raw_val` 등 신규 컬럼 도입** — `frq` 표준 단어 등록 완료 (재료 확보) 했으나 컬럼 도입 자체는 별도 ANALYZE 필요.
- **`rawdata_1m_h` 의 실제 주파수 측정 데이터 INSERT** — 시드 데이터 INSERT 는 별도 작업. 본 사이클은 `tag_m` 마스터 정정 한정.

## 도메인 모델

본 사이클은 신규 엔티티·테이블·필드 도입 없음. enum 값 1건 추가만:

| 엔티티 / 테이블 | 역할 | 주요 필드 변경 |
|---|---|---|
| `TagMeasurementType` enum (`common.tag.domain.enumtype.TagMeasurementType`) | `tag_m.tag_se_cd` 매핑 enum | **신규 값 1행 추가**: `FQI("주파수", "Hz")` (8번째 enum 값). `Tag` 엔티티·`tag_m` 테이블 정의 변경 없음 |

## DB 설계 변경

| 변경 대상 | 변경 유형 | 적용 정책 |
|---|---|---|
| `tag_m` 데이터 (양쪽 DB 각 4행) | UPDATE: `tag_se_cd` SPI → FQI | `V8_1__tag_patch.sql` 신규 분리 (§5.4 V{N} 동결 정책). 12행 미만 소량 — `ROW EXCLUSIVE` 락, 밀리초, `CONCURRENTLY` 불필요 (wtp-dba-reviewer 안건 4 결정) |
| `tag_m.tag_se_cd` 컬럼 COMMENT | 본문 갱신: 7종 → 8종 열거 | `V8_1__tag_patch.sql` 내 `COMMENT ON COLUMN` 1문 (`check-ddl-column-comment.sh` 훅은 존재 여부만 검사 — 내용 정합성 수동 확인 의무, wtp-backend-engineer 안건 5 결론) |
| `docs/ddl/tag.sql` | 사람이 읽는 SSOT 사본 동기화 | §5.3 양쪽 동시 갱신 의무 — V8_1 본문 누적 |

### 무중단 마이그레이션 전략

- **스키마 변경 0건**: 컬럼 타입·길이·NULL 정책 변경 없음 (VARCHAR(20) 자유형). `ACCESS EXCLUSIVE` 락 미발생.
- **데이터 UPDATE 12행 미만**: `ROW EXCLUSIVE` 락, 밀리초 단위 완료.
- **롤백 전략**: `UPDATE tag_m SET tag_se_cd = 'SPI' WHERE tag_se_cd = 'FQI'` 역방향 단일 UPDATE. 별도 백업 불필요. `V8_1__tag_patch.sql` 상단 주석에 역방향 SQL 명시 (wtp-dba-reviewer 권고 낮음).
- **멱등성**: SPI 잔재 0건 환경에서 재실행 시 UPDATE 0행 (오류 없음). COMMENT 멱등 (PostgreSQL `COMMENT ON COLUMN` 마지막 실행값으로 단일 정의).

### Phase 분해 (선택적 — TASK 단계에서 확정)

| Phase | 작업 | 검증 |
|-------|------|------|
| 1 | enum 추가 + Javadoc | `:common:compileJava` SUCCESS |
| 2 | DDL 패치 작성 (V8_1 + docs/ddl 양쪽) | `check-ddl-column-comment.sh` 훅 PASS |
| 3 | 테스트 갱신 | `:common:test --tests TagMeasurementTypeTest` PASS |
| 4 | 양쪽 DB UPDATE 적용 | `SELECT COUNT(*) FROM tag_m WHERE tag_se_cd = 'FQI'` = 4 양쪽 |
| 5 | 빌드 전체 검증 | `./gradlew.bat clean build` BUILD SUCCESSFUL |

## 예상 산출물

- [태스크](../../../tasks/20260520/주파수측정유형등록/TASK1.md)

## 부록: 도메인/DB 검토 결과 (ANALYZE 회의 결과 인용으로 갈음)

- **wtp-dba-reviewer** (ANALYZE 안건 4): 블로커 1건 (V8_1 분리 의무) — PLAN §DB 설계 변경에 반영 완료. 권고(중간) 1건 (docs/ddl 동기화) — PLAN §범위·§DB 설계 변경에 반영 완료. 권고(낮음) 1건 (롤백 SQL 주석) — PLAN §무중단 마이그레이션 전략에 반영 완료.
- **wtp-backend-engineer** (ANALYZE 안건 5): 블로커 0건. 권고(중간) 2건 (Test `hasSize(7→8)`·DDL COMMENT 갱신) — PLAN §구현 방향 Phase 1·2·3 에 반영 완료. 권고(낮음) 2건 (PLAN 가정 섹션·Javadoc 이력) — PLAN §가정 및 미해결 질문·§구현 방향 Phase 1 에 반영 완료.
- **wtp-domain-expert** (ANALYZE 안건 3): 블로커 0건 (가정 섹션 기재 의무 충족 — Q3/Q4/Q6 사이클 2 위임 명시). 권고(중간) 1건 (`ot-integration.md §3` 결측 대체값 표 갱신) — ANALYZE 룰 갱신 지시서 - [x] 완료로 해소.
- **wtp-glossary-manager** (ANALYZE 안건 1·2): `frq` 표준 단어 + FQI enum 코드값 결정 — ANALYZE 룰 갱신 지시서 - [x] 완료.

본 PLAN 단계에서는 추가 검토 게이트 호출 없이 ANALYZE 회의 결과를 그대로 인용한다 (안건이 동일 — 중복 검토 회피, `dev:plan.md` §5b "같은 항목 중복 검증 회피" 원칙 정합).

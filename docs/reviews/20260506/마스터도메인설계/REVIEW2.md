---
status: approved
created: 2026-05-06
updated: 2026-05-06
---
# 마스터도메인설계 — 사이클 2 코드 리뷰 (RawData immutable 정합 정렬)

## 관련 결과
- [결과](../../../results/20260506/마스터도메인설계/RESULT2.md)
- [이전 리뷰](../../20260503/마스터도메인설계/REVIEW1.md) (블로커 1 — 본 사이클에서 해소 검증 대상)
- [계획안](../../../plan/20260506/마스터도메인설계/PLAN2.md)
- [태스크](../../../tasks/20260506/마스터도메인설계/TASK2.md)

## 리뷰 범위

본 사이클의 변경 2건만 검토 대상. 다른 변경 (사이클 1 의 facility/instrument/tag/raw/menu 디렉토리·V6_1~V6_6·pump 도메인 변경·AI 도메인 변경) 은 사이클 1 의 REVIEW1 에서 이미 검토 완료.

| 카테고리 | 건수 | 파일 |
|---------|------|------|
| 엔티티 수정 (immutable 검증) | 1 | `RawData.java` |
| 단위 테스트 신규 | 1 | `RawDataTest.java` |

리뷰 점검 절차:
- **wtp-backend-engineer 호출** — Spring Boot 4·JPA·Lombok 정합성 + 정량 기준 (메서드 50줄·추상화 3단·DTO 상속 3단) + ROOT `coding-discipline.md §3` 정밀한 수정 + `test-strategy.md §1` 단위 테스트 패턴
- **wtp-domain-expert 호출** — 블로커 1 (REVIEW1) 해소 검증 + `ot-integration.md §3` QUALITY 코드 처리 정책 정합 + `db/partitioning-and-retention.md §1` BaseEntity 4 적용 정책 정합 + REVIEW1 미해소 권고/참고의 사이클 2 영향

**ANALYZE-룰 정합성 점검 생략** — 사이클 2 는 별도 ANALYZE 부재 (PLAN2 §부록에 Fix Cycle 알고리즘에 따라 ANALYZE 재진입 불요 명시). 사이클 1 의 ANALYZE1 룰 갱신 지시서 9건은 직전 커밋으로 모두 반영 완료 — 본 점검 대상 외.

## 발견 사항

> 카테고리: 복잡도 과잉 / 도메인 룰 위반 / 보안 / 성능 / 테스트 누락 / 기타. 심각도: 높음(블로커) / 중간(권고) / 낮음(참고).

### 높음 (블로커) — 0건

본 사이클에서 신규 발견된 블로커 0건. **REVIEW1 블로커 1건 (`changeQualityCd` vs PLAN1 §도메인 룰) 해소 ✓** — wtp-domain-expert 단정 결과.

### 중간 (권고) — 0건

본 사이클에서 신규 발견된 중간 권고 0건. REVIEW1 의 권고 1·3 은 본 사이클 범위 외 (PLAN2 §제외 사항·§가정 명시).

### 낮음 (참고) — 3건

| 심각도 | 카테고리 | 위치 | 설명 |
|-------|---------|------|------|
| 낮음 | 기타 (코드 스타일) | `common/src/main/java/com/mo/swtp/raw/domain/RawData.java:182` | `@PostLoad` 어노테이션이 `@jakarta.persistence.PostLoad` 완전 한정명 인라인으로 사용되어 있으나, `@PrePersist`·`@PreUpdate` 는 상단 `import jakarta.persistence.PrePersist`·`import jakarta.persistence.PreUpdate` 방식. 동일 파일 내 어노테이션 import 방식 통일 권고 — 기능 영향 없음. **본 사이클 변경 외**(사이클 1 신규 작성분의 잔재) — 사이클 1 잔여 항목으로 식별, RESULT2 §계획 외 변경 = "없음" 정합 유지를 위해 본 사이클에서 수정하지 않음. 별도 코드 정합 사이클로 분리 가능. |
| 낮음 | 기타 (룰-코드 정합성) | `swtp/backend/.claude/rules/db/partitioning-and-retention.md §1` 65줄 | 룰 원문은 INSERT-only 3컬럼 (`tag_srl_no·acq_dtm·raw_val`) 으로 열거. 사이클 2 에서 `quality_cd` 가 4번째 INSERT-only 컬럼으로 확정되었으나 룰 파일은 3컬럼 그대로 유지. RESULT2 §룰 갱신 영향 없음 섹션이 "룰 본문 변경 0건" 명시 + 코드 Javadoc 은 4컬럼 반영 → 기능 정합 달성. 룰 파일 열거를 4컬럼으로 보강하면 향후 `rawdata_1m_h` 패턴 재사용 시 오해 방지 가능 — 별도 chore 룰 정렬 작업으로 검토. **차단 사유 아님** (PLAN2 §제외 사항에 PLAN1 본문 변경 명시 근거 있음). |
| 낮음 | 기타 (REVIEW1 권고 3 이월) | `common/src/main/java/com/mo/swtp/raw/domain/RawData.java:163~177` (`onPreUpdateValidateImmutable`) | REVIEW1 권고 3 (§2.5 면책 인용 주석) 이 사이클 2 에서도 그대로 이월. 현 메서드 본문 약 18줄 (50줄 미만) 로 §2.1 정량 기준 미달 → 블로커 격상 아님. PLAN2 §가정 미해결 질문에 본 항목이 의도적 미반영으로 결정 명시. RESULT2 §기술 부채·후속 작업 식별 2번에 시계열 immutable 검증 패턴 재사용성 검토와 함께 후속 작업으로 식별 — 도구적 인지 확보. |

## 개선 제안

1. **(낮음 1·3 결합) `RawData @PostLoad` import 스타일 통일 + §2.5 면책 인용 주석** — 별도 코드 정합 사이클에서 두 항목을 함께 처리. import 통일은 단순 정렬, §2.5 면책 인용 주석은 패턴 재사용 시점 (`pump_ctrl_h`·`pump_predc_h`·`ai_drvn_mod_h` 시계열 immutable 검증 패턴 격상 ANALYZE) 에 함께 도입.

2. **(낮음 2) `db/partitioning-and-retention.md §1` 65줄 INSERT-only 컬럼 열거 4컬럼 정렬** — `tag_srl_no·acq_dtm·raw_val` → `tag_srl_no·acq_dtm·raw_val·quality_cd`. 사이클 2 의 immutable 강제 결정과 PLAN1 §도메인 룰 ("`corr_val` 만 갱신 허용") 의 정합 강화 목적. 별도 chore 룰 정렬 작업.

3. **(REVIEW1 미해소 항목 정리)**
   - REVIEW1 권고 1 (CORS): `dev/application.yml` 의 `localhost:5173,localhost:3000` 추가는 별도 chore 사이클 분리 (사용자 결정 2026-05-06).
   - REVIEW1 참고 1 (`RawDataId @AllArgsConstructor`): 사이클 2 변경 영향 없음 — 별도 검토 대상.
   - REVIEW1 참고 2 (`PumpControlIntegrationTest` SKIP): 실 PostgreSQL E2E 별도 사이클.

## 결론

- **블로커 0건** — REVIEW1 블로커 1 (`changeQualityCd` vs PLAN1 §도메인 룰) 해소 ✓ (wtp-domain-expert 단정).
- **권고 0건** — 본 사이클의 핵심 변경 (RawData.java + RawDataTest.java) 에 신규 권고 사항 없음.
- **참고 3건** — `@PostLoad` import 스타일 / `db/partitioning-and-retention.md §1` 룰-코드 미세 정합 / REVIEW1 권고 3 이월. 모두 차단 사유 아님.

본 사이클의 핵심 목적 (PLAN1 §도메인 룰 ↔ `RawData` 코드 정합 정렬) 달성. INSERT-only 4 컬럼 immutable 검증이 단위 테스트 5 케이스로 고정되었고, `:common:test`·`clean build` 모두 BUILD SUCCESSFUL — 회귀 없음.

`status: approved` 전환. 다음 단계는 `/dev:commit 마스터도메인설계` (사용자 명시 호출 대기 — 자동 실행 금지).

### REVIEW1 → REVIEW2 종합 흐름

| 항목 | REVIEW1 (사이클 1) | REVIEW2 (사이클 2) |
|------|------------------|------------------|
| 블로커 1 (`changeQualityCd` vs PLAN1 §도메인 룰) | 발견 | **해소 ✓** |
| 권고 1 (CORS) | 미해소 | PLAN2 §제외 사항 — 별도 chore 사이클 |
| 권고 2 (SCADA_TIMEOUT) | 재검토 시 해소 확인 | 본 사이클 영향 없음 |
| 권고 3 (§2.5 면책 인용 주석) | 미해소 | 사이클 2 에서도 이월 (참고 3) |
| 참고 1 (`RawDataId` 접근 수준) | 미해소 | 본 사이클 영향 없음 |
| 참고 2 (`PumpControlIntegrationTest` SKIP) | 미해소 | 본 사이클 영향 없음 |

REVIEW1 의 status 갱신: 본 사이클 2 에서 블로커 해소 확인되었으므로 별도 사이클 (사이클 3) 없이 사이클 2 종결 가능. REVIEW1 의 `status: draft` 는 사이클 1 의 트레이서빌리티를 위해 그대로 유지하되, "사이클 2 에서 블로커 1 해소 — REVIEW2 참조" 한 줄 코멘트 보강은 별도 doc-harness 정렬 작업으로 검토.

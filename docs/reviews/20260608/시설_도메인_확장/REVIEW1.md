---
status: approved
created: 2026-06-08
updated: 2026-06-08
---
# 시설 도메인 확장 — 리뷰

## 관련 결과
- [결과](../../../results/20260608/시설_도메인_확장/RESULT1.md)

## 리뷰 범위

시설 유형 7종(운영시설 WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR) 추가 + FacilityGroup 파생 분류 도입. enum 2종·자식 엔티티 7종·요청/응답 DTO 각 7종·Service switch·Controller 다형성·DDL patch·테스트 3종. wtp-* 4개 에이전트 직접 호출 (feature-dev 미사용).

| 리뷰어 | 블로커 | 권고/참고 |
|--------|-------|----------|
| wtp-backend-engineer | 0 | 3 (낮음) |
| wtp-dba-reviewer | 0 | 1 권고(중간) + 1 참고(낮음) |
| wtp-domain-expert | 0 | 1 권고(중간) + 1 참고(낮음) |
| wtp-glossary-manager | 0 | 1 권고(중간) + 1 참고(낮음) |

## 발견 사항

### 복잡도 과잉 (Overengineering)
| 심각도 | 항목 | 내용 | 처리 |
|--------|------|------|------|
| 낮음 | FacilityService switch 11분기 + private save/update 7종 반복 | JPA JOINED 다형성상 자식별 정적 팩토리 개별 호출 불가피 (DWT 외 공통 필드만 보유, 추상화 이득 없음). §2.5 면책 외이나 도메인 안전 미직결 → 블로커 미격상 | 권고 유지 — 12종+ 확장 시 `FacilityFactory` 분리를 차기 `/dev:analyze` 검토 |

### 도메인 룰 위반
| 심각도 | 항목 | 내용 | 처리 |
|--------|------|------|------|
| — | 4영역(알람·인터록·운전모드·이력) 비해당 판정 타당 | skeleton 모델 + 등록 API 한정, PLC 제어/AI 모드 행 미생성. `facility_type_cd` 필터 강제 룰과 그룹 조회 경계 가정 정합 | 통과 |
| 낮음 | SOLAR 향후 전력 수집 경로 | SOLAR 는 PWI/PWQ 태그 부착 가능성이 타 6종보다 높음. PWQ 결측(HLV 미적용, ot-integration.md §3) 직결 — 계측기 부착 사이클 시 4영역 재분류 인지 필요 | 개선 제안 (차기 계측기 사이클 가정 분리) |

### 성능 / DB
| 심각도 | 항목 | 내용 | 처리 |
|--------|------|------|------|
| 낮음(해소) | V2_1 patch TABLE COMMENT 미갱신 | docs/ddl 사본은 "자식 12종" 갱신했으나 운영본 V2_1 이 `COMMENT ON TABLE facility_m` 누락 → 운영본(V2 동결 5종)↔사본 불일치 (§5.3) | **본 리뷰에서 즉시 해소** — V2_1 patch 에 `COMMENT ON TABLE facility_m IS '...12종...'` 추가 |
| 중간 | 저장시설 3종(PWTF·DWT·RSV) FK ON DELETE RESTRICT 미명시 | 신규 7종·기존 point_m/prsf_m 은 RESTRICT 명시, 저장 3종만 미명시 (PostgreSQL 기본 NO ACTION ≈ RESTRICT 기능 동일하나 DDL 가독성 비일관). V2 동결 파일이라 직접 수정 불가 | 개선 제안 — 별도 `V2_2__facility_patch.sql` 정렬 사이클 (본 사이클 외, 기존 자산) |

### 테스트 누락
| 심각도 | 항목 | 내용 | 처리 |
|--------|------|------|------|
| — | 그룹 매핑·등록·직렬화·switch default 검증 | FacilityTypeTest(6)·FacilityServiceTest(+2)·FacilityDtoSerializationTest(+5) 신규/보강, 전 GREEN | 통과 |

### 기타 (용어·예외·데드코드)
| 심각도 | 항목 | 내용 | 처리 |
|--------|------|------|------|
| — | 표준 사전 정합 | `group` 단어 등록·7종 enum 전용(약어 미등재 선례 일관)·facilityGroupCd standard-terms 미등재(DB 컬럼 아님) 모두 적정 | 통과 |
| 낮음 | switch default 예외 유형 혼용 | FacilityDto.from()=IllegalStateException(프로그래밍 오류 fail-fast) vs FacilityService=RestApiException(FACILITY_TYPE_MISMATCH). from() 은 DB 로드 영속 엔티티 전용이라 실경로 노출 낮음, Javadoc 에 의도 명시됨 | 현 설계 유지 (의도 = 미지원 자식 = 프로그래밍 오류). 개선 제안 보류 |
| 낮음 | FacilityGroup.description·FacilityType.description 현 caller 없음 | TagMeasurementType.description 선례 동일 — 한글 논리명 메타 (섹션 API 카드 표출 시 소비 예정). 데드코드 경계이나 enum 컨벤션 정합 | 유지 (§3.1 기존 패턴 정합, 보고만) |
| 낮음 | standard-terms.md `facility_id` 행 "자식 9종" 구 시점 값 | 마스터도메인설계 당시 값, 현 12종. 기존 stale (본 사이클 미접촉 행) | 개선 제안 — 차기 용어 갱신 시 정정 |
| 낮음 | domain-abbreviations.md facility 비고 신규 7종 미언급 | 정보성, 사전 정합 위반 아님 | 개선 제안 |
| 낮음 | Facility.java facilityType 필드/클래스 Javadoc "PWTF·DWT·RSV" stale | 기존부터 POINT/PRSF 누락, 본 사이클로 더 부정확. §3 정밀한 수정상 TASK 외 직접 미변경 | 개선 제안 — docs 정합 또는 차기 facility 작업 시 정정 |

## 개선 제안

1. **(중간) 저장시설 3종 FK ON DELETE RESTRICT 정렬** — `V2_2__facility_patch.sql` + docs/ddl 동시 갱신 별도 사이클. 기능 영향 없음(PostgreSQL 기본 = RESTRICT 동치), DDL 가독성 일관성 목적.
2. **(중간) SOLAR 전력 수집 가정 분리** — 차기 계측기 부착 사이클의 ANALYZE 가정 섹션에 SOLAR PWQ 수집 → 알람 4영역 재분류 트리거 명시.
3. **(낮음) standard-terms.md `facility_id` "자식 9종"→정정 + domain-abbreviations.md facility 비고 7종 보강** — 차기 용어 관리 사이클.
4. **(낮음) Facility.java Javadoc 12종 정합** — docs 정합 사이클.

> 위 개선 제안은 모두 도메인 안전·기능에 영향 없는 후속 정합 항목이며 본 사이클 블로커 아님. 사용자 메모리 "사이클 간 자산 자동 원용 금지"·§3 정밀한 수정 원칙에 따라 본 사이클 범위(신규 7종 + 그룹) 외 기존 자산은 직접 수정하지 않고 별도 사이클로 분리한다.

## 결론

**블로커 0건 — REVIEW approved.** 4개 리뷰어 모두 통과. 다형성 9곳 동기화(응답 12·요청 11)·facilityGroupCd 파생·DDL 양쪽 동기화(V2_1 TABLE COMMENT 해소 포함)·테스트 전 GREEN·`./gradlew.bat clean build BUILD SUCCESSFUL` 확인. 본 사이클에서 즉시 해소한 항목 1건(V2_1 TABLE COMMENT), 후속 분리 개선 제안 4건. `/dev:commit` 진행 가능 (사용자 명시 승인 필요).

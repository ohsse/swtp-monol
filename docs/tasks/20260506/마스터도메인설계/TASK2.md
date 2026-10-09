---
status: completed
created: 2026-05-06
updated: 2026-05-06
---
# 마스터도메인설계 — RawData immutable 정합성 정렬 (사이클 2 TASK)

## 관련 계획
- [계획안](../../../plan/20260506/마스터도메인설계/PLAN2.md)

## Phase

> ROOT [`coding-discipline.md §4.1`](../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: `- [ ] {파일경로 백틱} 작업 → 검증: {확인 명령 / 테스트 / 조회}`. 검증 영역에 백틱 사용 금지 (`check-task-unstage.sh` 훅 파싱 충돌 방지).

### Phase 1: RawData 코드 정렬 (단일 파일)

- [x] `common/src/main/java/com/mo/swtp/raw/domain/RawData.java` changeQualityCd 메서드 (147-149줄) 및 그 Javadoc 제거 → 검증: grep changeQualityCd common/src/main/java/com/mo/swtp/raw/domain/RawData.java 매칭 0건
- [x] `common/src/main/java/com/mo/swtp/raw/domain/RawData.java` qualityCdSnapshot @Transient 필드 추가 + captureImmutableSnapshot 본문에 qualityCdSnapshot = qualityCd 한 줄 추가 → 검증: grep qualityCdSnapshot common/src/main/java/com/mo/swtp/raw/domain/RawData.java 매칭 3건 이상 (필드 선언 + 캡처 + 비교 분기)
- [x] `common/src/main/java/com/mo/swtp/raw/domain/RawData.java` onPreUpdateValidateImmutable 본문에 quality_cd 스냅샷 비교 분기 추가 (기존 3개 분기와 동일 패턴 IllegalStateException) → 검증: grep "quality_cd 는 INSERT-only" common/src/main/java/com/mo/swtp/raw/domain/RawData.java 매칭 1건
- [x] `common/src/main/java/com/mo/swtp/raw/domain/RawData.java` 클래스 Javadoc INSERT-only 컬럼 목록 4개 (tag_srl_no·acq_dtm·raw_val·quality_cd) 갱신 + @PreUpdate Javadoc "corr_val 만 갱신 허용" 정렬 → 검증: grep "corr_val·quality_cd 만 갱신 허용" common/src/main/java/com/mo/swtp/raw/domain/RawData.java 매칭 0건 + grep "corr_val 만 갱신 허용" common/src/main/java/com/mo/swtp/raw/domain/RawData.java 매칭 1건 이상

### Phase 2: 단위 테스트 신규 작성

- [x] `common/src/test/java/com/mo/swtp/raw/domain/RawDataTest.java` 신규 작성 + qualityCd_변경은_immutable_검증으로_차단된다 케이스 추가 (RawData.create → captureImmutableSnapshot → quality_cd 만 변경 → onPreUpdateValidateImmutable 호출 시 IllegalStateException 발생, 메시지 "quality_cd 는 INSERT-only" 포함) → 검증: 파일 존재 + 메서드 컴파일 성공
- [x] `common/src/test/java/com/mo/swtp/raw/domain/RawDataTest.java` corrVal_갱신은_허용된다 positive 케이스 추가 (RawData.create → captureImmutableSnapshot → changeCorrVal → onPreUpdateValidateImmutable 호출 시 예외 없음) → 검증: 메서드 컴파일 성공
- [x] `common/src/test/java/com/mo/swtp/raw/domain/RawDataTest.java` 회귀 케이스 3건 추가 (tagSrlNo·acqDtm·rawVal 각 변경 시 IllegalStateException 발생) → 검증: 메서드 컴파일 성공 + 총 5 케이스 작성 완료

### Phase 3: 빌드 검증

- [x] `./gradlew.bat :common:compileJava` 실행 → 검증: BUILD SUCCESSFUL 출력 확인
- [x] `./gradlew.bat :common:test` 실행 → 검증: BUILD SUCCESSFUL 출력 확인 + RawDataTest 5 케이스 PASS + 기존 테스트 회귀 없음
- [x] `./gradlew.bat clean build` 실행 → 검증: BUILD SUCCESSFUL 출력 확인 (common · api · scheduler 전체 회귀)

## 산출물
- [결과](../../../results/20260506/마스터도메인설계/RESULT2.md)

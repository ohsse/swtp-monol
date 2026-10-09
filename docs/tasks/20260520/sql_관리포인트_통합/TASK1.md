---
status: completed
created: 2026-05-20
updated: 2026-05-20
---
# SQL 관리포인트 통합 — 태스크

## 관련 계획

- [계획안](../../../plan/20260520/sql_관리포인트_통합/PLAN1.md)

## Phase

> ROOT [`coding-discipline.md §4.1`](../../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: `- [x] {파일경로 백틱} 작업 → 검증: {확인 명령 / 테스트 / 조회}`. 검증 영역에 백틱 사용 금지 (`check-task-unstage.sh` 훅 파싱 호환).

### Phase 1: docs/ddl 디렉토리 신설 + 9개 도메인 SSOT 사본 작성

- [x] `backend/docs/ddl/auth.sql` 작성 (api/db/migration/refresh_token_p.sql 흡수 + COMMENT ON COLUMN 의무) → 검증: test -f backend/docs/ddl/auth.sql && grep -c "COMMENT ON COLUMN" backend/docs/ddl/auth.sql 결과 1 이상
- [x] `backend/docs/ddl/facility.sql` 작성 (V6_1 facility_m + V7_1 자식 5종 + V8_1 sensor_point_m + V9_2 facility 인덱스 흡수, BaseEntity 4 + 모든 컬럼 COMMENT) → 검증: test -f backend/docs/ddl/facility.sql && grep "CREATE TABLE facility_m" backend/docs/ddl/facility.sql 매칭
- [x] `backend/docs/ddl/instrument.sql` 작성 (V6_2 instrument_m + V8_2 추가 + V8_3 dwt_m 컬럼 + V8_5 V8_6 drop 반영 + V8_7 + V9_3 pump_m drive_type_cd + V9_2 instrument 인덱스 최종 흡수) → 검증: test -f backend/docs/ddl/instrument.sql && grep "drive_type_cd" backend/docs/ddl/instrument.sql 매칭
- [x] `backend/docs/ddl/menu.sql` 작성 (V6_6 menu_m + menu_role_r 흡수) → 검증: test -f backend/docs/ddl/menu.sql && grep "menu_role_r" backend/docs/ddl/menu.sql 매칭
- [x] `backend/docs/ddl/opt.sql` 작성 (V9_3 predc_1m_h 파티션 테이블 + 월별 파티션 흡수) → 검증: test -f backend/docs/ddl/opt.sql && grep "predc_1m_h" backend/docs/ddl/opt.sql 매칭
- [x] `backend/docs/ddl/proc.sql` 작성 (V9_4 proc_m + ai_drvn_mod_p + ai_drvn_mod_h 흡수) → 검증: test -f backend/docs/ddl/proc.sql && grep -c "CREATE TABLE" backend/docs/ddl/proc.sql 결과 3
- [x] `backend/docs/ddl/raw.sql` 작성 (V6_5 rawdata_1m_h 파티션 테이블 + 월별 파티션 흡수) → 검증: test -f backend/docs/ddl/raw.sql && grep "rawdata_1m_h" backend/docs/ddl/raw.sql 매칭
- [x] `backend/docs/ddl/tag.sql` 작성 (V6_4 tag_m + V9_1 drop unit_cd + use_yn 추가 최종 반영) → 검증: test -f backend/docs/ddl/tag.sql && grep "use_yn" backend/docs/ddl/tag.sql 매칭 + grep -v "unit_cd" 통과
- [x] `backend/docs/ddl/user.sql` 작성 (user_m.sql + use_yn_alignment 정렬 결과 — VARCHAR(1) NOT NULL DEFAULT 미설정 + 단독 인덱스 미포함 + CHECK 보존) → 검증: test -f backend/docs/ddl/user.sql && grep "DEFAULT 'Y'" backend/docs/ddl/user.sql 매칭 0건

### Phase 2: resources/db/migration V1~V9 운영본 작성 (docs/ddl 와 완전 동일)

- [x] `common/src/main/resources/db/migration/V1__auth.sql` 작성 → 검증: diff common/src/main/resources/db/migration/V1__auth.sql backend/docs/ddl/auth.sql 결과 0 라인 차이
- [x] `common/src/main/resources/db/migration/V2__facility.sql` 작성 → 검증: diff common/src/main/resources/db/migration/V2__facility.sql backend/docs/ddl/facility.sql 결과 0 라인
- [x] `common/src/main/resources/db/migration/V3__instrument.sql` 작성 → 검증: diff common/src/main/resources/db/migration/V3__instrument.sql backend/docs/ddl/instrument.sql 결과 0 라인
- [x] `common/src/main/resources/db/migration/V4__menu.sql` 작성 → 검증: diff common/src/main/resources/db/migration/V4__menu.sql backend/docs/ddl/menu.sql 결과 0 라인
- [x] `common/src/main/resources/db/migration/V5__opt.sql` 작성 → 검증: diff common/src/main/resources/db/migration/V5__opt.sql backend/docs/ddl/opt.sql 결과 0 라인
- [x] `common/src/main/resources/db/migration/V6__proc.sql` 작성 → 검증: diff common/src/main/resources/db/migration/V6__proc.sql backend/docs/ddl/proc.sql 결과 0 라인
- [x] `common/src/main/resources/db/migration/V7__raw.sql` 작성 → 검증: diff common/src/main/resources/db/migration/V7__raw.sql backend/docs/ddl/raw.sql 결과 0 라인
- [x] `common/src/main/resources/db/migration/V8__tag.sql` 작성 → 검증: diff common/src/main/resources/db/migration/V8__tag.sql backend/docs/ddl/tag.sql 결과 0 라인
- [x] `common/src/main/resources/db/migration/V9__user.sql` 작성 → 검증: diff common/src/main/resources/db/migration/V9__user.sql backend/docs/ddl/user.sql 결과 0 라인

### Phase 3: README.md 작성 (운영자 안내 + 매핑 표)

- [x] `common/src/main/resources/db/migration/README.md` 신규 작성 (7섹션: 목적 / 기존환경 적용 금지 / 도메인-V 매핑 / 적용 명령 / 동등성 검증 / patch 정책 / docs/ddl 사본 관계) → 검증: test -f common/src/main/resources/db/migration/README.md && grep -c "##" common/src/main/resources/db/migration/README.md 결과 7 이상

### Phase 4: 구 디렉토리·파일 삭제

- [x] `common/src/main/resources/db/init/` 디렉토리 전체 삭제 (15개 SQL + README.md) → 검증: test -d common/src/main/resources/db/init 결과 비-zero (디렉토리 부재)
- [x] `common/src/main/resources/db/migration/V9_1__tag_m_use_yn_and_drop_unit_cd.sql` 삭제 → 검증: test -f common/src/main/resources/db/migration/V9_1__tag_m_use_yn_and_drop_unit_cd.sql 결과 비-zero
- [x] `common/src/main/resources/db/migration/V9_2__facility_instrument_lookup_indexes.sql` 삭제 → 검증: test -f 결과 비-zero
- [x] `common/src/main/resources/db/migration/V9_3__predc_1m_h.sql` 삭제 → 검증: test -f 결과 비-zero
- [x] `api/src/main/resources/db/migration/` 디렉토리 전체 삭제 (3개 SQL — user_m.sql · refresh_token_p.sql · user_m_use_yn_alignment.sql) → 검증: test -d api/src/main/resources/db/migration 결과 비-zero

### Phase 5: 빌드 검증 + 룰 정합성 재확인

- [x] ./gradlew.bat clean build 실행 → 검증: BUILD SUCCESSFUL 출력 확인
- [x] db/init 룰 영역 잔존 없음 검증 → 검증: grep -rn "db/init" .claude/rules/ .claude/hooks/ .claude/agents/ .claude/commands/ 결과 갱신 이력·폐기·2026-05-20·이동 컨텍스트 외 잔존 0건
- [x] check-ddl-column-comment.sh 매칭 글롭 단일화 검증 → 검증: grep "db/migration" .claude/hooks/check-ddl-column-comment.sh 매칭 + grep "db/init" .claude/hooks/check-ddl-column-comment.sh 결과 0건

## 산출물

- [결과](../../../results/20260520/sql_관리포인트_통합/RESULT1.md)

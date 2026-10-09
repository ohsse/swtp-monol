---
status: completed
created: 2026-06-04
updated: 2026-06-04
---
# 송수펌프 제어이력 2·3번 섹션 API — 작업 분해

## 관련 계획
- [계획안](../../../plan/20260604/송수펌프제어이력_2_3번섹션/PLAN1.md)

## Phase

> 체크박스 형식: `- [ ] {파일경로 백틱} 작업 → 검증: {확인 명령 / 테스트 / 조회}`. 검증 영역 백틱 미사용(훅 파싱 호환).

### Phase 1: 엔티티·enum·CMD 코드값·DDL (common + migration)

- [x] dev DB 기존 인덱스·파티션 실명 사전 조사 (V3_1 IF NOT EXISTS 정합 — 중복 생성 방지) → 검증: mcp swtp-postgres-dev 로 pg_indexes WHERE tablename='pump_ctrl_h' + pg_inherits 파티션 자식 테이블명 조회 결과를 V3_1 인덱스·파티션명과 대조 일치
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/enumtype/ControlCommand.java` 신규 — START("가동")/STOP("중지"), description getter (ctrl_div 매핑) → 검증: ./gradlew.bat :common:compileJava 성공
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/enumtype/ControlResult.java` 신규 — COMPLETED("제어완료")/CANCELLED("제어취소"), description getter (ctrl_rslt 매핑) → 검증: ./gradlew.bat :common:compileJava 성공
- [x] `common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java` 수정 — CMD("운전제어","") 10번째 코드값 추가 + enum javadoc "측정유형→신호유형(Signal Type)" 재해석 문구 갱신 → 검증: grep CMD 매칭 + ./gradlew.bat :common:compileJava 성공
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/PumpCtrlHistoryId.java` 신규 — @IdClass 복합 PK (pumpCtrlId:Long, ctrlDtm:LocalDateTime), Serializable, equals/hashCode → 검증: ./gradlew.bat :common:compileJava 성공
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/PumpCtrlHistory.java` 신규 — @Table(pump_ctrl_h) @IdClass + @SequenceGenerator(seq_pump_ctrl_id, allocationSize=100), BaseEntity 4 상속, instrumentId 논리참조(@Column, @ManyToOne 금지), ctrlDiv(ControlCommand)·ctrlRslt(ControlResult)·aiDrvnMod(AiDrvnModeCode nullable), 영속 메서드 미정의 → 검증: ./gradlew.bat :common:compileJava 성공 + grep "@ManyToOne" 미매칭(FK 금지)
- [x] `common/src/main/resources/db/migration/V3_1__instrument_patch.sql` 신규 — CREATE TABLE IF NOT EXISTS pump_ctrl_h(10컬럼, PK(pump_ctrl_id,ctrl_dtm), PARTITION BY RANGE(ctrl_dtm)) + CREATE SEQUENCE IF NOT EXISTS seq_pump_ctrl_id + BRIN(ctrl_dtm)·B-tree(instrument_id,ctrl_dtm DESC) IF NOT EXISTS + 파티션 202604~202703 IF NOT EXISTS + 전 컬럼 COMMENT ON COLUMN(ai_drvn_mod NULL=수동 명기) → 검증: check-ddl-column-comment.sh 훅 통과(저장 성공) + 인덱스·파티션명이 Phase 1.1 실명과 동일
- [x] `docs/ddl/instrument.sql` 수정 — 말미에 pump_ctrl_h CREATE+COMMENT+SEQUENCE+INDEX+파티션 누적(V3_1 동일 내용 동시 갱신, §5.3) → 검증: V3_1 patch 와 동일 커밋 포함 + diff 로 양쪽 pump_ctrl_h 정의 일치 확인

### Phase 2: Repository (api, Querydsl)

- [x] `api/src/main/java/com/mo/swtp/instrument/repository/PumpCtrlHistoryRepository.java` 신규 — JpaRepository<PumpCtrlHistory, PumpCtrlHistoryId> + PumpCtrlHistoryCustomRepository 상속 → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/instrument/repository/PumpCtrlHistoryCustomRepository.java` 신규 — countByAiDrvnMode(start,end):Map<AiDrvnModeCode,Long> + findCtrlHistoryList(start,end):List<PumpCtrlHistoryDto> 인터페이스 → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/instrument/repository/PumpCtrlHistoryCustomRepositoryImpl.java` 신규 — JPAQueryFactory 주입, 섹션2 groupBy(aiDrvnMod) count where ctrlDtm 범위 AND aiDrvnMod isNotNull, 섹션3 pch leftJoin instrument + 제어태그 상관 서브쿼리 MIN(CMD+OUTPUT+Y) orderBy ctrlDtm desc Projections.constructor, §2.5 면책 주석(query-tuning.md §2 + 파티션 프루닝 의도) → 검증: ./gradlew.bat :api:compileJava 성공 + grep "query-tuning.md" 주석 매칭

### Phase 3: 응답 DTO (api)

- [x] `api/src/main/java/com/mo/swtp/instrument/dto/PumpCtrlStatDto.java` 신규 — totalCount(long) + List<ModeStat>(중첩 정적 클래스: aiDrvnMod(AiDrvnModeCode @Schema implementation), count(long), rate(BigDecimal scale 1)) → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/instrument/dto/PumpCtrlHistoryDto.java` 신규 — ctrlDtm·pumpId·pumpNm·ctrlTagSrlNo(nullable)·ctrlDiv(ControlCommand)·ctrlRslt(ControlResult)·updtDtm·aiDrvnMod(nullable), LocalDateTime @JsonFormat(yyyy-MM-dd HH:mm:ss), enum @Schema(implementation), Querydsl Projections.constructor 매핑 생성자 → 검증: ./gradlew.bat :api:compileJava 성공

### Phase 4: Service + Controller (api)

- [x] `api/src/main/java/com/mo/swtp/instrument/service/PumpCtrlHistoryService.java` 신규 — @Transactional(readOnly=true), findCtrlStat(PumpPeriodSearchDto): 기간검증(isValid false→INVALID_INQ_PERIOD)→집계→3종(AI/AI_RECOMD/AI_ANLS) 0건 포함 ModeStat, total=Σcount, rate=count/total*100 HALF_UP scale 1, total=0→rate=0. findCtrlHistory(PumpPeriodSearchDto): 기간검증→Repository 위임 → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/instrument/web/PumpCtrlHistoryController.java` 신규 — extends CommonController, @Tag("12. 송수펌프 제어이력") @RequestMapping("/api/instrument"), GET /pump-ctrl-stat→CommonResponseDto<PumpCtrlStatDto>, GET /pump-ctrl-history(@ModelAttribute PumpPeriodSearchDto)→CommonResponseDto<List<PumpCtrlHistoryDto>>, getResponseEntity 래핑, @Operation·@ApiResponses 전건 → 검증: ./gradlew.bat :api:compileJava 성공 + Swagger 노출 2건

### Phase 5: 시드 데이터 + 단위 테스트

- [x] `docs/seed/pump_ctrl_h_seed_20260604.sql` 신규 — 제어 태그(CMD/OUTPUT) 4건 + pump_ctrl_h 더미 110건(AI 68·AI_RECOMD 34·AI_ANLS 0·수동 NULL 8, ctrl_div/ctrl_rslt 다양, 2026-06 범위). 사용자 승인(대상 dev DB)에 따라 henkey pg_execute_sql + dev connectionString 으로 적재 완료 → 검증: dev 조회 — CMD/OUTPUT 태그 4건 + pump_ctrl_h 110행 확인
- [x] `api/src/test/java/com/mo/swtp/instrument/service/PumpCtrlHistoryServiceTest.java` 신규 — Mockito CustomRepository mock. 섹션2: 전체=모드합 / 비율합 100±0.1 / total=0 rate=0 / NULL 제외 / AI_ANLS 0건 포함 3종. 섹션3: fromDt·toDt 결측·역전 INVALID_INQ_PERIOD / 정상 Repository 결과 통과 → 검증: ./gradlew.bat :api:test --tests *PumpCtrlHistoryServiceTest GREEN (6건 PASS)
- [x] `common/src/test/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementTypeTest.java` 수정 — CMD 추가에 따른 필수 부수 변경 (enum 9→10종 + CMD 단위·설명·전용 테스트) → 검증: ./gradlew.bat :common:test PASS

### Phase 6: 빌드·실조회 검증

- [x] 전체 빌드·QClass 재생성·DDL 훅 통과 → 검증: ./gradlew.bat clean build BUILD SUCCESSFUL
- [x] 시드 후 실조회 정합 (섹션2 모드 카운트 합=전체·비율 합 100% / 섹션3 ctrl_dtm DESC + 펌프명·제어태그 매핑 미존재 시 null) → 검증: dev 조회 — AI 68/AI_RECOMD 34/AI_ANLS 0 = 전체 102(이미지 일치), 섹션3 DESC 정렬·펌프명·CMD 태그 매핑·수동 NULL 노출 확인
- [x] 파티션 프루닝 동작 → 검증: EXPLAIN 결과 Seq Scan on pump_ctrl_h_202606 단일 파티션만 스캔(12개 중 1개 프루닝, db/query-tuning.md §2)

## 산출물
- [결과](../../../results/20260604/송수펌프제어이력_2_3번섹션/RESULT1.md)

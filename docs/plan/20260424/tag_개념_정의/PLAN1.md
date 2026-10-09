---
status: approved
created: 2026-04-24
updated: 2026-04-24
---
# TAG 도메인 개념 정립 — 표준 사전·룰 정합 적용 계획

## 목적

ANALYZE1 의 결정 사항을 바탕으로 **TAG 비즈니스 도메인 정립** 에 필요한 표준 사전 3층(비즈니스 도메인 약어 / 표준 단어 / 표준 데이터 도메인 / 표준 용어) 등록과 관련 룰(`ot-integration.md` SSOT 분리, `db-partitioning-and-retention.md` 시계열 FK 금지) 갱신이 코드/빌드에 부작용 없이 일관 적용되었음을 검증하고, 후속 작업으로 이관할 항목을 명시한다.

## 배경

- [ANALYZE1](../../../analyze/20260424/tag_개념_정의/ANALYZE1.md) (`status: approved`) 에서 6개 안건 결론 도출 완료. 룰 갱신 지시서 10건 모두 `- [x]` 완료
- ANALYZE 결정 후 본 PLAN 작성 시점에 룰 파일 6개 (`domain-abbreviations.md`, `dict/standard-words.md`, `dict/standard-data-domains.md`, `dict/standard-terms.md`, `ot-integration.md`, `db-partitioning-and-retention.md`) 가 이미 갱신됨
- 본 작업은 **사전 등록 + 룰 정합** 만 다루며, 신규 엔티티·API·DB 스키마 변경은 본 작업 범위 외 (사용자 §질문2 답변 확정)
- 따라서 PLAN/TASK/IMPL 단계의 본체는 (a) 갱신된 룰 파일 시각·정합 검토, (b) 빌드 영향 없음 확인, (c) 후속 작업 명시
- DBA 권고 1(시계열 파티션 FK 금지) 은 본 작업 포함됨 (사용자 승인). 권고 2(`tag_nm` 복합 인덱스 가이드) 는 본 작업 외 — `tag_m` 마스터 도입 작업으로 이관

## 범위

### 본 작업 포함 (확정)
1. 갱신된 6개 룰 파일의 시각·정합 검토
   - `domain-abbreviations.md` 도입 예정 표에 `tag` 1행 추가
   - `dict/standard-words.md` 표에 `se`/`desc` 2행 추가
   - `dict/standard-data-domains.md` 표에 `DOM_TAG_NM_50` 1행 추가
   - `dict/standard-terms.md` 표에 `tag_nm`/`tag_se_cd`/`tag_desc` 3행 추가 + 동의어·금지 패턴 표에 3행 추가
   - `ot-integration.md` §3 헤더 직후에 측정 유형 코드 SSOT 안내 인용 블록 추가
   - `db-partitioning-and-retention.md` §1 운영 원칙 절에 시계열 FK 금지 글머리 추가
2. 빌드 영향 없음 검증 — `./gradlew.bat build`
3. ANALYZE1 의 본 작업 외 항목을 RESULT/REVIEW 단계로 이관할 형식으로 정리

### 본 작업 외 (별도 작업으로 이관)
- `tag_alias` 용어 + `alias` 단어 등록 (`alias` 단어가 `nm` 과 유사 충돌인지 별도 ANALYZE 필요)
- 태그명 명명 규칙(`706-FRI-xxx-xxx`) 패턴 룰화 — `706` 의미가 사용자 미확정. 사용자 정보 확보 후 별도 ANALYZE
- `tag_m` 마스터 엔티티 + `TagMeasurementType` Java enum 구현 (Medium 또는 Large)
- `tag` 도메인 모듈 위치 (`api` 단독 vs `common` 이관) 결정 — `tag_m` 도입 ANALYZE 시
- DBA 권고 2 — `rawdata_m` 의 `(tag_nm, acq_dtm DESC)` 복합 인덱스 순서 + `tag_se_cd` 카디널리티 가이드 — `tag_m` 마스터 도입 작업의 DB 설계 단계로 이관

## 도메인 모델

본 작업은 **사전·룰 갱신** 만 다루므로 신규 엔티티·DTO·컬럼이 발생하지 않는다. 도메인 모델 변경 없음.

향후 도입 예정 (별도 작업):
- `Tag` 엔티티 (`tag_m` 테이블) — `tag_nm` PK + `tag_se_cd` + `tag_desc`
- `TagMeasurementType` Java enum (`com.mo.swtp.tag` 패키지 또는 `common` 이관)

## DB 설계 변경

본 작업은 **DB 스키마 변경 없음**. `tag_m` 테이블 등 마스터 테이블 신설은 별도 작업으로 이관됨.

단, `db-partitioning-and-retention.md §1 운영 원칙` 에 추가된 "시계열 파티션 테이블은 마스터 FK 금지" 원칙은 향후 `tag_m` 마스터 도입 시 `rawdata_m.tag_nm → tag_m.tag_nm` FK 추가를 자동 차단하는 **사전 룰** 역할을 한다. 본 룰 신규 추가는 코드/스키마에 직접 영향 없음.

## 구현 방향

본 작업의 IMPL 단계는 다음으로 한정된다:

1. **갱신된 룰 파일 6개 시각·정합 검토**
   - 각 파일의 추가된 행/절이 ANALYZE1 의 룰 갱신 지시서 텍스트와 1:1 일치하는지 확인
   - 표 정렬·들여쓰기·백틱 인용 등 마크다운 형식 일관성 점검
2. **빌드 영향 없음 확인**
   - `./gradlew.bat build` 실행 → BUILD SUCCESSFUL 확인
   - 룰 파일은 코드 외 산출물이므로 본질적으로 빌드에 영향 없음. 회귀 검증 차원
3. **사전 정합성 cross-link 검증**
   - `dict/standard-terms.md` 의 신규 용어 3건이 `dict/standard-data-domains.md` 의 `DOM_TAG_NM_50` 와 `DOM_CODE_20`/`DOM_TEXT` 재사용 정합한지
   - `ot-integration.md` §3 인용 블록의 `dict/standard-terms.md` 링크가 정상 작동하는지 (상대 경로 검증)
   - `domain-abbreviations.md` 의 `tag` 약어가 `dict/standard-terms.md` 의 `tag_*` 용어 조합 근거로 일관 사용되는지

## 테스트 전략

본 작업은 코드 변경이 없으므로 테스트 코드 추가/수정은 없다.

검증 항목:
- `./gradlew.bat build` (전체 모듈 빌드) — BUILD SUCCESSFUL
- 룰 파일 갱신 후 다른 룰 파일에 깨진 cross-link 가 발생했는지 시각 확인 (Grep 으로 사라진 키워드 추적)

영향받는 모듈 테스트:
- 본 작업은 코드 영향 없음 → 모듈별 테스트 추가 없음
- 단 `./gradlew.bat build` 가 전체 모듈 컴파일·기존 테스트를 실행하므로 회귀 검증 충분

## 제외 사항

- 신규 엔티티/DTO/Repository/Service/Controller 작성 (본 작업 범위 외)
- DDL 마이그레이션 / 데이터 픽스처 (본 작업 범위 외)
- `TagMeasurementType` Java enum 실제 작성 (본 작업 범위 외 — 룰에서만 SSOT 명시)
- `Tag.parse(String tagNm)` 헬퍼 도입 / 태그명 정규식 검증 로직 (안건 6 본 작업 외)

## 예상 산출물

- [태스크](../../../tasks/20260424/tag_개념_정의/TASK1.md)

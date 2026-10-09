package com.mo.swtp.instrument.dto;

import com.mo.swtp.common.enumtype.InqUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 송수펌프 가동이력 3번섹션 — 시계열 차트 조회 검색 DTO.
 *
 * <p>1번섹션 파라미터(조회 단위 셀렉트박스 + from~to 날짜)를 수신한다. 전력량·주파수 두 시계열 차트가
 * 동일 파라미터를 공유하므로 단일 검색 DTO 로 표현한다 (송수펌프가동이력_3번섹션 PLAN1 §구현 방향 4).</p>
 *
 * <p>from~to 기간 필드·변환 메서드는 공통 부모 {@link PumpPeriodSearchDto} 가 SSOT 로 보유하며, 본 DTO 는
 * 3번섹션 전용 {@code inqUnit}(조회 단위) 만 확장한다 (송수펌프가동이력_4번섹션 PLAN1 §구현 방향 1 — 4번섹션과
 * from~to 공유를 위한 상속 정렬). 상속 깊이 2단 ({@code .claude/rules/coding-discipline.md §2.1} 3단 이내).</p>
 *
 * <p>{@code @ModelAttribute} GET 쿼리 바인딩 — {@code @Getter @Setter @NoArgsConstructor}
 * ({@link InstrumentSearchDto} 선례 동형). 기간 검증 실패 시 예외 throw 는 Service 가 수행하며,
 * 본 DTO 는 {@link #isValid()} boolean 판정만 제공한다 (DTO 예외 throw 회피).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "송수펌프 가동이력 3번섹션 시계열 조회 검색 조건")
public class PumpTimeSeriesSearchDto extends PumpPeriodSearchDto {

    @Schema(description = "조회 단위 (시/일/월/년) — 시계열 x축 버킷 granularity 결정", implementation = InqUnit.class)
    private InqUnit inqUnit;

    /**
     * 조회 파라미터 유효성을 판정한다.
     *
     * @return 조회 단위가 non-null 이고 부모의 기간 검증({@link PumpPeriodSearchDto#isValid()}) 도 통과하면 {@code true}
     */
    @Override
    public boolean isValid() {
        return inqUnit != null && super.isValid();
    }
}

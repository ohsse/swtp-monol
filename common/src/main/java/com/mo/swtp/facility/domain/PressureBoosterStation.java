package com.mo.swtp.facility.domain;

import com.mo.swtp.common.enumtype.YnType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가압장 자식 엔티티 — JPA JOINED 자식 ({@code facility_type_cd = 'PRSF'}).
 *
 * <p>자식 PK는 부모 {@link Facility#getFacilityId()} 와 동일하며 JPA JOINED 표준 동작으로 자동 상속된다
 * ({@code @Id} 재선언 금지).</p>
 *
 * <p>송수펌프제어분석 ANALYZE1 (2026-05-08) 도입 — PWTF 와 동일한 정수지 단위 모니터링 대상.
 * AI 운전 모드 평가는 시설 종류별 독립으로 평가된다 (시설 단위 {@code last_rcv_dtm} 기준 강제 전환 +
 * {@code ai_drvn_mod_h} 이력 기록은 시설별로 독립, {@code .claude/rules/ot-integration.md} §5 정합).</p>
 *
 * <p>본 PLAN 단계에서는 자식 전용 컬럼 0건 skeleton 만 정의하며, 자식 전용 컬럼은 추후 요구사항명세서 기반
 * 차기 PLAN 에서 결정한다.</p>
 *
 * <p>PRSF 가압장 펌프 기동 명령 확장 전 반드시 별도 사이클에서 {@code pump_interlock_p} 룰 분리·등록을
 * 선행해야 한다. 현재 InterlockValidator 는 "규칙 미등록 시 통과" pass-through 로직 — PRSF 펌프 룰
 * 미등록 상태에서 기동 명령 경로 활성화 시 인터록 무결속 기동 위험 (PLAN1 §제외 사항 명시 의무).</p>
 */
@Entity
@Table(name = "prsf_m")
@DiscriminatorValue("PRSF")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PressureBoosterStation extends Facility {

    /**
     * 신규 가압장을 생성한다.
     *
     * @param facilityNm        시설명
     * @param parentFacilityId  상위 시설 ID (NULL 허용)
     * @param dispOrd           표시 순서
     * @param mainYn            주요 시설 여부
     * @return 생성된 가압장 ({@code use_yn = Y})
     */
    public static PressureBoosterStation create(
            String facilityNm,
            String parentFacilityId,
            Integer dispOrd,
            YnType mainYn) {
        return new PressureBoosterStation(facilityNm, parentFacilityId, dispOrd, mainYn);
    }

    private PressureBoosterStation(
            String facilityNm,
            String parentFacilityId,
            Integer dispOrd,
            YnType mainYn) {
        super(facilityNm, parentFacilityId, dispOrd, mainYn, YnType.Y);
    }
}

package com.mo.swtp.raw.domain;

import com.mo.swtp.common.domain.BaseEntity;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * SCADA 원시 데이터 1분 시계열 엔티티 — 월 RANGE 파티션 ({@code rawdata_1m_h}).
 *
 * <p>복합 PK {@code (rawdata_id, acq_dtm)} — {@link RawDataId} 식별. {@code acq_dtm} 은 파티션 키 역할도 한다.
 * {@code rawdata_id} 는 {@code seq_rawdata_id} 시퀀스 ({@code allocationSize=100}) 로 자동 할당.</p>
 *
 * <p>마스터도메인설계 ANALYZE1 Round 3 (2026-05-03) 결정으로 종전 단일 {@code tag_val} 을
 * {@code raw_val} (SCADA 원본) + {@code corr_val} (보정/Hold Last Value) 2 컬럼으로 분리.
 * BaseEntity 4 적용 — {@code corr_val} 갱신 시점은 {@code updt_dtm}·{@code updt_id} 자동 갱신.</p>
 *
 * <p>도메인 룰 — INSERT-only 컬럼 immutable 검증 (PLAN1 §도메인 룰):</p>
 * <ul>
 *   <li>{@code tag_srl_no}·{@code acq_dtm}·{@code raw_val}·{@code quality_cd} 는 SCADA 수집 시점 고정값 — 갱신 금지</li>
 *   <li>{@code corr_val} 만 갱신 허용 — 운영자 사후 보정 또는 Hold Last Value 적용 결과</li>
 *   <li>{@link #onPreUpdateValidateImmutable()} 가 {@code @PreUpdate} 시 변경 여부를 검사하고 위반 시 {@link IllegalStateException}</li>
 * </ul>
 *
 * <p>도메인 룰 — {@code corr_val} 갱신과 SCADA_TIMEOUT 분리 (PLAN1 §도메인 룰):
 * {@code corr_val} 갱신은 SCADA 인바운드 수신 경로와 무관 (운영자 사후 보정).
 * Service 구현 시 {@code corr_val} 갱신 경로에서 {@code ai_drvn_mod_p.last_rcv_dtm} 갱신·{@code ai_drvn_mod_h}
 * 행 추가 금지 — {@code ot-integration.md §5} 강제 전환 정책 직결.</p>
 *
 * <p>시계열 → 마스터 FK 금지 ({@code db/partitioning-and-retention.md §1}) — {@code tag_srl_no} 는
 * {@code tag_m.tag_srl_no} 의 논리 참조이며 FK 제약 없음. 참조 무결성은 애플리케이션 레벨에서 검증한다.</p>
 *
 * <p>참조: {@code docs/plan/20260503/마스터도메인설계/PLAN1.md} §도메인 모델, §도메인 룰.</p>
 */
@Entity
@Table(name = "rawdata_1m_h")
@IdClass(RawDataId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RawData extends BaseEntity {

    /** 로우데이터 ID — {@code seq_rawdata_id} 시퀀스 (DOM_SEQ_BIGINT, 복합 PK 1/2). */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqRawdataIdGenerator")
    @SequenceGenerator(
            name = "seqRawdataIdGenerator",
            sequenceName = "seq_rawdata_id",
            allocationSize = 100
    )
    @Column(name = "rawdata_id", nullable = false)
    private Long rawdataId;

    /** SCADA 수집 일시 — 파티션 키 + 복합 PK 2/2 (INSERT-only immutable). */
    @Id
    @Column(name = "acq_dtm", nullable = false)
    private LocalDateTime acqDtm;

    /** 태그 시리얼번호 — {@code tag_m.tag_srl_no} 논리 참조 (시계열 → 마스터 FK 금지, INSERT-only immutable). */
    @Column(name = "tag_srl_no", nullable = false, length = 50)
    private String tagSrlNo;

    /** SCADA 원본 측정값 — INSERT-only immutable, BAD QUALITY 시 NULL. */
    @Column(name = "raw_val", precision = 15, scale = 4)
    private BigDecimal rawVal;

    /** 보정/수정 측정값 — Hold Last Value 적용 결과 또는 운영자 보정, NULL 허용, 갱신 허용. */
    @Column(name = "corr_val", precision = 15, scale = 4)
    private BigDecimal corrVal;

    /** SCADA QUALITY 코드 — GOOD/BAD/UNCERTAIN ({@code ot-integration.md §3} 정합). */
    @Enumerated(EnumType.STRING)
    @Column(name = "quality_cd", nullable = false, length = 20)
    private QualityCode qualityCd;

    /**
     * INSERT-only 컬럼 immutable 검증용 영속 시점 스냅샷.
     * {@code @PostLoad}·{@code @PrePersist} 에서 채워지고 {@code @PreUpdate} 에서 비교한다.
     */
    @Transient
    private String tagSrlNoSnapshot;

    /** acqDtm 영속 시점 스냅샷. */
    @Transient
    private LocalDateTime acqDtmSnapshot;

    /** rawVal 영속 시점 스냅샷. */
    @Transient
    private BigDecimal rawValSnapshot;

    /** qualityCd 영속 시점 스냅샷. */
    @Transient
    private QualityCode qualityCdSnapshot;

    /**
     * 신규 SCADA 원시 데이터를 INSERT 한다.
     * INSERT 시점에는 immutable 검증을 거치지 않으나, 이후 갱신 시 본 메서드로 채워진 값과 비교된다.
     *
     * @param acqDtm     수집 일시
     * @param tagSrlNo   태그 시리얼번호 (논리 참조)
     * @param rawVal     원본 측정값 (BAD QUALITY 시 NULL)
     * @param corrVal    보정 측정값 (NULL 허용)
     * @param qualityCd  QUALITY 코드
     * @return 생성된 RawData ({@code rawdata_id} 는 시퀀스로 자동 할당)
     */
    public static RawData create(
            LocalDateTime acqDtm,
            String tagSrlNo,
            BigDecimal rawVal,
            BigDecimal corrVal,
            QualityCode qualityCd) {
        RawData rawData = new RawData();
        rawData.acqDtm = acqDtm;
        rawData.tagSrlNo = tagSrlNo;
        rawData.rawVal = rawVal;
        rawData.corrVal = corrVal;
        rawData.qualityCd = qualityCd;
        return rawData;
    }

    /**
     * 보정 측정값을 갱신한다 — 유일하게 갱신 허용된 컬럼.
     * {@code raw_val}·{@code tag_srl_no}·{@code acq_dtm} 변경 메서드는 의도적으로 미정의.
     *
     * @param corrVal 보정 측정값 (Hold Last Value 또는 운영자 보정)
     */
    public void changeCorrVal(BigDecimal corrVal) {
        this.corrVal = corrVal;
    }

    /**
     * INSERT-only 컬럼 스냅샷 캡처 — 신규 INSERT 시 호출.
     */
    @PrePersist
    protected void onPrePersistSnapshot() {
        captureImmutableSnapshot();
    }

    /**
     * INSERT-only 컬럼 immutable 검증 — UPDATE 시 변경 시 {@link IllegalStateException}.
     * {@code corr_val} 만 갱신 허용 (PLAN1 §도메인 룰).
     */
    @PreUpdate
    protected void onPreUpdateValidateImmutable() {
        if (!java.util.Objects.equals(tagSrlNoSnapshot, tagSrlNo)) {
            throw new IllegalStateException(
                    "tag_srl_no 는 INSERT-only immutable 컬럼이며 갱신할 수 없습니다 (PLAN1 §도메인 룰)");
        }
        if (!java.util.Objects.equals(acqDtmSnapshot, acqDtm)) {
            throw new IllegalStateException(
                    "acq_dtm 은 INSERT-only immutable 컬럼이며 갱신할 수 없습니다 (PLAN1 §도메인 룰)");
        }
        if (!java.util.Objects.equals(rawValSnapshot, rawVal)) {
            throw new IllegalStateException(
                    "raw_val 은 INSERT-only immutable 컬럼이며 갱신할 수 없습니다 (PLAN1 §도메인 룰)");
        }
        if (!java.util.Objects.equals(qualityCdSnapshot, qualityCd)) {
            throw new IllegalStateException(
                    "quality_cd 는 INSERT-only immutable 컬럼이며 갱신할 수 없습니다 (PLAN1 §도메인 룰)");
        }
    }

    /**
     * DB 로드 후 immutable 스냅샷을 채운다.
     * {@link BaseEntity#onPostLoadMarkLoaded()} 와 별개로 본 엔티티 전용 후처리.
     */
    @jakarta.persistence.PostLoad
    protected void onPostLoadCaptureSnapshot() {
        captureImmutableSnapshot();
    }

    private void captureImmutableSnapshot() {
        this.tagSrlNoSnapshot = this.tagSrlNo;
        this.acqDtmSnapshot = this.acqDtm;
        this.rawValSnapshot = this.rawVal;
        this.qualityCdSnapshot = this.qualityCd;
    }
}

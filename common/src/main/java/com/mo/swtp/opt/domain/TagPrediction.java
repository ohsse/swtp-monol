package com.mo.swtp.opt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * AI 예측 시계열 태그 예측값 엔티티 — 월 RANGE 파티션 ({@code predc_1m_h}).
 *
 * <p>송수펌프제어분석 §7 (시설 예측 데이터 표출) 신규 도메인. 활성 시설의 유량계 FRI/PRI 예측값 +
 * 펌프 OPS 예측 가동상태를 1분 그리드로 저장하는 immutable INSERT-only 이력. 본 사이클은 조회 전용
 * (INSERT 경로 = AI 추론 파이프라인은 사이클 2 결정 대기 — PLAN1 §제외 사항).</p>
 *
 * <p>복합 PK {@code (predc_id, predc_dtm)} — {@link TagPredictionId} 식별. {@code predc_dtm} 은 파티션 키
 * 역할도 한다. {@code predc_id} 는 {@code seq_predc_id} 시퀀스 ({@code allocationSize=100}) 로 자동 할당.</p>
 *
 * <p>immutable 이력 예외 — {@code BaseEntity} 미상속, {@code rgstr_dtm}·{@code rgstr_id} 만 보유,
 * {@code updt_*} 컬럼 미정의 ({@code db/indexing-and-migration.md §4.3} immutable 이력 표준 라벨 정합).
 * INSERT-only 구조이므로 변경 메서드 미정의로 자연스럽게 갱신 차단.</p>
 *
 * <p>시계열 → 마스터 FK 금지 ({@code db/partitioning-and-retention.md §1}) — {@code tag_srl_no} 는
 * {@code tag_m.tag_srl_no} 의 논리 참조이며 FK 제약 없음. 참조 무결성은 애플리케이션 레벨에서 검증한다.</p>
 *
 * <p>참조: {@code docs/plan/20260518/송수펌프제어분석-7번섹션/PLAN1.md} §1·§5.</p>
 */
@Entity
@Table(name = "predc_1m_h")
@IdClass(TagPredictionId.class)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TagPrediction {

    /** 예측 결과 ID — {@code seq_predc_id} 시퀀스 (DOM_SEQ_BIGINT, 복합 PK 1/2). */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqPredcIdGenerator")
    @SequenceGenerator(
            name = "seqPredcIdGenerator",
            sequenceName = "seq_predc_id",
            allocationSize = 100
    )
    @Column(name = "predc_id", nullable = false)
    private Long predcId;

    /** 예측 대상 일시 — 파티션 키 + 복합 PK 2/2 (INSERT-only immutable). */
    @Id
    @Column(name = "predc_dtm", nullable = false)
    private LocalDateTime predcDtm;

    /** 태그 시리얼번호 — {@code tag_m.tag_srl_no} 논리 참조 (시계열 → 마스터 FK 금지, INSERT-only immutable). */
    @Column(name = "tag_srl_no", nullable = false, length = 50)
    private String tagSrlNo;

    /** 예측 측정값 — NULL 허용 (예측 결측 표현). */
    @Column(name = "predc_val", precision = 15, scale = 4)
    private BigDecimal predcVal;

    /** 등록 일시 — immutable 이력 표준 라벨 (AuditingEntityListener 자동 주입). */
    @CreatedDate
    @Column(name = "rgstr_dtm", nullable = false, updatable = false)
    private LocalDateTime rgstrDtm;

    /** 등록자 ID — immutable 이력 표준 라벨 (AuditingEntityListener 자동 주입). */
    @CreatedBy
    @Column(name = "rgstr_id", nullable = false, length = 50, updatable = false)
    private String rgstrId;

    /**
     * 신규 AI 예측값을 INSERT 한다.
     * {@code predc_id} 는 시퀀스로 자동 할당, {@code rgstr_dtm}·{@code rgstr_id} 는 AuditingEntityListener 자동 주입.
     *
     * @param predcDtm  예측 대상 일시
     * @param tagSrlNo  태그 시리얼번호 (논리 참조)
     * @param predcVal  예측 측정값 (NULL 허용 — 결측 표현)
     * @return 생성된 TagPrediction
     */
    public static TagPrediction create(
            LocalDateTime predcDtm,
            String tagSrlNo,
            BigDecimal predcVal) {
        TagPrediction prediction = new TagPrediction();
        prediction.predcDtm = predcDtm;
        prediction.tagSrlNo = tagSrlNo;
        prediction.predcVal = predcVal;
        return prediction;
    }
}

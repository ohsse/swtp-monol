package com.mo.swtp.tag.domain;

import com.mo.swtp.common.domain.BaseEntity;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.tag.domain.enumtype.IoCode;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * 태그 마스터 엔티티 — SCADA 계측값 식별 단위.
 *
 * <p>PK는 외부에서 할당받는 자연키 ({@code tag_srl_no}, 706-FRI-xxx-xxx 형식, DOM_TAG_SRL_NO_50).
 * {@link Persistable} 을 구현하여 JPA {@code save()} 가 신규 INSERT 경로를 따르도록 한다 —
 * {@code getId()} 만 override 하고 {@code isNew()} 는 {@link BaseEntity#isNew()} 위임.</p>
 *
 * <p>마스터도메인설계 ANALYZE1 Round 3 (2026-05-03) 결정으로 종전 {@code tag_id} 별도 식별자를 폐기하고
 * 자연키 PK 로 변경. {@code io_cd} (DOM_CODE_20) + {@link IoCode} enum (INPUT/OUTPUT/BIDIR) 채택 —
 * {@code io_yn} (boolean) 대안 폐기.</p>
 *
 * <p>태그관리 ANALYZE1 안건 1·6 (2026-05-08) 결정으로 본 사이클에서 다음 변경:</p>
 * <ul>
 *   <li>{@code use_yn} 컬럼 추가 ({@link YnType} enum) — 논리 삭제 지원
 *       ({@code rawdata_1m_h.tag_srl_no} 논리 참조 보존, 시계열 → 마스터 FK 금지 정책 정합)</li>
 *   <li>{@code unit_cd} 필드 제거 — {@link TagMeasurementType} enum 의 {@code unit} 필드로 매핑 대체
 *       (측정 유형별 1:1 고정값 정규화)</li>
 *   <li>{@link #deactivate()} 메서드 추가 — 논리 삭제 (use_yn = N)</li>
 *   <li>{@link #changeInfo(TagMeasurementType, String, IoCode)} 시그니처 변경 — {@code instrument} 인자 추가 금지
 *       (안건 4 블로커 — 계측기 교체는 deactivate + create 플로우)</li>
 * </ul>
 *
 * <p>참조: {@code docs/plan/20260508/태그관리/PLAN1.md} §도메인 모델,
 * {@code .claude/rules/dict/standard-terms.md} (tag_srl_no, use_yn, io_cd, tag_se_cd).</p>
 */
@Entity
@Table(name = "tag_m")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag extends BaseEntity implements Persistable<String> {

    /** 태그 시리얼번호 — 자연키 PK (DOM_TAG_SRL_NO_50, 외부 할당). */
    @Id
    @Column(name = "tag_srl_no", nullable = false, length = 50)
    private String tagSrlNo;

    /** 소속 계측기 — 마스터 참조 FK to {@code instrument_m} (NOT NULL). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    /**
     * 태그 측정 유형 코드 — FRI/PRI/LEI/PWI/RMS/OPS/VOI ({@code ot-integration.md §3} 정합).
     * 측정 단위는 본 enum 의 {@code getUnit()} 으로 매핑 (별도 unit_cd 컬럼 폐기).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tag_se_cd", nullable = false, length = 20)
    private TagMeasurementType tagSeCd;

    /** 태그 설명 — 자유형 텍스트 (DOM_TEXT, NULL 허용). */
    @Column(name = "tag_desc", columnDefinition = "TEXT")
    private String tagDesc;

    /** 입출력 구분 코드 — INPUT/OUTPUT/BIDIR ({@link IoCode} enum 매핑). */
    @Enumerated(EnumType.STRING)
    @Column(name = "io_cd", nullable = false, length = 20)
    private IoCode ioCd;

    /**
     * 사용 여부 (DOM_YN) — 논리 삭제 플래그.
     * {@code rawdata_1m_h.tag_srl_no} 논리 참조 보존을 위해 물리 삭제 대신 본 플래그를 N 으로 전환한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "use_yn", nullable = false, length = 1)
    private YnType useYn;

    /**
     * {@inheritDoc} — PK 를 반환한다.
     */
    @Override
    public String getId() {
        return tagSrlNo;
    }

    /**
     * 신규 태그를 생성한다. 초기 {@code useYn} 은 {@link YnType#Y} 로 명시 할당된다 (Java SSOT).
     *
     * @param tagSrlNo    태그 시리얼번호 (외부 할당 자연키)
     * @param instrument  소속 계측기
     * @param tagSeCd     태그 측정 유형 (단위는 enum 매핑)
     * @param tagDesc     태그 설명 (NULL 허용)
     * @param ioCd        입출력 구분 코드
     * @return 생성된 태그
     */
    public static Tag create(
            String tagSrlNo,
            Instrument instrument,
            TagMeasurementType tagSeCd,
            String tagDesc,
            IoCode ioCd) {
        Tag tag = new Tag();
        tag.tagSrlNo = tagSrlNo;
        tag.instrument = instrument;
        tag.tagSeCd = tagSeCd;
        tag.tagDesc = tagDesc;
        tag.ioCd = ioCd;
        tag.useYn = YnType.Y;
        return tag;
    }

    /**
     * 태그 메타정보를 변경한다. null 인자는 기존 값을 유지한다.
     *
     * <p>{@code instrument} 인자는 의도적으로 미포함 — 계측기 교체는
     * {@link #deactivate()} + {@link #create(String, Instrument, TagMeasurementType, String, IoCode)} 플로우로 처리한다
     * (태그관리 ANALYZE1 안건 4 블로커, {@code .claude/rules/ot-integration.md} §5 이력 기록 의무 정합).</p>
     *
     * @param tagSeCd  태그 측정 유형 (단위 매핑은 enum 으로 자동)
     * @param tagDesc  태그 설명
     * @param ioCd     입출력 구분 코드
     */
    public void changeInfo(TagMeasurementType tagSeCd, String tagDesc, IoCode ioCd) {
        if (tagSeCd != null) {
            this.tagSeCd = tagSeCd;
        }
        if (tagDesc != null) {
            this.tagDesc = tagDesc;
        }
        if (ioCd != null) {
            this.ioCd = ioCd;
        }
    }

    /**
     * 태그를 논리 삭제한다 ({@code use_yn = N}).
     *
     * <p>{@code rawdata_1m_h.tag_srl_no} 논리 참조 보존을 위해 물리 삭제 대신 본 메서드를 호출한다.
     * 비활성 태그도 ADMIN 화면에서 조회되며 (메타 검토·재활성화 결정 필요), 알람 평가 등 도메인 룰
     * 평가 경로는 본 사이클 비변경.</p>
     */
    public void deactivate() {
        this.useYn = YnType.N;
    }
}

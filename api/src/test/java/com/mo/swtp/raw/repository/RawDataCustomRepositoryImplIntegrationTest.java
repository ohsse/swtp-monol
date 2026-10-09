package com.mo.swtp.raw.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataFacilitySumDto;
import com.mo.swtp.raw.dto.RawDataLatestDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link RawDataCustomRepositoryImpl} 통합 테스트.
 *
 * <p>송수펌프제어분석-3번섹션 PLAN1 §테스트 전략 §통합 테스트 — PostgreSQL DISTINCT ON + 1시간 윈도우
 * 파티션 프루닝 검증. 로컬 PostgreSQL ({@code application-test.yml} 의 {@code jdbc:postgresql://localhost:5432/smartwtp})
 * + 사전 DDL 실행 (V6_5__rawdata_1m_h.sql 의 2026-05 파티션) 가정.</p>
 *
 * <p>시나리오: 태그 5건 × 측정 5건 (총 25건) INSERT 후 {@code findLatestByTagSrlNos} 호출 →
 * 각 태그마다 가장 최근 행 1개씩 반환 검증 (총 5건). PLAN1 §성공 기준 #2 의 N=100 성능 측정은
 * 운영 환경에서 별도 부하 테스트로 진행.</p>
 *
 * <p>{@code @SpringBootTest(NONE)} + {@code @Transactional} — 각 테스트 종료 후 자동 롤백.</p>
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
@Disabled("PostgreSQL 기동 + DDL 사전 실행 (V6_5__rawdata_1m_h.sql 의 2026-05 파티션) 필요 — "
        + "사용자 로컬 환경에서 @Disabled 제거 후 실행하여 PLAN1 §성공 기준 #1·#2·#3 검증.")
class RawDataCustomRepositoryImplIntegrationTest {

    private static final String TEST_TAG_PREFIX = "TEST-OPS-IT-";

    @Autowired
    private RawDataRepository rawDataRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void 태그별_최신_측정값을_DISTINCT_ON_으로_조회한다() {
        // given: 5 태그 × 5 측정 = 25건 INSERT (acq_dtm 시간차 1분)
        LocalDateTime baseDtm = LocalDateTime.now().minusMinutes(30);
        for (int t = 1; t <= 5; t++) {
            String tagSrlNo = TEST_TAG_PREFIX + t;
            for (int m = 0; m < 5; m++) {
                insertRawData(tagSrlNo, baseDtm.plusMinutes(m), 1.0 + m * 0.1);
            }
        }
        em.flush();

        List<String> tagSrlNos = List.of(
                TEST_TAG_PREFIX + 1, TEST_TAG_PREFIX + 2, TEST_TAG_PREFIX + 3,
                TEST_TAG_PREFIX + 4, TEST_TAG_PREFIX + 5);

        // when
        List<RawDataLatestDto> result = rawDataRepository.findLatestByTagSrlNos(tagSrlNos);

        // then: 태그 5건 → 결과 5건 (각 태그의 m=4 인덱스, 가장 최근 acq_dtm)
        assertThat(result).hasSize(5);
        assertThat(result).extracting(RawDataLatestDto::tagSrlNo)
                .containsExactlyInAnyOrderElementsOf(tagSrlNos);
        // 각 태그의 raw_val 은 m=4 인 1.4 (baseDtm + 4분 의 값)
        assertThat(result).allMatch(r -> r.rawVal().doubleValue() == 1.4);
    }

    @Test
    void 빈_태그_목록은_빈_결과를_반환한다() {
        List<RawDataLatestDto> result = rawDataRepository.findLatestByTagSrlNos(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void 한시간_초과_과거_측정값은_제외된다() {
        // given: 1시간 30분 전 측정값 INSERT
        String tagSrlNo = TEST_TAG_PREFIX + "PAST";
        insertRawData(tagSrlNo, LocalDateTime.now().minusMinutes(90), 99.9);
        em.flush();

        // when
        List<RawDataLatestDto> result = rawDataRepository
                .findLatestByTagSrlNos(List.of(tagSrlNo));

        // then: 1시간 윈도우 제외로 결과 없음
        assertThat(result).isEmpty();
    }

    @Test
    void 시설별_버킷_시설합_PWI_MAX_와_발생시각을_조회한다() {
        // 시설별사용량-2번섹션 — unnest 평행 배열 바인드 + SUM(MAX)≠MAX(SUM) + argmax 검증.
        // F1: T1·T2 두 태그. 10:00 분합=10+20=30, 10:01 분합=40+1=41 → 버킷(시) MAX=41 (MAX-then-SUM 이면 60 — 오답)
        // F2: T3 한 태그. 10:00 분합=100.
        LocalDateTime base = LocalDateTime.of(2026, 5, 15, 10, 0, 0);
        insertRawData("TEST-FU-T1", base, 10.0);
        insertRawData("TEST-FU-T2", base, 20.0);
        insertRawData("TEST-FU-T1", base.plusMinutes(1), 40.0);
        insertRawData("TEST-FU-T2", base.plusMinutes(1), 1.0);
        insertRawData("TEST-FU-T3", base, 100.0);
        em.flush();

        List<String> tags = List.of("TEST-FU-T1", "TEST-FU-T2", "TEST-FU-T3");
        List<String> roots = List.of("F1", "F1", "F2");
        LocalDateTime start = LocalDateTime.of(2026, 5, 15, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 16, 0, 0);

        List<RawDataFacilitySumDto> result = rawDataRepository
                .findFacilityBucketPeakElpwr(tags, roots, start, end, "hour");

        // F1 최대전력 = 41 (분합 SUM 후 버킷 MAX), 발생 버킷 = 10:00 (date_trunc 'hour')
        RawDataFacilitySumDto f1 = pick(result, "F1");
        assertThat(f1.value()).isEqualByComparingTo("41.0");
        assertThat(f1.dtm()).isEqualTo(LocalDateTime.of(2026, 5, 15, 10, 0, 0));
        RawDataFacilitySumDto f2 = pick(result, "F2");
        assertThat(f2.value()).isEqualByComparingTo("100.0");
    }

    @Test
    void 시설별_마지막_분의_시설합_PWI_를_조회한다() {
        // F1: 10:01 이 마지막 분 → 시설합 41. F2: 10:00 시설합 100.
        LocalDateTime base = LocalDateTime.of(2026, 5, 15, 10, 0, 0);
        insertRawData("TEST-FU-T1", base, 10.0);
        insertRawData("TEST-FU-T2", base, 20.0);
        insertRawData("TEST-FU-T1", base.plusMinutes(1), 40.0);
        insertRawData("TEST-FU-T2", base.plusMinutes(1), 1.0);
        insertRawData("TEST-FU-T3", base, 100.0);
        em.flush();

        List<String> tags = List.of("TEST-FU-T1", "TEST-FU-T2", "TEST-FU-T3");
        List<String> roots = List.of("F1", "F1", "F2");
        LocalDateTime start = LocalDateTime.of(2026, 5, 15, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 16, 0, 0);

        List<RawDataFacilitySumDto> result = rawDataRepository
                .findFacilityLatestMinuteSumElpwr(tags, roots, start, end);

        RawDataFacilitySumDto f1 = pick(result, "F1");
        assertThat(f1.value()).isEqualByComparingTo("41.0");
        assertThat(f1.dtm()).isEqualTo(LocalDateTime.of(2026, 5, 15, 10, 1, 0));
        RawDataFacilitySumDto f2 = pick(result, "F2");
        assertThat(f2.value()).isEqualByComparingTo("100.0");
        assertThat(f2.dtm()).isEqualTo(LocalDateTime.of(2026, 5, 15, 10, 0, 0));
    }

    @Test
    void 시설별_집계는_빈_태그_목록에_빈_결과를_반환한다() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 15, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 16, 0, 0);
        assertThat(rawDataRepository.findFacilityLatestMinuteSumElpwr(List.of(), List.of(), start, end))
                .isEmpty();
        assertThat(rawDataRepository.findFacilityBucketPeakElpwr(List.of(), List.of(), start, end, "hour"))
                .isEmpty();
    }

    /** 시설별 집계 결과에서 facilityId 매칭 1건을 추출한다. */
    private RawDataFacilitySumDto pick(List<RawDataFacilitySumDto> result, String facilityId) {
        return result.stream()
                .filter(r -> r.facilityId().equals(facilityId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("facilityId 결과 없음: " + facilityId));
    }

    /**
     * 시계열 native INSERT — BaseEntity AuditingEntityListener 우회 (테스트 픽스처 전용).
     */
    private void insertRawData(String tagSrlNo, LocalDateTime acqDtm, double rawVal) {
        em.createNativeQuery("""
                        INSERT INTO rawdata_1m_h (
                            rawdata_id, acq_dtm, tag_srl_no, raw_val, corr_val, quality_cd,
                            rgstr_dtm, updt_dtm, rgstr_id, updt_id
                        ) VALUES (
                            nextval('seq_rawdata_id'), :acqDtm, :tagSrlNo, :rawVal, NULL, :qualityCd,
                            :nowDtm, :nowDtm, 'test', 'test'
                        )
                        """)
                .setParameter("acqDtm", acqDtm)
                .setParameter("tagSrlNo", tagSrlNo)
                .setParameter("rawVal", rawVal)
                .setParameter("qualityCd", QualityCode.GOOD.name())
                .setParameter("nowDtm", LocalDateTime.now())
                .executeUpdate();
    }
}

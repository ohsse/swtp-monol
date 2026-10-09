package com.mo.swtp.opt.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mo.swtp.opt.dto.TagPredictionMatchDto;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
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
 * {@link TagPredictionCustomRepositoryImpl} 통합 테스트.
 *
 * <p>송수펌프제어분석-7번섹션 PLAN1 §테스트 전략 §통합 테스트 — {@code latest_meas} CTE +
 * {@code CROSS JOIN LATERAL} 근접매칭 검증. 로컬 PostgreSQL ({@code application-test.yml} 의
 * {@code jdbc:postgresql://localhost:5432/smartwtp}) + 사전 DDL 실행 (V6_5 rawdata_1m_h + V9_3 predc_1m_h
 * 의 현재 월 파티션) 가정.</p>
 *
 * <p>시나리오:</p>
 * <ol>
 *   <li>태그별 근접행 1건 반환 — rawdata 최신 acq_dtm + 1시간 에 가장 근접한 predc 행</li>
 *   <li>윈도우 밖 예측행은 미반환 (LATERAL 누락 → 응답 List 미포함)</li>
 *   <li>빈 태그 목록 → 빈 결과</li>
 * </ol>
 *
 * <p>fixture 시각은 {@code LocalDateTime.now()} 기준 — rawdata 의 {@code NOW() - INTERVAL '1 hour'} 1시간
 * 윈도우 정책과 정합 (RawData 통합 테스트 선례 동일).</p>
 *
 * <p>{@code @SpringBootTest(NONE)} + {@code @Transactional} — 각 테스트 종료 후 자동 롤백.</p>
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
@Disabled("PostgreSQL 기동 + DDL 사전 실행 (V6_5 rawdata_1m_h + V9_3 predc_1m_h 의 현재 월 파티션) 필요 — "
        + "사용자 로컬 환경에서 @Disabled 제거 후 실행하여 PLAN1 §성공 기준 #1·#2·#3 검증.")
class TagPredictionCustomRepositoryTest {

    private static final String TEST_TAG_PREFIX = "TEST-PREDC-IT-";
    private static final int WINDOW_MINUTES = 5;

    @Autowired
    private TagPredictionRepository tagPredictionRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void 태그별_근접_예측행_1건씩을_반환한다() {
        // given: 태그 3건 × (rawdata 최신 acq_dtm 1건 + predc 5건 시각차) 픽스처
        LocalDateTime nowBase = LocalDateTime.now().minusMinutes(10);  // 최근 acq_dtm
        for (int t = 1; t <= 3; t++) {
            String tagSrlNo = TEST_TAG_PREFIX + t;
            // rawdata: 최신 acq_dtm 1건만 (DISTINCT ON 의 최신 1건)
            insertRawData(tagSrlNo, nowBase, 1.0);
            // predc: (nowBase + 1h) 주변 5건 시각차 (-4, -2, 0, +2, +4 분) — 0분 행이 정확 일치
            LocalDateTime target = nowBase.plusHours(1);
            insertPrediction(tagSrlNo, target.minusMinutes(4), 91.0);
            insertPrediction(tagSrlNo, target.minusMinutes(2), 92.0);
            insertPrediction(tagSrlNo, target,                  93.0);  // 정확 일치
            insertPrediction(tagSrlNo, target.plusMinutes(2),   94.0);
            insertPrediction(tagSrlNo, target.plusMinutes(4),   95.0);
        }
        em.flush();

        List<String> tagSrlNos = List.of(
                TEST_TAG_PREFIX + 1, TEST_TAG_PREFIX + 2, TEST_TAG_PREFIX + 3);

        // when
        List<TagPredictionMatchDto> result = tagPredictionRepository
                .findNearestByTagSrlNos(tagSrlNos, WINDOW_MINUTES);

        // then: 태그 3건 → 결과 3건, 모두 정확 일치 (predcVal=93.0)
        assertThat(result).hasSize(3);
        assertThat(result).extracting(TagPredictionMatchDto::tagSrlNo)
                .containsExactlyInAnyOrderElementsOf(tagSrlNos);
        assertThat(result).allMatch(r -> r.predcVal().doubleValue() == 93.0);
    }

    @Test
    void 윈도우_밖_예측행은_반환되지_않는다() {
        // given: rawdata 최신 acq_dtm 1건 + predc 는 윈도우 ±5분 밖 (target - 10분, target + 10분)
        String tagSrlNo = TEST_TAG_PREFIX + "WINDOW-MISS";
        LocalDateTime nowBase = LocalDateTime.now().minusMinutes(10);
        LocalDateTime target = nowBase.plusHours(1);
        insertRawData(tagSrlNo, nowBase, 1.0);
        insertPrediction(tagSrlNo, target.minusMinutes(10), 10.0);
        insertPrediction(tagSrlNo, target.plusMinutes(10),  20.0);
        em.flush();

        // when
        List<TagPredictionMatchDto> result = tagPredictionRepository
                .findNearestByTagSrlNos(List.of(tagSrlNo), WINDOW_MINUTES);

        // then: 윈도우 밖이라 LATERAL 미반환 → 결과 0건 (Service 가 null 매핑)
        assertThat(result).isEmpty();
    }

    @Test
    void 빈_태그_목록은_빈_결과를_반환한다() {
        List<TagPredictionMatchDto> result = tagPredictionRepository
                .findNearestByTagSrlNos(List.of(), WINDOW_MINUTES);
        assertThat(result).isEmpty();
    }

    /**
     * rawdata 시계열 native INSERT — BaseEntity AuditingEntityListener 우회 (테스트 픽스처 전용).
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

    /**
     * predc 시계열 native INSERT — AuditingEntityListener 우회 (테스트 픽스처 전용).
     */
    private void insertPrediction(String tagSrlNo, LocalDateTime predcDtm, double predcVal) {
        em.createNativeQuery("""
                        INSERT INTO predc_1m_h (
                            predc_id, predc_dtm, tag_srl_no, predc_val, rgstr_dtm, rgstr_id
                        ) VALUES (
                            nextval('seq_predc_id'), :predcDtm, :tagSrlNo, :predcVal,
                            :nowDtm, 'test'
                        )
                        """)
                .setParameter("predcDtm", predcDtm)
                .setParameter("tagSrlNo", tagSrlNo)
                .setParameter("predcVal", predcVal)
                .setParameter("nowDtm", LocalDateTime.now())
                .executeUpdate();
    }
}

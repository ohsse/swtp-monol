package com.mo.swtp.facility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mo.swtp.auth.web.ApiErrorResponseWriter;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.DwtDto;
import com.mo.swtp.facility.dto.DwtUpsertDto;
import com.mo.swtp.facility.dto.FacilityDto;
import com.mo.swtp.facility.dto.FacilitySearchDto;
import com.mo.swtp.facility.dto.PwtfUpsertDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.service.FacilityService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link FacilityService} 통합 테스트 — 실제 PostgreSQL 연결 + JPA JOINED 다형성 영속 검증.
 *
 * <p>{@code WebEnvironment.NONE}: 웹 서버 없이 서비스·JPA 레이어만 기동. 각 테스트는 {@code @Transactional}
 * 로 격리되며 테스트 종료 후 자동 롤백된다.</p>
 *
 * <p>시설명({@code facility_m.facility_nm}) UNIQUE 제약 + 기존 DB 데이터와의 충돌을 방지하기 위해
 * 시설명에 UUID prefix 를 사용한다.</p>
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class FacilityServiceIntegrationTest {

    @MockitoBean
    private ApiErrorResponseWriter apiErrorResponseWriter;

    @Autowired
    private FacilityService facilityService;

    @PersistenceContext
    private EntityManager em;

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * JPA JOINED 다형성 read-only 매핑 ({@code @Column(insertable=false, updatable=false)}) 의
     * 알려진 제약 회피: INSERT 후 동일 트랜잭션 내 영속 컨텍스트 캐시에는 {@code facilityType}
     * 필드가 null 인 상태가 남는다. 1차 캐시 비우고 DB 재조회로 facility_type_cd 값을 강제 매핑한다.
     */
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    void PWTF_DWT_각1건_등록_후_type_PWTF_조회_시_PWTF만_반환() {
        String pwtfNm = uniqueName("IT정수지");
        String dwtNm = uniqueName("IT배수지");
        facilityService.saveFacility(buildPwtf(pwtfNm));
        facilityService.saveFacility(buildDwt(dwtNm, new BigDecimal("2.5")));
        flushAndClear();

        FacilitySearchDto search = new FacilitySearchDto();
        search.setFacilityTypeCd(FacilityType.PWTF);
        List<FacilityDto> result = facilityService.findAllFacilities(search);

        assertThat(result).extracting(FacilityDto::getFacilityNm).contains(pwtfNm);
        assertThat(result).extracting(FacilityDto::getFacilityNm).doesNotContain(dwtNm);
        assertThat(result).extracting(FacilityDto::getFacilityTypeCd)
                .allMatch(t -> t == FacilityType.PWTF);
    }

    @Test
    void DELETE_후_useYn_Y_조회_응답에서_해당_시설_제외() {
        String nm = uniqueName("IT논리삭제");
        String facilityId = facilityService.saveFacility(buildPwtf(nm));
        facilityService.deactivateFacility(facilityId);
        flushAndClear();

        FacilitySearchDto search = new FacilitySearchDto();
        search.setFacilityTypeCd(FacilityType.PWTF);
        search.setUseYn(YnType.Y);
        List<FacilityDto> result = facilityService.findAllFacilities(search);

        assertThat(result).extracting(FacilityDto::getFacilityNm).doesNotContain(nm);
        // useYn=N 필터로는 포함 확인
        search.setUseYn(YnType.N);
        List<FacilityDto> deactivated = facilityService.findAllFacilities(search);
        assertThat(deactivated).extracting(FacilityDto::getFacilityNm).contains(nm);
    }

    @Test
    void 시설명_UNIQUE_충돌_시_existsBy_사전차단으로_DUPLICATE_FACILITY_NM_예외() {
        String nm = uniqueName("IT중복");
        facilityService.saveFacility(buildPwtf(nm));

        assertThatThrownBy(() -> facilityService.saveFacility(buildPwtf(nm)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.DUPLICATE_FACILITY_NM);
    }

    @Test
    void DWT_등록_후_단건조회_시_minReqPrsr_노출() {
        String nm = uniqueName("IT배수지M");
        String facilityId = facilityService.saveFacility(buildDwt(nm, new BigDecimal("3.7")));
        flushAndClear();

        FacilityDto dto = facilityService.findFacilityDto(facilityId);

        assertThat(dto.getFacilityTypeCd()).isEqualTo(FacilityType.DWT);
        assertThat(dto).isInstanceOf(DwtDto.class);
        assertThat(((DwtDto) dto).getMinReqPrsr()).isEqualByComparingTo("3.7");
    }

    private PwtfUpsertDto buildPwtf(String nm) {
        PwtfUpsertDto dto = new PwtfUpsertDto();
        dto.setFacilityTypeCd(FacilityType.PWTF);
        dto.setFacilityNm(nm);
        dto.setParentFacilityId(null);
        dto.setDispOrd(1);
        dto.setMainYn(YnType.Y);
        return dto;
    }

    private DwtUpsertDto buildDwt(String nm, BigDecimal minReqPrsr) {
        DwtUpsertDto dto = new DwtUpsertDto();
        dto.setFacilityTypeCd(FacilityType.DWT);
        dto.setFacilityNm(nm);
        dto.setParentFacilityId(null);
        dto.setDispOrd(1);
        dto.setMainYn(YnType.Y);
        dto.setMinReqPrsr(minReqPrsr);
        dto.setMinReqBranchPrsr(new BigDecimal("0.8"));
        return dto;
    }
}

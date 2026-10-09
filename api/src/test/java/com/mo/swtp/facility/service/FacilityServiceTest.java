package com.mo.swtp.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.ChemicalBuilding;
import com.mo.swtp.facility.domain.DistributionWaterTank;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.PressureBoosterStation;
import com.mo.swtp.facility.domain.PurifiedWaterTank;
import com.mo.swtp.facility.domain.Reservoir;
import com.mo.swtp.facility.domain.enumtype.FacilityGroup;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.ChmbDto;
import com.mo.swtp.facility.dto.ChmbUpsertDto;
import com.mo.swtp.facility.dto.DwtDto;
import com.mo.swtp.facility.dto.DwtUpsertDto;
import com.mo.swtp.facility.dto.FacilityDto;
import com.mo.swtp.facility.dto.FacilityUpsertDto;
import com.mo.swtp.facility.dto.PrsfDto;
import com.mo.swtp.facility.dto.PrsfUpsertDto;
import com.mo.swtp.facility.dto.PwtfDto;
import com.mo.swtp.facility.dto.PwtfUpsertDto;
import com.mo.swtp.facility.dto.RsvDto;
import com.mo.swtp.facility.dto.RsvUpsertDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FacilityService} 단위 테스트 — Mockito 격리.
 *
 * <p>시나리오: 자식 4종(PWTF/DWT/RSV/PRSF) 등록·수정 정상 + 신규 운영시설 CHMB 등록/조회
 * (switch default 미진입 + facilityGroupCd OPERATION 파생) + 논리 삭제 + ErrorCode 4종 분기
 * (FACILITY_NOT_FOUND·DUPLICATE_FACILITY_NM·FACILITY_TYPE_MISMATCH·INVALID_PARENT_FACILITY_ID).</p>
 */
@ExtendWith(MockitoExtension.class)
class FacilityServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @InjectMocks private FacilityService facilityService;

    // ========== 등록 정상 시나리오 (자식 4종) ==========

    @Test
    void PWTF_등록_정상() {
        PwtfUpsertDto dto = buildPwtfDto("정수지1", null, 1, YnType.Y);
        given(facilityRepository.existsByFacilityNm("정수지1")).willReturn(false);
        given(facilityRepository.save(any(Facility.class))).willAnswer(inv -> inv.getArgument(0));

        facilityService.saveFacility(dto);

        then(facilityRepository).should().save(any(PurifiedWaterTank.class));
    }

    @Test
    void DWT_등록_정상_minReqPrsr_포함() {
        DwtUpsertDto dto = buildDwtDto("배수지1", null, 1, YnType.Y, new BigDecimal("2.5"));
        given(facilityRepository.existsByFacilityNm("배수지1")).willReturn(false);
        given(facilityRepository.save(any(Facility.class))).willAnswer(inv -> inv.getArgument(0));

        facilityService.saveFacility(dto);

        then(facilityRepository).should().save(any(DistributionWaterTank.class));
    }

    @Test
    void RSV_등록_정상() {
        RsvUpsertDto dto = buildRsvDto("저수지1", null, 1, YnType.Y);
        given(facilityRepository.existsByFacilityNm("저수지1")).willReturn(false);
        given(facilityRepository.save(any(Facility.class))).willAnswer(inv -> inv.getArgument(0));

        facilityService.saveFacility(dto);

        then(facilityRepository).should().save(any(Reservoir.class));
    }

    @Test
    void PRSF_등록_정상() {
        PrsfUpsertDto dto = buildPrsfDto("가압장1", null, 1, YnType.Y);
        given(facilityRepository.existsByFacilityNm("가압장1")).willReturn(false);
        given(facilityRepository.save(any(Facility.class))).willAnswer(inv -> inv.getArgument(0));

        facilityService.saveFacility(dto);

        then(facilityRepository).should().save(any(PressureBoosterStation.class));
    }

    @Test
    void 신규유형_CHMB_등록_정상_switch_default_미진입() {
        // 신규 운영시설 유형 CHMB 등록 — saveFacility switch 가 default(FACILITY_TYPE_MISMATCH) 로
        // 빠지지 않고 ChemicalBuilding 자식으로 영속됨을 검증한다.
        ChmbUpsertDto dto = buildChmbDto("약품동1", null, 1, YnType.Y);
        given(facilityRepository.existsByFacilityNm("약품동1")).willReturn(false);
        given(facilityRepository.save(any(Facility.class))).willAnswer(inv -> inv.getArgument(0));

        facilityService.saveFacility(dto);

        then(facilityRepository).should().save(any(ChemicalBuilding.class));
    }

    // ========== 수정 시나리오 (자식 4종 + DWT minReqPrsr 변경 검증) ==========

    @Test
    void PWTF_수정_정상() {
        PurifiedWaterTank entity = createPwtfWithId("uuid-pwtf-1", "정수지1", null);
        PwtfUpsertDto dto = buildPwtfDto("정수지1-수정", null, 2, YnType.N);
        given(facilityRepository.findById("uuid-pwtf-1")).willReturn(Optional.of(entity));
        given(facilityRepository.existsByFacilityNmAndFacilityIdNot("정수지1-수정", "uuid-pwtf-1"))
                .willReturn(false);

        facilityService.updateFacility("uuid-pwtf-1", dto);

        assertThat(entity.getFacilityNm()).isEqualTo("정수지1-수정");
        assertThat(entity.getDispOrd()).isEqualTo(2);
    }

    @Test
    void DWT_수정_시_changeMinReqPrsr_호출_검증() {
        DistributionWaterTank entity = createDwtWithId("uuid-dwt-1", "배수지1", new BigDecimal("2.0"));
        DwtUpsertDto dto = buildDwtDto("배수지1-수정", null, 1, YnType.Y, new BigDecimal("3.5"));
        given(facilityRepository.findById("uuid-dwt-1")).willReturn(Optional.of(entity));
        given(facilityRepository.existsByFacilityNmAndFacilityIdNot("배수지1-수정", "uuid-dwt-1"))
                .willReturn(false);

        facilityService.updateFacility("uuid-dwt-1", dto);

        assertThat(entity.getMinReqPrsr()).isEqualByComparingTo("3.5");
        assertThat(entity.getFacilityNm()).isEqualTo("배수지1-수정");
    }

    @Test
    void RSV_수정_정상() {
        Reservoir entity = createRsvWithId("uuid-rsv-1", "저수지1");
        RsvUpsertDto dto = buildRsvDto("저수지1-수정", null, 1, YnType.Y);
        given(facilityRepository.findById("uuid-rsv-1")).willReturn(Optional.of(entity));
        given(facilityRepository.existsByFacilityNmAndFacilityIdNot("저수지1-수정", "uuid-rsv-1"))
                .willReturn(false);

        facilityService.updateFacility("uuid-rsv-1", dto);

        assertThat(entity.getFacilityNm()).isEqualTo("저수지1-수정");
    }

    @Test
    void PRSF_수정_정상() {
        PressureBoosterStation entity = createPrsfWithId("uuid-prsf-1", "가압장1");
        PrsfUpsertDto dto = buildPrsfDto("가압장1-수정", null, 1, YnType.Y);
        given(facilityRepository.findById("uuid-prsf-1")).willReturn(Optional.of(entity));
        given(facilityRepository.existsByFacilityNmAndFacilityIdNot("가압장1-수정", "uuid-prsf-1"))
                .willReturn(false);

        facilityService.updateFacility("uuid-prsf-1", dto);

        assertThat(entity.getFacilityNm()).isEqualTo("가압장1-수정");
    }

    // ========== 논리 삭제 ==========

    @Test
    void 논리_삭제_시_deactivate_호출() {
        PurifiedWaterTank entity = createPwtfWithId("uuid-pwtf-1", "정수지1", null);
        given(facilityRepository.findById("uuid-pwtf-1")).willReturn(Optional.of(entity));

        facilityService.deactivateFacility("uuid-pwtf-1");

        assertThat(entity.getUseYn()).isEqualTo(YnType.N);
    }

    // ========== ErrorCode 분기 ==========

    @Test
    void 등록_시_DUPLICATE_FACILITY_NM_예외() {
        PwtfUpsertDto dto = buildPwtfDto("정수지1", null, 1, YnType.Y);
        given(facilityRepository.existsByFacilityNm("정수지1")).willReturn(true);

        assertThatThrownBy(() -> facilityService.saveFacility(dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.DUPLICATE_FACILITY_NM);
        then(facilityRepository).should(never()).save(any(Facility.class));
    }

    @Test
    void 수정_시_DUPLICATE_FACILITY_NM_예외() {
        PurifiedWaterTank entity = createPwtfWithId("uuid-pwtf-1", "정수지1", null);
        PwtfUpsertDto dto = buildPwtfDto("다른정수지", null, 1, YnType.Y);
        given(facilityRepository.findById("uuid-pwtf-1")).willReturn(Optional.of(entity));
        given(facilityRepository.existsByFacilityNmAndFacilityIdNot("다른정수지", "uuid-pwtf-1"))
                .willReturn(true);

        assertThatThrownBy(() -> facilityService.updateFacility("uuid-pwtf-1", dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.DUPLICATE_FACILITY_NM);
    }

    @Test
    void 수정_시_FACILITY_NOT_FOUND_예외() {
        PwtfUpsertDto dto = buildPwtfDto("정수지1", null, 1, YnType.Y);
        given(facilityRepository.findById("non-existent")).willReturn(Optional.empty());

        assertThatThrownBy(() -> facilityService.updateFacility("non-existent", dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void 삭제_시_FACILITY_NOT_FOUND_예외() {
        given(facilityRepository.findById("non-existent")).willReturn(Optional.empty());

        assertThatThrownBy(() -> facilityService.deactivateFacility("non-existent"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void 단건조회_시_FACILITY_NOT_FOUND_예외() {
        given(facilityRepository.findById("non-existent")).willReturn(Optional.empty());

        assertThatThrownBy(() -> facilityService.findFacilityDto("non-existent"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    // ========== 자식 타입별 findFacilityDto 다형 응답 ==========

    @Test
    void 단건조회_DWT_시_DwtDto_인스턴스_반환_minReqPrsr_매핑() {
        DistributionWaterTank entity = createDwtWithId("uuid-dwt-1", "배수지1", new BigDecimal("2.5"));
        given(facilityRepository.findById("uuid-dwt-1")).willReturn(Optional.of(entity));

        FacilityDto dto = facilityService.findFacilityDto("uuid-dwt-1");

        assertThat(dto).isInstanceOf(DwtDto.class);
        assertThat(((DwtDto) dto).getMinReqPrsr()).isEqualByComparingTo("2.5");
        assertThat(dto.getFacilityNm()).isEqualTo("배수지1");
        assertThat(dto.getFacilityTypeCd()).isEqualTo(FacilityType.DWT);
    }

    @Test
    void 단건조회_PWTF_시_PwtfDto_인스턴스_반환() {
        PurifiedWaterTank entity = createPwtfWithId("uuid-pwtf-1", "정수지1", null);
        given(facilityRepository.findById("uuid-pwtf-1")).willReturn(Optional.of(entity));

        FacilityDto dto = facilityService.findFacilityDto("uuid-pwtf-1");

        assertThat(dto).isInstanceOf(PwtfDto.class);
        assertThat(dto.getFacilityNm()).isEqualTo("정수지1");
        assertThat(dto.getFacilityTypeCd()).isEqualTo(FacilityType.PWTF);
    }

    @Test
    void 단건조회_RSV_시_RsvDto_인스턴스_반환() {
        Reservoir entity = createRsvWithId("uuid-rsv-1", "저수지1");
        given(facilityRepository.findById("uuid-rsv-1")).willReturn(Optional.of(entity));

        FacilityDto dto = facilityService.findFacilityDto("uuid-rsv-1");

        assertThat(dto).isInstanceOf(RsvDto.class);
        assertThat(dto.getFacilityNm()).isEqualTo("저수지1");
        assertThat(dto.getFacilityTypeCd()).isEqualTo(FacilityType.RSV);
    }

    @Test
    void 단건조회_PRSF_시_PrsfDto_인스턴스_반환() {
        PressureBoosterStation entity = createPrsfWithId("uuid-prsf-1", "가압장1");
        given(facilityRepository.findById("uuid-prsf-1")).willReturn(Optional.of(entity));

        FacilityDto dto = facilityService.findFacilityDto("uuid-prsf-1");

        assertThat(dto).isInstanceOf(PrsfDto.class);
        assertThat(dto.getFacilityNm()).isEqualTo("가압장1");
        assertThat(dto.getFacilityTypeCd()).isEqualTo(FacilityType.PRSF);
    }

    @Test
    void 단건조회_CHMB_시_ChmbDto_반환_facilityGroupCd_OPERATION_파생() {
        ChemicalBuilding entity = createChmbWithId("uuid-chmb-1", "약품동1");
        given(facilityRepository.findById("uuid-chmb-1")).willReturn(Optional.of(entity));

        FacilityDto dto = facilityService.findFacilityDto("uuid-chmb-1");

        assertThat(dto).isInstanceOf(ChmbDto.class);
        assertThat(dto.getFacilityNm()).isEqualTo("약품동1");
        assertThat(dto.getFacilityTypeCd()).isEqualTo(FacilityType.CHMB);
        assertThat(dto.getFacilityGroupCd()).isEqualTo(FacilityGroup.OPERATION);
    }

    @Test
    void 등록_시_부모_미존재_INVALID_PARENT_FACILITY_ID_예외() {
        PwtfUpsertDto dto = buildPwtfDto("정수지1", "non-existent-parent", 1, YnType.Y);
        given(facilityRepository.existsById("non-existent-parent")).willReturn(false);

        assertThatThrownBy(() -> facilityService.saveFacility(dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.INVALID_PARENT_FACILITY_ID);
    }

    @Test
    void 수정_시_자기참조_INVALID_PARENT_FACILITY_ID_예외() {
        PurifiedWaterTank entity = createPwtfWithId("uuid-pwtf-1", "정수지1", null);
        PwtfUpsertDto dto = buildPwtfDto("정수지1", "uuid-pwtf-1", 1, YnType.Y);
        given(facilityRepository.findById("uuid-pwtf-1")).willReturn(Optional.of(entity));

        assertThatThrownBy(() -> facilityService.updateFacility("uuid-pwtf-1", dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.INVALID_PARENT_FACILITY_ID);
    }

    @Test
    void 수정_시_FACILITY_TYPE_MISMATCH_예외() {
        // path 의 시설은 PWTF, body 는 DWT 요청 → type 불일치
        PurifiedWaterTank entity = createPwtfWithId("uuid-pwtf-1", "정수지1", null);
        DwtUpsertDto dto = buildDwtDto("정수지1-수정", null, 1, YnType.Y, new BigDecimal("2.5"));
        given(facilityRepository.findById("uuid-pwtf-1")).willReturn(Optional.of(entity));

        assertThatThrownBy(() -> facilityService.updateFacility("uuid-pwtf-1", dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_TYPE_MISMATCH);
    }

    // ========== Helper ==========

    private PwtfUpsertDto buildPwtfDto(String nm, String parentId, int ord, YnType mainYn) {
        PwtfUpsertDto dto = new PwtfUpsertDto();
        dto.setFacilityTypeCd(FacilityType.PWTF);
        dto.setFacilityNm(nm);
        dto.setParentFacilityId(parentId);
        dto.setDispOrd(ord);
        dto.setMainYn(mainYn);
        return dto;
    }

    private DwtUpsertDto buildDwtDto(String nm, String parentId, int ord, YnType mainYn,
            BigDecimal minReqPrsr) {
        return buildDwtDto(nm, parentId, ord, mainYn, minReqPrsr, new BigDecimal("0.8"));
    }

    private DwtUpsertDto buildDwtDto(String nm, String parentId, int ord, YnType mainYn,
            BigDecimal minReqPrsr, BigDecimal minReqBranchPrsr) {
        DwtUpsertDto dto = new DwtUpsertDto();
        dto.setFacilityTypeCd(FacilityType.DWT);
        dto.setFacilityNm(nm);
        dto.setParentFacilityId(parentId);
        dto.setDispOrd(ord);
        dto.setMainYn(mainYn);
        dto.setMinReqPrsr(minReqPrsr);
        dto.setMinReqBranchPrsr(minReqBranchPrsr);
        return dto;
    }

    private RsvUpsertDto buildRsvDto(String nm, String parentId, int ord, YnType mainYn) {
        RsvUpsertDto dto = new RsvUpsertDto();
        dto.setFacilityTypeCd(FacilityType.RSV);
        dto.setFacilityNm(nm);
        dto.setParentFacilityId(parentId);
        dto.setDispOrd(ord);
        dto.setMainYn(mainYn);
        return dto;
    }

    private PrsfUpsertDto buildPrsfDto(String nm, String parentId, int ord, YnType mainYn) {
        PrsfUpsertDto dto = new PrsfUpsertDto();
        dto.setFacilityTypeCd(FacilityType.PRSF);
        dto.setFacilityNm(nm);
        dto.setParentFacilityId(parentId);
        dto.setDispOrd(ord);
        dto.setMainYn(mainYn);
        return dto;
    }

    private ChmbUpsertDto buildChmbDto(String nm, String parentId, int ord, YnType mainYn) {
        ChmbUpsertDto dto = new ChmbUpsertDto();
        dto.setFacilityTypeCd(FacilityType.CHMB);
        dto.setFacilityNm(nm);
        dto.setParentFacilityId(parentId);
        dto.setDispOrd(ord);
        dto.setMainYn(mainYn);
        return dto;
    }

    private PurifiedWaterTank createPwtfWithId(String id, String nm, String parentId) {
        PurifiedWaterTank entity = PurifiedWaterTank.create(nm, parentId, 1, YnType.Y);
        setFacilityIdAndType(entity, id, FacilityType.PWTF);
        return entity;
    }

    private DistributionWaterTank createDwtWithId(String id, String nm, BigDecimal minReqPrsr) {
        DistributionWaterTank entity = DistributionWaterTank.create(
                nm, null, 1, YnType.Y, minReqPrsr, new BigDecimal("0.8"));
        setFacilityIdAndType(entity, id, FacilityType.DWT);
        return entity;
    }

    private Reservoir createRsvWithId(String id, String nm) {
        Reservoir entity = Reservoir.create(nm, null, 1, YnType.Y);
        setFacilityIdAndType(entity, id, FacilityType.RSV);
        return entity;
    }

    private PressureBoosterStation createPrsfWithId(String id, String nm) {
        PressureBoosterStation entity = PressureBoosterStation.create(nm, null, 1, YnType.Y);
        setFacilityIdAndType(entity, id, FacilityType.PRSF);
        return entity;
    }

    private ChemicalBuilding createChmbWithId(String id, String nm) {
        ChemicalBuilding entity = ChemicalBuilding.create(nm, null, 1, YnType.Y);
        setFacilityIdAndType(entity, id, FacilityType.CHMB);
        return entity;
    }

    /**
     * Facility 의 facilityId 는 {@code @GeneratedValue(UUID)} 자동 생성, facilityType 은
     * {@code @DiscriminatorColumn(insertable=false, updatable=false)} 로 영속 컨텍스트가 자동 관리한다.
     * 단위 테스트에서 영속화된 상태를 모사하기 위해 reflection 으로 두 필드를 동시 주입한다.
     */
    private void setFacilityIdAndType(Facility facility, String id, FacilityType type) {
        try {
            Field idField = Facility.class.getDeclaredField("facilityId");
            idField.setAccessible(true);
            idField.set(facility, id);
            Field typeField = Facility.class.getDeclaredField("facilityType");
            typeField.setAccessible(true);
            typeField.set(facility, type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

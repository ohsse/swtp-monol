package com.mo.swtp.facility.service;

import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.ActivatedCarbonFilter;
import com.mo.swtp.facility.domain.ChemicalBuilding;
import com.mo.swtp.facility.domain.DewateringBuilding;
import com.mo.swtp.facility.domain.DistributionWaterTank;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.FiltrationBuilding;
import com.mo.swtp.facility.domain.PreOzonationBuilding;
import com.mo.swtp.facility.domain.PressureBoosterStation;
import com.mo.swtp.facility.domain.PurifiedWaterTank;
import com.mo.swtp.facility.domain.Reservoir;
import com.mo.swtp.facility.domain.SolarPowerFacility;
import com.mo.swtp.facility.domain.WaterTransmissionBuilding;
import com.mo.swtp.facility.dto.AcfbUpsertDto;
import com.mo.swtp.facility.dto.ChmbUpsertDto;
import com.mo.swtp.facility.dto.DewbUpsertDto;
import com.mo.swtp.facility.dto.DwtUpsertDto;
import com.mo.swtp.facility.dto.FacilityDto;
import com.mo.swtp.facility.dto.FacilitySearchDto;
import com.mo.swtp.facility.dto.FacilityUpsertDto;
import com.mo.swtp.facility.dto.FltbUpsertDto;
import com.mo.swtp.facility.dto.PozbUpsertDto;
import com.mo.swtp.facility.dto.PrsfUpsertDto;
import com.mo.swtp.facility.dto.PwtfUpsertDto;
import com.mo.swtp.facility.dto.RsvUpsertDto;
import com.mo.swtp.facility.dto.SolarUpsertDto;
import com.mo.swtp.facility.dto.WtbldUpsertDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시설 CRUD 서비스 — JPA JOINED 다형성 + Jackson 다형성 DTO 디스패처.
 *
 * <p>{@link FacilityUpsertDto} 추상 부모를 Jackson 이 {@code facilityTypeCd} 필드로 자식 11종
 * ({@link PwtfUpsertDto}·{@link DwtUpsertDto}·{@link RsvUpsertDto}·{@link PrsfUpsertDto} +
 * 시설_도메인_확장 신규 7종 {@link WtbldUpsertDto}·{@link ChmbUpsertDto}·{@link AcfbUpsertDto}·
 * {@link PozbUpsertDto}·{@link FltbUpsertDto}·{@link DewbUpsertDto}·{@link SolarUpsertDto}) 으로 자동
 * 역직렬화한다 (요청측 11종 — POINT 는 SCADA 자동 생성으로 등록 API 대상 외). 본 서비스의
 * {@link #saveFacility}·{@link #updateFacility} 는 Java 21 switch 패턴 매칭으로 자식 타입별 private
 * 메서드로 디스패치한다.</p>
 *
 * <p>도메인 룰: 자식 종류별 정적 팩토리 ({@code PurifiedWaterTank.create} 등) 를 통해서만 영속한다 —
 * 부모 {@link Facility} 가 abstract 이고 자식 종류별로 자식 테이블 INSERT 가 별도 발생하기 때문.</p>
 *
 * <p>시설물관리기능 ANALYZE1·PLAN1 (2026-05-11) 도입.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityService {

    private final FacilityRepository facilityRepository;

    /**
     * 시설 목록을 조회한다. type/useYn 필터는 NULL 허용 (NULL 시 전체).
     *
     * <p>정렬: {@code use_yn} DESC (활성 우선) → {@code disp_ord} ASC → {@code facility_nm} ASC.
     * user_m·tag_m 선례 동일 패턴.</p>
     */
    public List<FacilityDto> findAllFacilities(FacilitySearchDto searchDto) {
        return facilityRepository.findFacilities(searchDto).stream()
                .map(FacilityDto::from)
                .toList();
    }

    /**
     * 단건 시설을 조회한다.
     *
     * @throws RestApiException FACILITY_NOT_FOUND — 존재하지 않는 facilityId
     */
    public FacilityDto findFacilityDto(String facilityId) {
        return FacilityDto.from(findFacilityOrThrow(facilityId));
    }

    /**
     * 신규 시설을 등록한다. 자식 종류는 {@link FacilityUpsertDto#getFacilityTypeCd()} 로 결정한다.
     *
     * @return 생성된 시설의 facilityId
     * @throws RestApiException DUPLICATE_FACILITY_NM·INVALID_PARENT_FACILITY_ID
     */
    @Transactional
    public String saveFacility(FacilityUpsertDto dto) {
        validateParentFacility(dto.getParentFacilityId(), null);
        validateDuplicateFacilityNm(dto.getFacilityNm());
        return switch (dto) {
            case PwtfUpsertDto p -> savePwtf(p);
            case DwtUpsertDto d -> saveDwt(d);
            case RsvUpsertDto r -> saveRsv(r);
            case PrsfUpsertDto pr -> savePrsf(pr);
            case WtbldUpsertDto w -> saveWtbld(w);
            case ChmbUpsertDto c -> saveChmb(c);
            case AcfbUpsertDto a -> saveAcfb(a);
            case PozbUpsertDto po -> savePozb(po);
            case FltbUpsertDto f -> saveFltb(f);
            case DewbUpsertDto de -> saveDewb(de);
            case SolarUpsertDto s -> saveSolar(s);
            default -> throw new RestApiException(FacilityErrorCode.FACILITY_TYPE_MISMATCH);
        };
    }

    /**
     * 시설을 수정한다. JPA dirty checking 으로 UPDATE 가 발행된다.
     *
     * @throws RestApiException FACILITY_NOT_FOUND·FACILITY_TYPE_MISMATCH·DUPLICATE_FACILITY_NM·INVALID_PARENT_FACILITY_ID
     */
    @Transactional
    public void updateFacility(String facilityId, FacilityUpsertDto dto) {
        Facility facility = findFacilityOrThrow(facilityId);
        validateTypeMatch(facility, dto);
        validateParentFacility(dto.getParentFacilityId(), facilityId);
        validateDuplicateFacilityNmOnUpdate(dto.getFacilityNm(), facilityId);
        switch (dto) {
            case PwtfUpsertDto p -> updatePwtf((PurifiedWaterTank) facility, p);
            case DwtUpsertDto d -> updateDwt((DistributionWaterTank) facility, d);
            case RsvUpsertDto r -> updateRsv((Reservoir) facility, r);
            case PrsfUpsertDto pr -> updatePrsf((PressureBoosterStation) facility, pr);
            case WtbldUpsertDto w -> updateWtbld((WaterTransmissionBuilding) facility, w);
            case ChmbUpsertDto c -> updateChmb((ChemicalBuilding) facility, c);
            case AcfbUpsertDto a -> updateAcfb((ActivatedCarbonFilter) facility, a);
            case PozbUpsertDto po -> updatePozb((PreOzonationBuilding) facility, po);
            case FltbUpsertDto f -> updateFltb((FiltrationBuilding) facility, f);
            case DewbUpsertDto de -> updateDewb((DewateringBuilding) facility, de);
            case SolarUpsertDto s -> updateSolar((SolarPowerFacility) facility, s);
            default -> throw new RestApiException(FacilityErrorCode.FACILITY_TYPE_MISMATCH);
        }
    }

    /**
     * 시설을 논리 삭제한다 ({@code use_yn = N}).
     *
     * <p>물리 삭제하지 않는 이유: {@code instrument_m}·{@code ai_drvn_mod_p}·{@code rawdata_1m_h} 가
     * facilityId 를 논리 참조하므로 (시설물관리기능 ANALYZE1 사용자 결정).</p>
     *
     * @throws RestApiException FACILITY_NOT_FOUND
     */
    @Transactional
    public void deactivateFacility(String facilityId) {
        Facility facility = findFacilityOrThrow(facilityId);
        facility.deactivate();
    }

    private Facility findFacilityOrThrow(String facilityId) {
        return facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND));
    }

    private String savePwtf(PwtfUpsertDto dto) {
        PurifiedWaterTank entity = PurifiedWaterTank.create(
                dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
        return facilityRepository.save(entity).getFacilityId();
    }

    private String saveDwt(DwtUpsertDto dto) {
        DistributionWaterTank entity = DistributionWaterTank.create(
                dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn(),
                dto.getMinReqPrsr(), dto.getMinReqBranchPrsr());
        return facilityRepository.save(entity).getFacilityId();
    }

    private String saveRsv(RsvUpsertDto dto) {
        Reservoir entity = Reservoir.create(
                dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
        return facilityRepository.save(entity).getFacilityId();
    }

    private String savePrsf(PrsfUpsertDto dto) {
        PressureBoosterStation entity = PressureBoosterStation.create(
                dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
        return facilityRepository.save(entity).getFacilityId();
    }

    private String saveWtbld(WtbldUpsertDto dto) {
        WaterTransmissionBuilding entity = WaterTransmissionBuilding.create(
                dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
        return facilityRepository.save(entity).getFacilityId();
    }

    private String saveChmb(ChmbUpsertDto dto) {
        ChemicalBuilding entity = ChemicalBuilding.create(
                dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
        return facilityRepository.save(entity).getFacilityId();
    }

    private String saveAcfb(AcfbUpsertDto dto) {
        ActivatedCarbonFilter entity = ActivatedCarbonFilter.create(
                dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
        return facilityRepository.save(entity).getFacilityId();
    }

    private String savePozb(PozbUpsertDto dto) {
        PreOzonationBuilding entity = PreOzonationBuilding.create(
                dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
        return facilityRepository.save(entity).getFacilityId();
    }

    private String saveFltb(FltbUpsertDto dto) {
        FiltrationBuilding entity = FiltrationBuilding.create(
                dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
        return facilityRepository.save(entity).getFacilityId();
    }

    private String saveDewb(DewbUpsertDto dto) {
        DewateringBuilding entity = DewateringBuilding.create(
                dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
        return facilityRepository.save(entity).getFacilityId();
    }

    private String saveSolar(SolarUpsertDto dto) {
        SolarPowerFacility entity = SolarPowerFacility.create(
                dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
        return facilityRepository.save(entity).getFacilityId();
    }

    private void updatePwtf(PurifiedWaterTank entity, PwtfUpsertDto dto) {
        entity.changeInfo(dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
    }

    private void updateDwt(DistributionWaterTank entity, DwtUpsertDto dto) {
        entity.changeInfo(dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
        entity.changeMinReqPrsr(dto.getMinReqPrsr());
        entity.changeMinReqBranchPrsr(dto.getMinReqBranchPrsr());
    }

    private void updateRsv(Reservoir entity, RsvUpsertDto dto) {
        entity.changeInfo(dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
    }

    private void updatePrsf(PressureBoosterStation entity, PrsfUpsertDto dto) {
        entity.changeInfo(dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
    }

    private void updateWtbld(WaterTransmissionBuilding entity, WtbldUpsertDto dto) {
        entity.changeInfo(dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
    }

    private void updateChmb(ChemicalBuilding entity, ChmbUpsertDto dto) {
        entity.changeInfo(dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
    }

    private void updateAcfb(ActivatedCarbonFilter entity, AcfbUpsertDto dto) {
        entity.changeInfo(dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
    }

    private void updatePozb(PreOzonationBuilding entity, PozbUpsertDto dto) {
        entity.changeInfo(dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
    }

    private void updateFltb(FiltrationBuilding entity, FltbUpsertDto dto) {
        entity.changeInfo(dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
    }

    private void updateDewb(DewateringBuilding entity, DewbUpsertDto dto) {
        entity.changeInfo(dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
    }

    private void updateSolar(SolarPowerFacility entity, SolarUpsertDto dto) {
        entity.changeInfo(dto.getFacilityNm(), dto.getParentFacilityId(), dto.getDispOrd(), dto.getMainYn());
    }

    private void validateParentFacility(String parentFacilityId, String selfFacilityId) {
        if (parentFacilityId == null) {
            return;
        }
        if (selfFacilityId != null && selfFacilityId.equals(parentFacilityId)) {
            throw new RestApiException(FacilityErrorCode.INVALID_PARENT_FACILITY_ID);
        }
        if (!facilityRepository.existsById(parentFacilityId)) {
            throw new RestApiException(FacilityErrorCode.INVALID_PARENT_FACILITY_ID);
        }
    }

    private void validateDuplicateFacilityNm(String facilityNm) {
        if (facilityRepository.existsByFacilityNm(facilityNm)) {
            throw new RestApiException(FacilityErrorCode.DUPLICATE_FACILITY_NM);
        }
    }

    private void validateDuplicateFacilityNmOnUpdate(String facilityNm, String facilityId) {
        if (facilityRepository.existsByFacilityNmAndFacilityIdNot(facilityNm, facilityId)) {
            throw new RestApiException(FacilityErrorCode.DUPLICATE_FACILITY_NM);
        }
    }

    private void validateTypeMatch(Facility facility, FacilityUpsertDto dto) {
        if (facility.getFacilityType() != dto.getFacilityTypeCd()) {
            throw new RestApiException(FacilityErrorCode.FACILITY_TYPE_MISMATCH);
        }
    }
}

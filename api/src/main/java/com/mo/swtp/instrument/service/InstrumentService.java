package com.mo.swtp.instrument.service;

import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.FlowMeter;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.LevelMeter;
import com.mo.swtp.instrument.domain.PowerMeter;
import com.mo.swtp.instrument.domain.PressureMeter;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.Valve;
import com.mo.swtp.instrument.dto.FlowMeterUpsertDto;
import com.mo.swtp.instrument.dto.InstrumentDto;
import com.mo.swtp.instrument.dto.InstrumentSearchDto;
import com.mo.swtp.instrument.dto.InstrumentUpsertDto;
import com.mo.swtp.instrument.dto.LevelMeterUpsertDto;
import com.mo.swtp.instrument.dto.PowerMeterUpsertDto;
import com.mo.swtp.instrument.dto.PressureMeterUpsertDto;
import com.mo.swtp.instrument.dto.PumpUpsertDto;
import com.mo.swtp.instrument.dto.ValveUpsertDto;
import com.mo.swtp.instrument.exception.InstrumentErrorCode;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계측기 CRUD 서비스 — JPA JOINED 다형성 + Jackson 다형성 DTO 디스패처.
 *
 * <p>{@link InstrumentUpsertDto} 추상 부모를 Jackson 이 {@code equipTypeCd} 필드로 자식 6종
 * ({@link PumpUpsertDto}·{@link ValveUpsertDto}·{@link FlowMeterUpsertDto}·{@link PressureMeterUpsertDto}·
 * {@link LevelMeterUpsertDto}·{@link PowerMeterUpsertDto}) 으로 자동 역직렬화한다. 본 서비스의
 * {@link #saveInstrument} 는 Java 21 switch 패턴 매칭으로 자식 타입별 정적 팩토리 호출로 디스패치한다.</p>
 *
 * <p>도메인 룰: 자식 종류별 정적 팩토리 ({@code Pump.create} 등) 를 통해서만 영속한다 — 부모
 * {@link Instrument} 가 abstract 이고 자식 종류별로 자식 테이블 INSERT 가 별도 발생하기 때문.</p>
 *
 * <p>업데이트는 자식 5종 (PUMP 외) 이 자체 컬럼 0건이므로 부모 {@link Instrument#changeInfo} 단일 호출로
 * 통합되며, PUMP 만 추가로 {@link Pump#changePumpSelfColumns} 호출로 자체 컬럼 변경을 처리한다 — DRY 정합
 * (자식 5종에 동일한 update 메서드 5건 분기 회피).</p>
 *
 * <p>계측기관리CRUD ANALYZE1·PLAN1 (2026-05-12) 도입. {@code FacilityService} 패턴 재현.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;
    private final FacilityRepository facilityRepository;

    /**
     * 계측기 목록을 조회한다. 모든 필터 NULL 허용 (NULL 시 전체).
     *
     * <p>정렬: {@code use_yn} DESC (활성 우선) → {@code disp_ord} ASC → {@code instrument_nm} ASC.</p>
     */
    public List<InstrumentDto> findAllInstruments(InstrumentSearchDto searchDto) {
        return instrumentRepository.findInstruments(searchDto).stream()
                .map(InstrumentDto::from)
                .toList();
    }

    /**
     * 단건 계측기를 조회한다.
     *
     * @throws RestApiException INSTRUMENT_NOT_FOUND — 존재하지 않는 instrumentId
     */
    public InstrumentDto findInstrumentDto(String instrumentId) {
        return InstrumentDto.from(findInstrumentOrThrow(instrumentId));
    }

    /**
     * 신규 계측기를 등록한다. 자식 종류는 {@link InstrumentUpsertDto#getEquipTypeCd()} 로 결정한다.
     *
     * @return 생성된 계측기의 instrumentId
     * @throws RestApiException INVALID_FACILITY_ID·DUPLICATE_INSTRUMENT_NM
     */
    @Transactional
    public String saveInstrument(InstrumentUpsertDto dto) {
        Facility facility = validateAndLoadFacility(dto.getFacilityId());
        validateDuplicateInstrumentNm(facility.getFacilityId(), dto.getInstrumentNm());
        Instrument instrument = switch (dto) {
            case PumpUpsertDto p -> Pump.create(
                    dto.getInstrumentNm(), facility, dto.getDispOrd(),
                    p.getRatedHead(), p.getRatedFlwrt(), p.getOprtngType(), p.getDriveType());
            case ValveUpsertDto v -> Valve.create(dto.getInstrumentNm(), facility, dto.getDispOrd());
            case FlowMeterUpsertDto f -> FlowMeter.create(dto.getInstrumentNm(), facility, dto.getDispOrd());
            case PressureMeterUpsertDto pr -> PressureMeter.create(dto.getInstrumentNm(), facility, dto.getDispOrd());
            case LevelMeterUpsertDto l -> LevelMeter.create(dto.getInstrumentNm(), facility, dto.getDispOrd());
            case PowerMeterUpsertDto pw -> PowerMeter.create(dto.getInstrumentNm(), facility, dto.getDispOrd());
            default -> throw new RestApiException(InstrumentErrorCode.EQUIP_TYPE_MISMATCH);
        };
        return instrumentRepository.save(instrument).getInstrumentId();
    }

    /**
     * 계측기를 수정한다. JPA dirty checking 으로 UPDATE 가 발행된다.
     *
     * @throws RestApiException INSTRUMENT_NOT_FOUND·EQUIP_TYPE_MISMATCH·INVALID_FACILITY_ID·DUPLICATE_INSTRUMENT_NM
     */
    @Transactional
    public void updateInstrument(String instrumentId, InstrumentUpsertDto dto) {
        Instrument instrument = findInstrumentOrThrow(instrumentId);
        validateEquipTypeMatch(instrument, dto);
        Facility facility = validateAndLoadFacility(dto.getFacilityId());
        validateDuplicateInstrumentNmOnUpdate(facility.getFacilityId(), dto.getInstrumentNm(), instrumentId);
        instrument.changeInfo(dto.getInstrumentNm(), facility, dto.getDispOrd());
        if (dto instanceof PumpUpsertDto p) {
            ((Pump) instrument).changePumpSelfColumns(p.getRatedHead(), p.getRatedFlwrt(), p.getOprtngType(), p.getDriveType());
        }
    }

    /**
     * 계측기를 논리 삭제한다 ({@code use_yn = N}).
     *
     * <p>물리 삭제하지 않는 이유: {@code tag_m.instrument_id} FK + 향후 {@code rawdata_1m_h.tag_srl_no}
     * 논리 참조 + AI 운전 모드 참조 보존 정책 정합 (PLAN1 §핵심 설계 결정 5).</p>
     *
     * @throws RestApiException INSTRUMENT_NOT_FOUND
     */
    @Transactional
    public void deactivateInstrument(String instrumentId) {
        Instrument instrument = findInstrumentOrThrow(instrumentId);
        instrument.deactivate();
    }

    private Instrument findInstrumentOrThrow(String instrumentId) {
        return instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new RestApiException(InstrumentErrorCode.INSTRUMENT_NOT_FOUND));
    }

    /**
     * 시설 FK 사전 검증 — DB FK 위반 (500) 회피하고 명확한 400 에러 반환.
     * facility 객체를 반환하여 호출처에서 재조회 회피.
     */
    private Facility validateAndLoadFacility(String facilityId) {
        return facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RestApiException(InstrumentErrorCode.INVALID_FACILITY_ID));
    }

    private void validateDuplicateInstrumentNm(String facilityId, String instrumentNm) {
        if (instrumentRepository.existsByFacilityFacilityIdAndInstrumentNm(facilityId, instrumentNm)) {
            throw new RestApiException(InstrumentErrorCode.DUPLICATE_INSTRUMENT_NM);
        }
    }

    private void validateDuplicateInstrumentNmOnUpdate(String facilityId, String instrumentNm, String instrumentId) {
        if (instrumentRepository.existsByFacilityFacilityIdAndInstrumentNmAndInstrumentIdNot(
                facilityId, instrumentNm, instrumentId)) {
            throw new RestApiException(InstrumentErrorCode.DUPLICATE_INSTRUMENT_NM);
        }
    }

    private void validateEquipTypeMatch(Instrument instrument, InstrumentUpsertDto dto) {
        if (instrument.getEquipType() != dto.getEquipTypeCd()) {
            throw new RestApiException(InstrumentErrorCode.EQUIP_TYPE_MISMATCH);
        }
    }
}

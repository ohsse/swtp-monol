package com.mo.swtp.instrument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.PurifiedWaterTank;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.PumpDriveType;
import com.mo.swtp.instrument.domain.PumpOprtngType;
import com.mo.swtp.instrument.domain.Valve;
import com.mo.swtp.instrument.dto.InstrumentUpsertDto;
import com.mo.swtp.instrument.dto.PumpUpsertDto;
import com.mo.swtp.instrument.dto.ValveUpsertDto;
import com.mo.swtp.instrument.exception.InstrumentErrorCode;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link InstrumentService} 단위 테스트 — Mockito 격리.
 *
 * <p>계측기관리CRUD PLAN1 §성공 기준 (검증 가능 형태) 의 7건 시나리오를 검증한다.</p>
 *
 * <ul>
 *   <li>① PUMP 등록 성공 → 저장 결과 instanceof Pump + 자체 컬럼 3건 값 일치 (tagNm 폐기)</li>
 *   <li>② VALVE 등록 성공 → 저장 결과 instanceof Valve</li>
 *   <li>③ 동일 시설·동일 이름 등록 시 RestApiException(DUPLICATE_INSTRUMENT_NM)</li>
 *   <li>④ 존재하지 않는 facilityId 등록 시 RestApiException(INVALID_FACILITY_ID) — DB FK 위반 500 회피</li>
 *   <li>⑤ PUMP 엔티티 PUT 에 equipTypeCd=VALVE 전송 시 RestApiException(EQUIP_TYPE_MISMATCH)</li>
 *   <li>⑥ 논리 삭제 → useYn = N (deactivate 후 엔티티 상태 검증)</li>
 *   <li>⑦ 다른 시설에 동일 이름 등록 허용 — (facility_id, instrument_nm) 복합 UNIQUE 검증</li>
 * </ul>
 *
 * <p>FacilityServiceTest 동일 패턴.</p>
 */
@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    @Mock private InstrumentRepository instrumentRepository;
    @Mock private FacilityRepository facilityRepository;
    @InjectMocks private InstrumentService instrumentService;

    private static final String FACILITY_ID_1 = "facility-uuid-001";
    private static final String FACILITY_ID_2 = "facility-uuid-002";
    private static final String INSTRUMENT_ID = "instrument-uuid-001";

    // ========== 시나리오 ①: PUMP 등록 성공 ==========

    @Test
    void PUMP_등록_성공_자체_컬럼_4건_값_일치() {
        PumpUpsertDto dto = buildPumpDto(
                FACILITY_ID_1, "송수펌프-001", 1,
                new BigDecimal("65.0"), new BigDecimal("250.0"),
                PumpOprtngType.AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE);
        Facility facility = buildFacility(FACILITY_ID_1, "정수지1");
        given(facilityRepository.findById(FACILITY_ID_1)).willReturn(Optional.of(facility));
        given(instrumentRepository.existsByFacilityFacilityIdAndInstrumentNm(FACILITY_ID_1, "송수펌프-001"))
                .willReturn(false);
        given(instrumentRepository.save(any(Instrument.class))).willAnswer(inv -> inv.getArgument(0));

        instrumentService.saveInstrument(dto);

        org.mockito.ArgumentCaptor<Instrument> captor =
                org.mockito.ArgumentCaptor.forClass(Instrument.class);
        then(instrumentRepository).should().save(captor.capture());
        Instrument saved = captor.getValue();
        assertThat(saved).isInstanceOf(Pump.class);
        Pump pump = (Pump) saved;
        assertThat(pump.getRatedHead()).isEqualByComparingTo("65.0");
        assertThat(pump.getRatedFlwrt()).isEqualByComparingTo("250.0");
        assertThat(pump.getOprtngType()).isEqualTo(PumpOprtngType.AUTO_CAPABLE);
        assertThat(pump.getDriveType()).isEqualTo(PumpDriveType.INVERTER_DRIVE);
    }

    // ========== 시나리오 ②: VALVE 등록 성공 ==========

    @Test
    void VALVE_등록_성공() {
        ValveUpsertDto dto = buildValveDto(FACILITY_ID_1, "밸브-001", 1);
        Facility facility = buildFacility(FACILITY_ID_1, "정수지1");
        given(facilityRepository.findById(FACILITY_ID_1)).willReturn(Optional.of(facility));
        given(instrumentRepository.existsByFacilityFacilityIdAndInstrumentNm(FACILITY_ID_1, "밸브-001"))
                .willReturn(false);
        given(instrumentRepository.save(any(Instrument.class))).willAnswer(inv -> inv.getArgument(0));

        instrumentService.saveInstrument(dto);

        then(instrumentRepository).should().save(any(Valve.class));
    }

    // ========== 시나리오 ③: 동일 시설·동일 이름 등록 시 DUPLICATE_INSTRUMENT_NM ==========

    @Test
    void 동일_시설_동일_이름_등록_시_DUPLICATE_INSTRUMENT_NM() {
        PumpUpsertDto dto = buildPumpDto(
                FACILITY_ID_1, "송수펌프-001", 1,
                new BigDecimal("65.0"), new BigDecimal("250.0"),
                PumpOprtngType.AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE);
        Facility facility = buildFacility(FACILITY_ID_1, "정수지1");
        given(facilityRepository.findById(FACILITY_ID_1)).willReturn(Optional.of(facility));
        given(instrumentRepository.existsByFacilityFacilityIdAndInstrumentNm(FACILITY_ID_1, "송수펌프-001"))
                .willReturn(true);

        assertThatThrownBy(() -> instrumentService.saveInstrument(dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InstrumentErrorCode.DUPLICATE_INSTRUMENT_NM);
        then(instrumentRepository).should(never()).save(any(Instrument.class));
    }

    // ========== 시나리오 ④: 존재하지 않는 facilityId 등록 시 INVALID_FACILITY_ID ==========

    @Test
    void 존재하지_않는_facilityId_등록_시_INVALID_FACILITY_ID() {
        PumpUpsertDto dto = buildPumpDto(
                "non-existent-facility", "송수펌프-001", 1,
                new BigDecimal("65.0"), new BigDecimal("250.0"),
                PumpOprtngType.AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE);
        given(facilityRepository.findById("non-existent-facility")).willReturn(Optional.empty());

        assertThatThrownBy(() -> instrumentService.saveInstrument(dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InstrumentErrorCode.INVALID_FACILITY_ID);
        then(instrumentRepository).should(never()).save(any(Instrument.class));
    }

    // ========== 시나리오 ⑤: PUMP 엔티티 PUT 에 equipTypeCd=VALVE → EQUIP_TYPE_MISMATCH ==========

    @Test
    void PUMP_엔티티_PUT_VALVE_타입_전송_시_EQUIP_TYPE_MISMATCH() {
        Facility facility = buildFacility(FACILITY_ID_1, "정수지1");
        Pump existingPump = Pump.create(
                "송수펌프-001", facility, 1,
                new BigDecimal("65.0"), new BigDecimal("250.0"),
                PumpOprtngType.AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE);
        given(instrumentRepository.findById(INSTRUMENT_ID)).willReturn(Optional.of(existingPump));

        ValveUpsertDto wrongTypeDto = buildValveDto(FACILITY_ID_1, "송수펌프-001", 1);

        assertThatThrownBy(() -> instrumentService.updateInstrument(INSTRUMENT_ID, wrongTypeDto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InstrumentErrorCode.EQUIP_TYPE_MISMATCH);
    }

    // ========== 시나리오 ⑥: 논리 삭제 → useYn = N ==========

    @Test
    void 논리_삭제_시_useYn_N_전환() {
        Facility facility = buildFacility(FACILITY_ID_1, "정수지1");
        Pump pump = Pump.create(
                "송수펌프-001", facility, 1,
                new BigDecimal("65.0"), new BigDecimal("250.0"),
                PumpOprtngType.AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE);
        given(instrumentRepository.findById(INSTRUMENT_ID)).willReturn(Optional.of(pump));

        instrumentService.deactivateInstrument(INSTRUMENT_ID);

        assertThat(pump.getUseYn()).isEqualTo(YnType.N);
    }

    // ========== 시나리오 ⑦: 다른 시설에 동일 이름 등록 허용 ==========

    @Test
    void 다른_시설에_동일_이름_등록_허용() {
        // 시설 1 에는 이미 "송수펌프-001" 존재하지만, 시설 2 에서 같은 이름 등록 시도
        PumpUpsertDto dto = buildPumpDto(
                FACILITY_ID_2, "송수펌프-001", 1,
                new BigDecimal("65.0"), new BigDecimal("250.0"),
                PumpOprtngType.AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE);
        Facility facility2 = buildFacility(FACILITY_ID_2, "정수지2");
        given(facilityRepository.findById(FACILITY_ID_2)).willReturn(Optional.of(facility2));
        // 시설 2 + "송수펌프-001" 조합은 미존재
        given(instrumentRepository.existsByFacilityFacilityIdAndInstrumentNm(FACILITY_ID_2, "송수펌프-001"))
                .willReturn(false);
        given(instrumentRepository.save(any(Instrument.class))).willAnswer(inv -> inv.getArgument(0));

        instrumentService.saveInstrument(dto);

        // 시설 1 의 동일 이름은 검사하지 않음 — 시설 2 단위로만 검사
        then(instrumentRepository).should()
                .existsByFacilityFacilityIdAndInstrumentNm(FACILITY_ID_2, "송수펌프-001");
        then(instrumentRepository).should().save(any(Pump.class));
    }

    // ========== 픽스처 ==========

    private PumpUpsertDto buildPumpDto(
            String facilityId, String instrumentNm, Integer dispOrd,
            BigDecimal ratedHead, BigDecimal ratedFlwrt,
            PumpOprtngType oprtngType, PumpDriveType driveType) {
        PumpUpsertDto dto = new PumpUpsertDto();
        dto.setEquipTypeCd(com.mo.swtp.instrument.domain.enumtype.EquipType.PUMP);
        dto.setFacilityId(facilityId);
        dto.setInstrumentNm(instrumentNm);
        dto.setDispOrd(dispOrd);
        dto.setRatedHead(ratedHead);
        dto.setRatedFlwrt(ratedFlwrt);
        dto.setOprtngType(oprtngType);
        dto.setDriveType(driveType);
        return dto;
    }

    private ValveUpsertDto buildValveDto(String facilityId, String instrumentNm, Integer dispOrd) {
        ValveUpsertDto dto = new ValveUpsertDto();
        dto.setEquipTypeCd(com.mo.swtp.instrument.domain.enumtype.EquipType.VALVE);
        dto.setFacilityId(facilityId);
        dto.setInstrumentNm(instrumentNm);
        dto.setDispOrd(dispOrd);
        return dto;
    }

    /**
     * Facility 픽스처 — PurifiedWaterTank 정적 팩토리 사용 + reflection 으로 facilityId 주입.
     * Facility 는 UUID 자동 생성이므로 테스트에서는 reflection 으로 ID 주입 (FacilityServiceTest 동일 패턴).
     */
    private Facility buildFacility(String facilityId, String facilityNm) {
        PurifiedWaterTank facility = PurifiedWaterTank.create(facilityNm, null, 1, YnType.Y);
        injectFacilityId(facility, facilityId);
        return facility;
    }

    private void injectFacilityId(Facility facility, String facilityId) {
        try {
            Field idField = Facility.class.getDeclaredField("facilityId");
            idField.setAccessible(true);
            idField.set(facility, facilityId);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject facilityId for test", e);
        }
    }
}

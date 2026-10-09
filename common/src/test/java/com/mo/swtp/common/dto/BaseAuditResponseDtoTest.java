package com.mo.swtp.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mo.swtp.common.domain.BaseEntity;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link BaseAuditResponseDto} 의 매핑 헬퍼와 직렬화 정책 검증.
 *
 * <p>두 가지를 검증한다:</p>
 * <ol>
 *     <li>{@code applyAuditMeta(BaseEntity)} 가 {@link BaseEntity} 의 4컬럼을 자식 DTO 에 모두 매핑하는지</li>
 *     <li>{@code rgstrDtm}·{@code updtDtm} 가 {@code @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")} 으로
 *         초 단위 문자열로 직렬화되는지</li>
 * </ol>
 *
 * <p>Spring Context 를 띄우지 않고 {@link ObjectMapper} 를 직접 구성한다
 * — {@link JavaTimeModule} 등록 + {@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} disable
 * (Spring Boot 기본 {@code JacksonAutoConfiguration} 과 동등 구성).</p>
 */
class BaseAuditResponseDtoTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void applyAuditMeta_가_BaseEntity_의_4컬럼을_모두_매핑한다() throws Exception {
        // given — BaseEntity 자식 fake + 4컬럼 리플렉션 주입 (AuditingEntityListener 미사용 단위 테스트)
        LocalDateTime rgstrDtm = LocalDateTime.of(2026, 5, 11, 10, 30, 15);
        LocalDateTime updtDtm = LocalDateTime.of(2026, 5, 11, 12, 0, 0);
        FakeAuditableEntity entity = FakeAuditableEntity.create(rgstrDtm, updtDtm, "alice", "bob");

        // when
        TestDto dto = TestDto.from(entity);

        // then
        assertThat(dto.getRgstrDtm()).isEqualTo(rgstrDtm);
        assertThat(dto.getUpdtDtm()).isEqualTo(updtDtm);
        assertThat(dto.getRgstrId()).isEqualTo("alice");
        assertThat(dto.getUpdtId()).isEqualTo("bob");
    }

    @Test
    void rgstrDtm_과_updtDtm_은_yyyy_MM_dd_HH_mm_ss_초_단위로_직렬화된다() throws Exception {
        // given
        LocalDateTime rgstrDtm = LocalDateTime.of(2026, 5, 11, 10, 30, 15);
        LocalDateTime updtDtm = LocalDateTime.of(2026, 5, 11, 12, 0, 0);
        FakeAuditableEntity entity = FakeAuditableEntity.create(rgstrDtm, updtDtm, "system", "system");
        TestDto dto = TestDto.from(entity);

        // when
        String json = objectMapper.writeValueAsString(dto);

        // then — 초 단위까지 문자열 직렬화
        assertThat(json).contains("\"rgstrDtm\":\"2026-05-11 10:30:15\"");
        assertThat(json).contains("\"updtDtm\":\"2026-05-11 12:00:00\"");
    }

    @Test
    void rgstrId_와_updtId_는_평문_문자열로_직렬화된다() throws Exception {
        // given
        FakeAuditableEntity entity = FakeAuditableEntity.create(
                LocalDateTime.of(2026, 5, 11, 10, 30, 15),
                LocalDateTime.of(2026, 5, 11, 12, 0, 0),
                "alice",
                "bob");
        TestDto dto = TestDto.from(entity);

        // when
        String json = objectMapper.writeValueAsString(dto);

        // then
        assertThat(json).contains("\"rgstrId\":\"alice\"");
        assertThat(json).contains("\"updtId\":\"bob\"");
    }

    /** 본 테스트 전용 자식 DTO — {@link BaseAuditResponseDto} 의 매핑·직렬화를 검증하기 위한 최소 골격 */
    static class TestDto extends BaseAuditResponseDto {

        private TestDto() {}

        static TestDto from(BaseEntity entity) {
            TestDto dto = new TestDto();
            dto.applyAuditMeta(entity);
            return dto;
        }
    }

    /**
     * {@link BaseEntity} 자식 fake — AuditingEntityListener 미동작 환경에서
     * 4컬럼을 리플렉션으로 직접 주입한다.
     */
    static class FakeAuditableEntity extends BaseEntity {

        private FakeAuditableEntity() {}

        static FakeAuditableEntity create(LocalDateTime rgstrDtm, LocalDateTime updtDtm,
                                          String rgstrId, String updtId) {
            FakeAuditableEntity entity = new FakeAuditableEntity();
            setField(entity, "rgstrDtm", rgstrDtm);
            setField(entity, "updtDtm", updtDtm);
            setField(entity, "rgstrId", rgstrId);
            setField(entity, "updtId", updtId);
            return entity;
        }

        private static void setField(Object target, String fieldName, Object value) {
            try {
                Field field = BaseEntity.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("테스트 픽스처 주입 실패: " + fieldName, e);
            }
        }
    }
}

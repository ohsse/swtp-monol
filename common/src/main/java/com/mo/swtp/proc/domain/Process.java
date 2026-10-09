package com.mo.swtp.proc.domain;

import com.mo.swtp.common.domain.BaseEntity;
import com.mo.swtp.common.enumtype.YnType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * 공정/제어대상 마스터 엔티티.
 *
 * <p>업무 화면 카테고리 단위 마스터 (예: 송수펌프제어·정수공정·약품투입). AI 운전모드 설정의
 * 적용 단위이며, 시스템 전역 설정으로 같은 화면의 모든 사용자가 동일 모드 상태를 공유한다.</p>
 *
 * <p>PK 는 외부 할당 ({@code @GeneratedValue} 미사용) — 사업장 간 공통 코드값 정렬 목적이다
 * (예: 'PUMP_CONTROL'·'WTR_TREAT'). {@link Persistable} 을 구현하며 {@code isNew()} 판정은
 * {@link BaseEntity} 의 {@code newEntity} 플래그에 위임한다.</p>
 *
 * <p>{@code proc_id} 형식은 정규식 {@code ^[A-Z][A-Z0-9_]*$} 로 애플리케이션 레벨 검증한다
 * — DDL CHECK 미적용 (Java 정적 팩토리가 SSOT, use_yn_consistency 선례).</p>
 *
 * <p>{@code proc_nm} 은 시스템 전체 UNIQUE — facility_nm·menu_nm 선례 동일 패턴.</p>
 *
 * <p>도입: 송수펌프제어분석-2번섹션 ANALYZE2 + PLAN2 (2026-05-20).</p>
 */
@Entity
@Table(
        name = "proc_m",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_proc_m_proc_nm",
                columnNames = "proc_nm"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Process extends BaseEntity implements Persistable<String> {

    /** proc_id 코드값 형식 정규식 — 대문자 시작 + 대문자·숫자·언더스코어만 허용 */
    public static final Pattern PROC_ID_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    @Id
    @Column(name = "proc_id", nullable = false, length = 50)
    private String procId;

    /** 공정/제어대상명 (시스템 전체 UNIQUE) */
    @Column(name = "proc_nm", nullable = false, length = 100)
    private String procNm;

    /** 표시 순서 — 화면 메뉴 정렬 */
    @Column(name = "disp_ord", nullable = false)
    private Integer dispOrd;

    /** 사용 여부 ({@link YnType#Y}: 활성, {@link YnType#N}: 비활성) */
    @Enumerated(EnumType.STRING)
    @Column(name = "use_yn", nullable = false, length = 1)
    private YnType useYn;

    /**
     * {@inheritDoc} — PK 를 반환한다.
     */
    @Override
    public String getId() {
        return procId;
    }

    /**
     * 신규 공정/제어대상을 생성한다. 등록자·수정자는 JPA Auditing 이 자동 주입한다.
     *
     * @param procId  공정/제어대상 ID — 정규식 {@code ^[A-Z][A-Z0-9_]*$} 검증
     * @param procNm  공정/제어대상명
     * @param dispOrd 표시 순서
     * @return 신규 Process 엔티티
     * @throws IllegalArgumentException procId 형식 위반 시
     */
    public static Process create(String procId, String procNm, Integer dispOrd) {
        validateProcId(procId);
        return new Process(procId, procNm, dispOrd, YnType.Y);
    }

    /**
     * 공정/제어대상 정보를 변경한다. null 필드는 유지한다.
     *
     * @param procNm  변경할 명칭 (null 이면 유지)
     * @param dispOrd 변경할 표시 순서 (null 이면 유지)
     */
    public void changeInfo(String procNm, Integer dispOrd) {
        if (procNm != null) {
            this.procNm = procNm;
        }
        if (dispOrd != null) {
            this.dispOrd = dispOrd;
        }
    }

    /**
     * 공정/제어대상을 비활성화 (논리 삭제) 한다.
     */
    public void deactivate() {
        this.useYn = YnType.N;
    }

    /**
     * proc_id 형식 검증 — 대문자 시작 + 대문자·숫자·언더스코어만 허용.
     *
     * @param procId 검증 대상
     * @throws IllegalArgumentException 형식 위반 시
     */
    private static void validateProcId(String procId) {
        if (procId == null || !PROC_ID_PATTERN.matcher(procId).matches()) {
            throw new IllegalArgumentException("Invalid procId format: " + procId);
        }
    }
}

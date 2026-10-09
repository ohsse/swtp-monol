package com.mo.swtp.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 공통 감사(Audit) 필드를 제공하는 엔티티 베이스 클래스.
 *
 * <p>모든 엔티티는 이 클래스 또는 {@link DomainEventEntity}를 상속한다.
 * 도메인 이벤트가 필요한 엔티티는 {@link DomainEventEntity}를, 그렇지 않은 경우 이 클래스를 상속한다.</p>
 *
 * <p>등록·수정 일시와 등록자·수정자는 {@link AuditingEntityListener} 가 자동으로 주입한다.
 * 각 실행 모듈(api/scheduler)은 {@code @EnableJpaAuditing} 과 {@code AuditorAware<String>} Bean 을 제공해야 한다.</p>
 *
 * <p>외부에서 PK를 할당받는 엔티티({@code @GeneratedValue} 미사용)는
 * {@code implements Persistable<ID>}를 선언하고 {@code getId()}만 구현한다.
 * {@code isNew()} 판정은 이 클래스의 {@code newEntity} 플래그가 담당하므로
 * {@link org.springframework.data.repository.CrudRepository#save save()} 호출 시
 * 신규 엔티티는 항상 {@code em.persist()} 경로를 따른다.</p>
 */
@MappedSuperclass
@Getter
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    /** 엔티티 최초 생성 일시 */
    @CreatedDate
    @Column(name = "rgstr_dtm", nullable = false, updatable = false)
    private LocalDateTime rgstrDtm;

    /** 엔티티 최종 수정 일시 */
    @LastModifiedDate
    @Column(name = "updt_dtm", nullable = false)
    private LocalDateTime updtDtm;

    /** 엔티티 최초 생성자 ID */
    @CreatedBy
    @Column(name = "rgstr_id", nullable = false, updatable = false, length = 50)
    private String rgstrId;

    /** 엔티티 최종 수정자 ID */
    @LastModifiedBy
    @Column(name = "updt_id", nullable = false, length = 50)
    private String updtId;

    /**
     * {@link Persistable#isNew()} 판정용 신규 여부 플래그.
     * JPA 리플렉션 생성·기본 생성자 호출 시 true, persist 또는 로드 후 false로 전환된다.
     */
    @Transient
    private boolean newEntity = true;

    /**
     * {@link Persistable}을 구현하는 하위 엔티티가 위임하는 신규 여부 판정 메서드.
     */
    public boolean isNew() {
        return newEntity;
    }

    /**
     * 최초 저장 시 신규 플래그를 해제한다. 날짜·auditor는 {@link AuditingEntityListener}가 담당한다.
     */
    @PrePersist
    protected void onPrePersistMarkLoaded() {
        this.newEntity = false;
    }

    /**
     * DB에서 로드된 엔티티의 신규 플래그를 해제한다.
     */
    @PostLoad
    protected void onPostLoadMarkLoaded() {
        this.newEntity = false;
    }
}

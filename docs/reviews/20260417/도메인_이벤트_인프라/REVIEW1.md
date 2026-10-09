---
status: approved
created: 2026-04-17
updated: 2026-04-17
---
# 도메인 이벤트 인프라 구축 리뷰

## 관련 결과
- [결과](../../../results/20260417/도메인_이벤트_인프라/RESULT1.md)

## 리뷰 범위
- `common/build.gradle`
- `common/.../domain/BaseEntity.java`
- `common/.../event/DomainEventHolder.java`
- `common/.../domain/DomainEventEntity.java`
- `common/.../event/AbstractDomainEventPublisher.java`
- `common/.../event/DomainEventEntityTest.java`
- `common/.../event/AbstractDomainEventPublisherTest.java`

## 발견 사항

### 높음 (블로커) — 해소됨
| # | 항목 | 조치 |
|---|------|------|
| 1 | `saveAndPublish` Javadoc: null eventFunction 케이스에서도 기존 등록 이벤트가 발행됨을 미기술 | Javadoc 수정 + 경계 케이스 테스트 추가 |
| 2 | `deleteAndPublish` Javadoc: "BEFORE_COMMIT에서 연관 데이터 정리 후 삭제 보장"은 JPA 동작과 다름 | Javadoc을 실제 JPA flush 순서 기반으로 재작성 |

### 중간 (수정 권고)
| # | 항목 | 조치 |
|---|------|------|
| 3 | `build.gradle`: spring-data-jpa/spring-context 버전이 BOM 분리 관리됨 | common에 Spring BOM이 없는 구조적 한계. api 실 사용 버전과 일치하도록 명시, 주석으로 관리 기준 기록 |
| 4 | `DomainEventEntity`: 추상 클래스에 `@NoArgsConstructor(access = PROTECTED)` 미적용 | abstract 클래스이므로 컴파일러가 protected 생성자 자동 생성. 런타임 문제 없음. 명시적 선언은 선택 사항으로 미적용 |
| 5 | `deleteAndPublish` 실행 순서(InOrder) 테스트 누락 | `InOrder` 기반 순서 검증 테스트 추가 |
| 6 | null eventFunction + 기존 등록 이벤트 병존 케이스 미검증 | 경계 케이스 테스트 추가 |

### 낮음 (선택적 개선)
| # | 항목 | 조치 |
|---|------|------|
| 7 | `DomainEventEntity` Javadoc 예시에 `registerEvent` 호출 코드 누락 | 현재 주석으로 충분히 설명됨, 미수정 |
| 8 | `mockito-junit-jupiter` 버전이 루트 `mockito-core`와 분리 관리됨 | 버전 동일(5.20.0). 향후 버전 업 시 함께 수정 필요 |

## 개선 제안
- `AbstractDomainEventPublisher`를 사용하는 구체 Publisher는 `eventFunction = null`로 `saveAndPublish`를 호출하는 상황을 피하도록 설계할 것 (기존 이벤트 발행 side effect 방지)
- Hibernate flush 순서에 의존하는 BEFORE_COMMIT 핸들러 작성 시 FK 제약 케이스에서 명시적 `EntityManager.flush()` 활용 권장

## 결론
블로커(높음) 2건 모두 Javadoc 수정 및 테스트 보완으로 해소됨.
기능 로직은 reference 프로젝트와 동일하며 정상 동작함.
`./gradlew.bat clean build` 21개 태스크 전체 통과.

**블로커 없음 — 커밋 진행 가능**

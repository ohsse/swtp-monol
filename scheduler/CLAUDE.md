# scheduler 모듈 작업 규칙

## 목적
`scheduler` 모듈은 스마트정수장 백엔드의 스케줄링 및 배치 오케스트레이션 모듈입니다.
Spring Batch를 이용한 배치 작업 정의와 실행 스케줄링을 담당합니다.
`common` 모듈에 의존하며, 웹 계층 역할은 하지 않습니다.

---

## 의존성
- `common` 모듈 의존
- Spring Batch, Spring Data JPA, MyBatis, PostgreSQL, Lombok

---

## 허용 범위
- `@Configuration` 기반 `Job`, `Step`, `Tasklet`, `ItemReader/Processor/Writer` 정의
- `@Scheduled`, `@EnableScheduling` 기반 스케줄 트리거
- `JpaRepository`, `CustomRepositoryImpl`, `MyBatis Mapper`
- 스케줄러 전용 `@ConfigurationProperties`, 배치 설정 클래스
- `common` 모듈의 도메인 엔티티 소비

---

## 금지 범위
- `@RestController`, `@RequestMapping`, 웹 계층 구현
- `@Entity`, `@MappedSuperclass`, `@Embeddable` 신규 정의 → `common` 모듈에 작성
- 공통 응답 포맷, 공통 예외의 중복 구현
- Querydsl APT 추가 또는 QClass 재생성

---

## 패키지 원칙
- 애플리케이션 진입점과 실행 설정: `com.mo.swtp.scheduler.*`
- 도메인별 배치: `com.mo.swtp.{도메인}` 구조, 하위 패키지 `job`, `step`, `mapper`, `config`
- MyBatis 매퍼: `@SchedulerMybatisMapper` 어노테이션으로 스캔 대상 명시

---

## 코드 컨벤션
- Lombok을 적극 활용한다. (getter/setter/생성자 직접 구현 지양)
- 의존성 주입은 `@RequiredArgsConstructor` 기반 생성자 주입을 사용한다.
- 모든 클래스와 메서드에 Javadoc을 작성한다.

---

## 설정 원칙
- 배치 실행 설정은 `application.yml`과 `@ConfigurationProperties`로 외부화한다.
- DB 접속 정보 같은 민감 설정은 환경 변수 주입을 우선한다.
- 스케줄 주기는 코드 하드코딩 대신 설정 파일로 관리한다.

---

## 테스트
```bash
# scheduler 모듈 테스트
./gradlew.bat :scheduler:test

# 공통 타입/엔티티 의존 변경이 포함된 경우 전체 테스트
./gradlew.bat test
```

# 지자체별 멀티테넌트 배포 구조

스마트정수장 백엔드는 지자체별로 별도 인스턴스를 배포한다.
소스 코드는 공통이며 환경별 차이는 Gradle 빌드 프로파일(`-Pprofile`)과 `resources-env/`로 분리한다.

---

## 참조 문서 관계

| 문서 | 이 문서와의 관계 |
|------|----------------|
| [`api/build.gradle`](../../api/build.gradle) | `ext.profile`, `resources-env` 구조의 원천 |
| [`scheduler/build.gradle`](../../scheduler/build.gradle) | scheduler 모듈 동일 패턴 |
| [`db/README.md`](db/README.md) (분리 인덱스) | 지자체별 DB 접속 정보는 resources-env 분리 대상 — 3개 자식 룰 모두 적용 |

---

## 1. 빌드 프로파일 명명 규칙

### 형식

```
{지자체코드}           # 예: gs, gm, hy
{지자체코드}{번호}     # 예: gm2, gr1, hy2  ← 동일 지자체 복수 정수장
```

- 소문자 영문 2~3자 + 선택적 숫자 한 자리
- 레거시 EMS 선례: `ba`, `gm2`, `gr`, `gr1`, `gs`, `gu`, `hp2`, `hy2`, `ji2`, `ss`, `wm`
- 레거시 PMS 선례: `gm`, `gm2`, `gs`, `hy`
- `common`은 예약어 (기본값, resources-env/common 디렉토리 없으면 프로파일 미지정 빌드)

### 빌드 명령

```bash
# 지자체별 실행 JAR 빌드
./gradlew.bat :api:bootJar -Pprofile=gs

# 로컬 개발 (프로파일 미지정 → 기본 resources/ 만 사용)
./gradlew.bat :api:bootRun

# 전체 빌드 검증 (공통 코드 빌드)
./gradlew.bat build
```

> **⚠️ 주의**: `-Pprofile` 미지정 시 `ext.profile = 'common'`으로 설정되고 `resources-env/common/`이 없으면 기본 `resources/` 파일만 사용된다. CI에서는 항상 `-Pprofile`을 명시한다.

---

## 2. resources-env 디렉토리 구조

```
{모듈}/src/main/
├── resources/                  ← 공통 기본 설정 (모든 지자체에 적용)
│   ├── application.yml
│   ├── logback-spring.xml
│   └── spy.properties
└── resources-env/
    └── {지자체코드}/           ← 지자체별 오버라이드 파일
        ├── application.yml     ← DB 커넥션, SCADA 엔드포인트, 지자체 전용 설정
        ├── logback-spring.xml  ← 로그 경로·수준 오버라이드 (필요 시만)
        └── spy.properties      ← p6spy executionThreshold 오버라이드 (필요 시만)
```

**오버라이드 동작**: Gradle `DuplicatesStrategy.INCLUDE`에 의해 `resources-env/{profile}/` 파일이 `resources/` 기본 파일보다 우선 적용된다.

**모듈별 독립**: `api/`와 `scheduler/` 각 모듈에 별도 `resources-env/` 디렉토리를 유지한다.
한 모듈의 설정 파일이 다른 모듈에 자동 전파되지 않으므로 두 모듈 모두 필요한 설정을 작성한다.

---

## 3. 공통 vs 지자체별 분리 기준

| 항목 | 분류 | 위치 |
|------|------|------|
| 비즈니스 로직 / 도메인 규칙 | **공통** | `src/main/java` |
| 엔티티 / 공통 응답·예외 | **공통** | `common` 모듈 |
| API 엔드포인트 구조 | **공통** | `api` 모듈 |
| Spring Boot 기본 설정 | **공통** | `resources/application.yml` |
| DB 접속 정보 (host·port·dbname·user·pw) | **지자체별** | `resources-env/{profile}/application.yml` |
| SCADA 연동 엔드포인트·토픽 | **지자체별** | `resources-env/{profile}/application.yml` |
| 지자체 전용 요금제 파라미터 | **지자체별** | `resources-env/{profile}/application.yml` |
| 로그 파일 경로·보존 정책 | **지자체별** | `resources-env/{profile}/logback-spring.xml` |
| p6spy 슬로우 쿼리 임계값 | **지자체별** | `resources-env/{profile}/spy.properties` |
| Swagger / SpringDoc 노출 정책 (`springdoc.api-docs.enabled` · `springdoc.swagger-ui.enabled`) | **지자체별** | `resources-env/{profile}/application.yml` |

---

## 4. 프로파일 분기 · Bean 분기 · 별도 모듈 판정

작업 범위에 따라 세 가지 전략 중 하나를 선택한다.

### 전략 1: 프로파일별 설정 파일 (기본)

설정값(커넥션·URL·파라미터)만 다른 경우.

```yaml
# resources-env/gs/application.yml
spring.datasource.url: jdbc:postgresql://gs-db:5432/wtp
```

### 전략 2: 프로파일별 Bean (`@Profile`)

초기화·동작 로직이 지자체별로 다르지만 인터페이스는 동일한 경우.

```java
@Profile("gs")
@Component
public class GsScadaAdapter implements ScadaAdapter { ... }

@Profile("!gs")  // 기본 구현
@Component
public class DefaultScadaAdapter implements ScadaAdapter { ... }
```

> `@Profile` 남용은 코드 파편화를 유발한다. 인터페이스로 추상화 후 전략 패턴 적용을 우선 검토한다.

### 전략 3: 별도 모듈

도메인 모델 자체가 지자체별로 전혀 다른 경우 (매우 드문 케이스). Gradle 멀티모듈에 `{지자체코드}-domain` 모듈을 추가한다. 이 결정은 아키텍처 변경에 해당하며 PLAN 문서 작성 및 팀 리뷰가 필요하다.

### 판정 기준 요약

| 조건 | 권장 전략 |
|------|----------|
| 설정값만 다름 | 전략 1: 프로파일별 설정 파일 |
| 구현 코드가 다르나 인터페이스 동일 | 전략 2: `@Profile` + Strategy Pattern |
| 도메인 모델 자체가 다름 | 전략 3: 별도 모듈 (PLAN 필요) |

---

## 5. 신규 지자체 추가 절차

1. **디렉토리 생성**: `api/src/main/resources-env/{지자체코드}/`와 `scheduler/src/main/resources-env/{지자체코드}/` 생성
2. **application.yml 작성**: `resources/application.yml`에서 DB·SCADA 섹션만 복사하여 실제 접속 정보 기입 (민감 정보는 환경 변수 주입, 하드코딩 금지)
3. **Swagger / SpringDoc 노출 정책 명시**: `springdoc.api-docs.enabled` 와 `springdoc.swagger-ui.enabled` 를 **반드시 명시** 한다. `application-common.yml` 에 default 가 없으므로 미명시 시 SpringDoc 라이브러리 기본값(활성)이 적용되어 운영 노출 위험. 운영 tenant 는 `false`, 개발 환경(dev/local) 은 `true` 가 표준
4. **logback-spring.xml** (필요 시): 로그 파일 경로 오버라이드
5. **빌드 검증**: `./gradlew.bat :api:bootJar -Pprofile={지자체코드}` 성공 확인

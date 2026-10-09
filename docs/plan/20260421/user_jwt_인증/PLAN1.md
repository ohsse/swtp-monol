---
status: approved
created: 2026-04-21
updated: 2026-04-21
---
# 사용자 로그인 · JWT 토큰 관리 설계

## 목적
스마트정수장 백엔드에 사용자 도메인(User 엔티티)과 JWT 기반 로그인·로그아웃·토큰 갱신 API를 추가한다.

## 배경
JWT 발급·검증·리프레시 토큰 회전까지의 인프라(`JwtTokenManagementService`, `RefreshToken`, `JwtAuthenticationFilter`)는
이미 구축되어 있으나, User 엔티티·로그인 API·비밀번호 해싱·권한 체크가 비어 있다.
아울러 기존 `BaseEntity`·`RefreshToken` 엔티티의 컬럼명(rgstr_dtm/updt_dtm 등)을 네이밍 규칙에 맞게 정렬한다.

## 범위

### 토큰 정책
- AT 유효기간: 1시간 (현재 30분 → 60분 변경)
- RT 유효기간: 7일 (유지)
- AT 갱신 시 RT 회전 (기존 `rotateRefreshToken()` 재사용)
- 1계정 1RT (기존 `issueTokens()`가 자동 처리)

### 사용자 모델
- 테이블: `user_m`
- 필드: `user_id`, `user_nm`, `user_pw`(BCrypt), `user_role`(ADMIN/USER), `use_yn`, `rgstr_id`, `updt_id`, `rgstr_dtm`, `updt_dtm`

### 권한
- ADMIN: 사용자 등록/수정/삭제, 추후 기기제어·모드제어
- USER: 조회 전용

### 엔티티 컬럼명 정렬
- `BaseEntity`: `created_at` → `rgstr_dtm`, `updated_at` → `updt_dtm`
- `RefreshToken`: `expires_at` → `expr_dtm`, `revoked_at` → `revoke_dtm`, `last_used_at` → `last_used_dtm`

## 구현 방향

### 재사용
- `JwtTokenManagementService` — 토큰 발급·회전·폐기 로직 그대로
- `JwtAuthenticationFilter` — request attribute 적재 구조 그대로
- `JwtErrorCode` — `EXPIRED_TOKEN`, `REFRESH_TOKEN_*` 코드 그대로

### 신규
- `user/domain/User.java`, `UserRole.java` (common)
- `UserService`, `UserController`, `UserRepository`, `UserErrorCode` (api)
- `AuthService`, `AuthController`, `AuthErrorCode`, `RoleGuard`, 각종 DTO (api)
- `PasswordEncoderConfig` (BCrypt Bean, api)
- `spring-security-crypto` 의존성 추가

## 테스트 전략
- `UserServiceTest`, `AuthServiceTest` 유닛 테스트
- `./gradlew.bat :api:test` 통과
- 로컬 E2E: 로그인→AT/RT 수신→리프레시→로그아웃 흐름 확인

## 제외 사항
- Spring Security 전체 도입
- 역할 세분화 (기기제어·모드제어 권한)
- 실운영 DB 마이그레이션 스크립트

## 예상 산출물
- [태스크](../../../tasks/20260421/user_jwt_인증/TASK1.md)

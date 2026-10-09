package com.mo.swtp.opt.exception;

import com.mo.swtp.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * opt 도메인 에러 코드 (전력피크 목표값 등).
 *
 * <p>opt 도메인 첫 write 경로 ErrorCode — 전력피크분석-1번섹션 ANALYZE1 + PLAN1 (2026-06-04).
 * {@code httpStatus}(int) 외 필드 금지 (exception-patterns.md §2 — 사용자 표기 문구는 frontend 가
 * {@code errorCode.name()} 으로 명세 매핑).</p>
 */
@Getter
@RequiredArgsConstructor
public enum OptErrorCode implements ErrorCode {

    /** 전력피크 목표값 시드 행 미초기화 — 배포 누락·픽스처 결손 (정상 경로에서 발생 불가) */
    PEAK_TARGET_NOT_INITIALIZED(500),

    /** 사용량 추이 조회 파라미터 무효 — 집계단위/시작일/종료일 null·기간 역전·YEAR 미지원·13개월 초과 (사용량트렌드-2번섹션 PLAN1) */
    INVALID_SEARCH_PERIOD(400);

    private final int httpStatus;
}

package com.mo.swtp.api.config.web;

import com.mo.swtp.common.response.CommonResponseDto;
import org.springframework.http.ResponseEntity;

public abstract class CommonController {

    protected <T> ResponseEntity<CommonResponseDto<T>> getResponseEntity(T data) {
        return ResponseEntity.ok(new CommonResponseDto<>("SUCCESS", data));
    }

    protected ResponseEntity<CommonResponseDto<Void>> getResponseEntity() {
        return ResponseEntity.ok(new CommonResponseDto<>("SUCCESS", null));
    }
}

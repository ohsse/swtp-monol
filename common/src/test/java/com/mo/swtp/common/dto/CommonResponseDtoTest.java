package com.mo.swtp.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import com.mo.swtp.common.response.CommonResponseDto;
import org.junit.jupiter.api.Test;

class CommonResponseDtoTest {

    @Test
    void stringDataCanBeStored() {
        CommonResponseDto<String> response = new CommonResponseDto<>("SUCCESS", "ok");

        assertEquals("SUCCESS", response.getCode());
        assertEquals("ok", response.getData());
    }

    @Test
    void collectionDataCanBeStored() {
        CommonResponseDto<List<Integer>> response = new CommonResponseDto<>("SUCCESS", List.of(1, 2, 3));

        assertEquals("SUCCESS", response.getCode());
        assertEquals(List.of(1, 2, 3), response.getData());
    }

    @Test
    void mapDataCanBeStored() {
        CommonResponseDto<Map<String, Object>> response =
                new CommonResponseDto<>("USER_NOT_FOUND", Map.of("userId", "user-1"));

        assertEquals("USER_NOT_FOUND", response.getCode());
        assertInstanceOf(Map.class, response.getData());
        assertEquals("user-1", response.getData().get("userId"));
    }

    @Test
    void nullDataCanBeStored() {
        CommonResponseDto<Void> response = new CommonResponseDto<>("SUCCESS", null);

        assertEquals("SUCCESS", response.getCode());
        assertNull(response.getData());
    }
}

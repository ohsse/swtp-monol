package com.mo.swtp.common.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JwtTokenHelperTest {

    private static final String SECRET = "local-dev-only-secret-change-me-0123456789";

    @Test
    void generatesTokenPairAndReadsClaims() {
        JwtTokenHelper helper = new JwtTokenHelper(SECRET, "swtp", 30, 7);

        JwtToken token = helper.generateTokenPair("user-1", Map.of("role", "ADMIN"));
        JwtTokenInspection inspection = helper.inspect(token.getAccessToken());

        assertEquals(JwtTokenStatus.VALID, inspection.status());
        assertEquals("user-1", inspection.subject());
        assertEquals("ADMIN", helper.getClaim(token.getAccessToken(), "role", String.class));
        assertEquals(
                JwtTokenHelper.ACCESS_TOKEN_TYPE,
                helper.getClaim(token.getAccessToken(), JwtTokenHelper.TOKEN_TYPE_CLAIM, String.class)
        );
        assertNotNull(inspection.issuedAt());
        assertNotNull(inspection.expiresAt());
    }

    @Test
    void returnsExpiredWhenTokenIsPastExpiration() {
        JwtTokenHelper helper = new JwtTokenHelper(SECRET, "swtp", -1, 7);

        String expiredToken = helper.generateAccessToken("user-1", Map.of("role", "ADMIN"));
        JwtTokenInspection inspection = helper.inspect(expiredToken);

        assertEquals(JwtTokenStatus.EXPIRED, inspection.status());
        assertEquals("user-1", inspection.subject());
        assertEquals("ADMIN", inspection.claims().get("role"));
    }

    @Test
    void returnsInvalidWhenSignatureIsBroken() {
        JwtTokenHelper helper = new JwtTokenHelper(SECRET, "swtp", 30, 7);

        String token = helper.generateAccessToken("user-1", Map.of());
        JwtTokenInspection inspection = helper.inspect(token + "broken");

        assertEquals(JwtTokenStatus.INVALID, inspection.status());
        assertNull(inspection.subject());
        assertTrue(inspection.claims().isEmpty());
    }
}

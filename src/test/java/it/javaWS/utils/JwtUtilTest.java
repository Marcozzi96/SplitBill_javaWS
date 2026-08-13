package it.javaWS.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import it.javaWS.models.entities.User;

class JwtUtilTest {

    // 64 byte casuali in Base64: chiave valida per HS512
    private static final String SECRET_BASE64 = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".getBytes());

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", SECRET_BASE64);
        ReflectionTestUtils.setField(jwtUtil, "jwtValidity", 3600L);
    }

    private User user() {
        User user = new User();
        user.setId(42L);
        user.setUsername("mario");
        return user;
    }

    @Test
    void generateToken_andExtractClaims_roundTrip() {
        String token = jwtUtil.generateToken(user());

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("mario");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
    }

    @Test
    void validateToken_withCorrectUser_returnsTrue() {
        String token = jwtUtil.generateToken(user());

        assertThat(jwtUtil.validateToken(token, user())).isTrue();
    }

    @Test
    void validateToken_withDifferentUser_returnsFalse() {
        String token = jwtUtil.generateToken(user());
        User other = new User();
        other.setId(43L);
        other.setUsername("luigi");

        assertThat(jwtUtil.validateToken(token, other)).isFalse();
    }

    @Test
    void extractAllClaims_withExpiredToken_throwsExpiredJwtException() {
        ReflectionTestUtils.setField(jwtUtil, "jwtValidity", -1L);

        String token = jwtUtil.generateToken(user());

        // jjwt lancia ExpiredJwtException già in fase di parsing di un token scaduto
        assertThatThrownBy(() -> jwtUtil.extractAllClaims(token))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    void getSigningKey_withBlankSecret_generatesEphemeralKey() {
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", "");

        String token = jwtUtil.generateToken(user());

        // La chiave effimera firma e verifica correttamente all'interno della stessa istanza
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("mario");
    }
}

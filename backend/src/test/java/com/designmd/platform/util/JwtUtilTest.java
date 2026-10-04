package com.designmd.platform.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtUtil 单元测试（纯单元测试，不启动 Spring 容器）。
 * <p>覆盖点：token 生成与解析、角色声明、过期 token、非法/篡改/异密钥 token 的容错
 * （解析失败统一返回 null，不抛异常，保证过滤器链不会因坏 token 中断）。
 */
class JwtUtilTest {

    private static final String SECRET = "design-md-platform-unit-test-secret-key-0123456789";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = newJwtUtil(SECRET, 86400000L);
    }

    private JwtUtil newJwtUtil(String secret, long expiration) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", secret);
        ReflectionTestUtils.setField(util, "expiration", expiration);
        return util;
    }

    @Test
    @DisplayName("生成的 token 可解析出 username / userId / role")
    void generateAndParseToken() {
        String token = jwtUtil.generateToken(7L, "alice", "USER");

        Claims claims = jwtUtil.parseToken(token);

        assertThat(token).isNotBlank();
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("userId", Long.class)).isEqualTo(7L);
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }

    @Test
    @DisplayName("ADMIN 角色声明可正确解析（供 SecurityConfig 的 hasRole 判定）")
    void parseAdminRole() {
        Claims claims = jwtUtil.parseToken(jwtUtil.generateToken(1L, "admin", "ADMIN"));

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("已过期的 token 解析失败返回 null")
    void parseExpiredTokenReturnsNull() {
        JwtUtil expired = newJwtUtil(SECRET, -1000L);

        assertThat(expired.parseToken(expired.generateToken(7L, "alice", "USER"))).isNull();
    }

    @Test
    @DisplayName("非法 token 字符串解析失败返回 null 且不抛异常")
    void parseInvalidTokenReturnsNull() {
        assertThat(jwtUtil.parseToken("not-a-jwt-token")).isNull();
        assertThat(jwtUtil.parseToken("")).isNull();
        assertThat(jwtUtil.parseToken(null)).isNull();
    }

    @Test
    @DisplayName("被篡改的 token 解析失败返回 null")
    void parseTamperedTokenReturnsNull() {
        String token = jwtUtil.generateToken(7L, "alice", "USER");

        assertThat(jwtUtil.parseToken(token + "tampered")).isNull();
        assertThat(jwtUtil.parseToken("Bearer " + token)).isNull();
    }

    @Test
    @DisplayName("使用其他密钥签发的 token 解析失败返回 null")
    void parseTokenWithWrongSecretReturnsNull() {
        JwtUtil other = newJwtUtil("another-secret-key-for-unit-test-0123456789", 86400000L);
        String token = other.generateToken(7L, "alice", "USER");

        assertThat(jwtUtil.parseToken(token)).isNull();
    }
}

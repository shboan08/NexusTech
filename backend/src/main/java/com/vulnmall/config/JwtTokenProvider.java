package com.vulnmall.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.*;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final KeyPair rsaKeyPair;
    private final String publicKeyPem;
    private final com.vulnmall.service.ScoreboardService scoreboardService;

    public JwtTokenProvider(@org.springframework.context.annotation.Lazy com.vulnmall.service.ScoreboardService scoreboardService) {
        this.scoreboardService = scoreboardService;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.rsaKeyPair = generator.generateKeyPair();
            this.publicKeyPem = formatPublicKeyToPem(this.rsaKeyPair.getPublic());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA Key initialization failed", e);
        }
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public PublicKey getRsaPublicKey() {
        return rsaKeyPair.getPublic();
    }

    /**
     * 표준 발급: RS256 비대칭키 서명
     */
    public String generateToken(Long userId, String username, String role) {
        long now = System.currentTimeMillis();
        long expirationMs = 1000 * 60 * 60 * 24; // 24시간

        return Jwts.builder()
                .setHeaderParam("typ", "JWT")
                .setHeaderParam("alg", "RS256")
                .setSubject(username)
                .claim("userId", userId)
                .claim("role", role)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expirationMs))
                .signWith(rsaKeyPair.getPrivate(), SignatureAlgorithm.RS256)
                .compact();
    }

    /**
     * [고난도 취약점]: Algorithm Confusion (RS256 vs HS256)
     * 클라이언트가 토큰 헤더의 alg를 HS256으로 변조하고 서버의 공개키(PEM)를 HMAC 대칭키로 사용하여 서명할 경우,
     * 파서가 헤더의 alg를 확인하여 대칭키 검증 경로로 분기하면서 서명 검증을 통과시킴.
     */
    public Claims parseAndValidateToken(String token) {
        try {
            // 헤더 사전 파싱
            String[] splitToken = token.split("\\.");
            if (splitToken.length < 2) {
                throw new MalformedJwtException("Invalid token format");
            }
            String headerJson = new String(Base64.getUrlDecoder().decode(splitToken[0]));

            // alg 헤더에 따른 취약한 키 해석
            if (headerJson.contains("\"alg\":\"HS256\"") || headerJson.contains("\"alg\": \"HS256\"")) {
                if (scoreboardService != null) {
                    scoreboardService.markFound("JWT_CONFUSION");
                }
                // Key Confusion: 공개키 PEM 문자열의 바이트를 대칭키(HMAC)로 간주하여 검증
                byte[] hmacKeyBytes = publicKeyPem.getBytes();
                SecretKey hmacKey = Keys.hmacShaKeyFor(hmacKeyBytes);
                return Jwts.parserBuilder()
                        .setSigningKey(hmacKey)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();
            } else {
                // 정상 RS256 검증
                return Jwts.parserBuilder()
                        .setSigningKey(rsaKeyPair.getPublic())
                        .build()
                        .parseClaimsJws(token)
                        .getBody();
            }
        } catch (JwtException | IllegalArgumentException e) {
            throw new RuntimeException("JWT Validation failed: " + e.getMessage());
        }
    }

    private String formatPublicKeyToPem(PublicKey publicKey) {
        String base64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" +
                base64.replaceAll("(.{64})", "$1\n") +
                "\n-----END PUBLIC KEY-----\n";
    }
}

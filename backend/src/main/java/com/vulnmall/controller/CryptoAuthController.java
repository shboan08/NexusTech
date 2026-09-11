package com.vulnmall.controller;

import com.vulnmall.entity.User;
import com.vulnmall.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth/crypto")
public class CryptoAuthController {

    private final UserRepository userRepository;
    // 16바이트 고정 대칭키
    private static final byte[] AES_KEY = "VulnMallSecKey12".getBytes(StandardCharsets.UTF_8);

    public CryptoAuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * [WSTG-CRYP-02: AES-ECB Mode Block Shuffling Flaw]
     * 자동 로그인(Remember-Me) 쿠키 발급
     * 취약한 AES-ECB 모드(초기화 벡터 IV 부재)를 사용하여 16바이트 블록 단위 암호화 수행
     */
    @PostMapping("/remember-me")
    public ResponseEntity<?> issueRememberMeToken(@RequestParam("username") String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "사용자 없음"));

        User user = userOpt.get();
        // 포맷: "role=" + role + ";user=" + username
        String payload = String.format("role=%-5s;user=%s", user.getRole(), user.getUsername());

        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String tokenHex = HexFormat.of().formatHex(encrypted);

            return ResponseEntity.ok(Map.of(
                    "rememberMeToken", tokenHex,
                    "cipherMode", "AES/ECB/PKCS5Padding",
                    "message", "Remember-Me 토큰이 발급되었습니다."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Remember-Me 토큰 검증 (블록 셔플링을 통한 권한 상승 허용)
     */
    @PostMapping("/remember-me/verify")
    public ResponseEntity<?> verifyRememberMeToken(@RequestParam("token") String tokenHex) {
        try {
            byte[] cipherBytes = HexFormat.of().parseHex(tokenHex);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(cipherBytes);
            String payload = new String(decrypted, StandardCharsets.UTF_8);

            // 파싱: "role=ADMIN;user=..."
            String role = "USER";
            String username = "unknown";
            String[] parts = payload.split(";");
            for (String part : parts) {
                if (part.startsWith("role=")) role = part.substring(5).trim();
                if (part.startsWith("user=")) username = part.substring(5).trim();
            }

            return ResponseEntity.ok(Map.of(
                    "status", "AUTHENTICATED",
                    "username", username,
                    "role", role,
                    "rawDecryptedPayload", payload
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Decryption failed",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * [WSTG-CRYP-03: Predictable Password Reset Token]
     * 예측 가능한 시간 기반 MD5 리셋 토큰 생성
     * token = MD5(username + (timestamp_in_seconds))
     */
    @PostMapping("/forgot-password-link")
    public ResponseEntity<?> generateResetLink(@RequestParam("username") String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "사용자 없음"));

        try {
            long epochSecond = System.currentTimeMillis() / 1000;
            String raw = username + epochSecond;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            String token = HexFormat.of().formatHex(digest);

            User user = userOpt.get();
            user.setResetToken(token);
            userRepository.updateUser(user);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "비밀번호 재설정 이메일이 발송되었습니다 (시뮬레이션).",
                    "resetUrl", "/reset-password?token=" + token,
                    "algorithm", "MD5(username + timestamp)"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}

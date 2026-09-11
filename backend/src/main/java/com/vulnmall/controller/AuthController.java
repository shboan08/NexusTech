package com.vulnmall.controller;

import com.vulnmall.config.JwtTokenProvider;
import com.vulnmall.dto.AuthDto;
import com.vulnmall.entity.User;
import com.vulnmall.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final com.vulnmall.service.ScoreboardService scoreboardService;

    public AuthController(UserRepository userRepository, JwtTokenProvider jwtTokenProvider,
                          com.vulnmall.service.ScoreboardService scoreboardService) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.scoreboardService = scoreboardService;
    }

    /**
     * [공개키 노출 엔드포인트]
     * JWT 비대칭키 서명 검증을 위해 클라이언트/외부 서비스에 공개키 제공.
     * 공격자는 이 공개키 PEM 문자열을 가져와 HMAC(HS256) 대칭키로 악용(Key Confusion 공격).
     */
    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        Map<String, String> response = new HashMap<>();
        response.put("algorithm", "RS256");
        response.put("publicKey", jwtTokenProvider.getPublicKeyPem());
        return ResponseEntity.ok(response);
    }

    /**
     * 로그인 엔드포인트
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthDto.LoginRequest request) {
        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());

        // 계정 열거 취약점: 사용자 존재 유무에 따라 서로 다른 응답 메시지 반환
        if (userOpt.isEmpty()) {
            String flag = scoreboardService.markFound("USER_ENUM");
            return ResponseEntity.status(401)
                    .header("X-Vuln-Flag", flag)
                    .body(Map.of(
                            "status", 401,
                            "error", "UserNotFound",
                            "message", "해당 아이디('" + request.getUsername() + "')는 등록되지 않은 사용자입니다.",
                            "flag", flag
                    ));
        }

        User user = userOpt.get();
        if (!user.getPassword().equals(request.getPassword())) {
            String flag = scoreboardService.markFound("USER_ENUM");
            return ResponseEntity.status(401)
                    .header("X-Vuln-Flag", flag)
                    .body(Map.of(
                            "status", 401,
                            "error", "BadCredentials",
                            "message", "비밀번호가 올바르지 않습니다.",
                            "flag", flag
                    ));
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        return ResponseEntity.ok(new AuthDto.AuthResponse(token, user));
    }

    /**
     * 회원가입 엔드포인트 (Mass Assignment 가능)
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthDto.RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "이미 사용 중인 아이디입니다."));
        }

        String flag = null;
        if (request.getRole() != null && !request.getRole().equalsIgnoreCase("USER")) {
            flag = scoreboardService.markFound("MASS_ASSIGN_REG");
        }

        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setPassword(request.getPassword());
        newUser.setEmail(request.getEmail());
        newUser.setSecurityQuestion(request.getSecurityQuestion());
        newUser.setSecurityAnswer(request.getSecurityAnswer());
        // 취약점: 사용자가 전달한 role 및 balance를 그대로 신뢰
        newUser.setRole(request.getRole() != null ? request.getRole() : "USER");
        newUser.setBalance(request.getBalance() != null ? request.getBalance() : new java.math.BigDecimal("100000.00"));

        Long userId = userRepository.createUser(newUser);
        newUser.setId(userId);

        String token = jwtTokenProvider.generateToken(newUser.getId(), newUser.getUsername(), newUser.getRole());
        var res = ResponseEntity.ok();
        if (flag != null) {
            res.header("X-Vuln-Flag", flag);
        }
        return res.body(new AuthDto.AuthResponse(token, newUser));
    }

    /**
     * 프로필 조회
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        String username = auth.getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        return userOpt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 프로필 업데이트 (Mass Assignment 취약점: role: "ADMIN" 전송 시 즉시 권한 상승)
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody AuthDto.ProfileUpdateRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        String username = auth.getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();

        User user = userOpt.get();
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getSecurityQuestion() != null) user.setSecurityQuestion(request.getSecurityQuestion());
        if (request.getSecurityAnswer() != null) user.setSecurityAnswer(request.getSecurityAnswer());
        
        String flag = null;
        // [Mass Assignment]: 일반 유저가 role을 ADMIN으로 변조하거나 balance를 마음대로 충전 가능
        if (request.getRole() != null) {
            user.setRole(request.getRole());
            if ("ADMIN".equalsIgnoreCase(request.getRole())) {
                flag = scoreboardService.markFound("MASS_ASSIGN_PROFILE");
            }
        }
        if (request.getBalance() != null) user.setBalance(request.getBalance());

        userRepository.updateUser(user);
        Map<String, Object> resp = new java.util.HashMap<>(Map.of("message", "프로필이 성공적으로 변경되었습니다.", "user", user));
        var res = ResponseEntity.ok();
        if (flag != null) {
            resp.put("flag", flag);
            res.header("X-Vuln-Flag", flag);
        }
        return res.body(resp);
    }

    /**
     * 비밀번호 재설정
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody AuthDto.PasswordResetRequest request) {
        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "존재하지 않는 사용자입니다."));
        }

        User user = userOpt.get();
        if (user.getSecurityAnswer() == null || !user.getSecurityAnswer().equalsIgnoreCase(request.getSecurityAnswer().trim())) {
            return ResponseEntity.badRequest().body(Map.of("message", "보안 질문의 답변이 일치하지 않습니다."));
        }

        userRepository.updatePassword(user.getUsername(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "비밀번호가 성공적으로 재설정되었습니다."));
    }

    /**
     * [WSTG-SESS-05: Cross-Site Request Forgery (CSRF)]
     * CSRF 토큰 검증 없이 파라미터 기반으로 회원의 이메일 강제 변조 가능
     */
    @RequestMapping(value = "/change-email", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> changeEmailCsrf(@RequestParam("username") String username,
                                             @RequestParam("email") String newEmail) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "사용자를 찾을 수 없습니다."));

        String flag = scoreboardService.markFound("CSRF");

        User user = userOpt.get();
        user.setEmail(newEmail);
        userRepository.updateUser(user);

        Map<String, Object> resp = new java.util.HashMap<>(Map.of(
                "status", "SUCCESS",
                "username", username,
                "newEmail", newEmail,
                "message", "이메일이 '" + newEmail + "'로 성공적으로 변경되었습니다."
        ));
        resp.put("flag", flag);
        return ResponseEntity.ok().header("X-Vuln-Flag", flag).body(resp);
    }
}

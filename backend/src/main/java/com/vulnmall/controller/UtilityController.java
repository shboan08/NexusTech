package com.vulnmall.controller;

import com.vulnmall.dto.UtilDto;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/util")
public class UtilityController {

    private final com.vulnmall.service.ScoreboardService scoreboardService;

    public UtilityController(com.vulnmall.service.ScoreboardService scoreboardService) {
        this.scoreboardService = scoreboardService;
    }

    /**
     * [고난도 Command Injection]
     * 배송 물류 서버 핑 진단 도구
     * 단순 도메인/IP 형식 검사를 시도하지만, 정규식이 공백이나 세미콜론(;), 파이프(|), 백틱(`)을 완벽히 방어하지 못하거나
     * OS 쉘 인자로 직접 결합되어 임의 명령 실행 가능
     */
    @PostMapping("/ping")
    public ResponseEntity<?> pingHost(@RequestBody UtilDto.PingRequest request) {
        String host = request.getTargetHost();
        if (host == null || host.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "대상 호스트(IP/도메인)를 입력해주세요."));
        }

        if (host.contains(";") || host.contains("&") || host.contains("|") || host.contains("`") || host.contains("$") || host.contains("\n")) {
            scoreboardService.markFound("CMD_INJECTION");
        }

        StringBuilder output = new StringBuilder();
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            String[] command;

            if (isWindows) {
                command = new String[]{"cmd.exe", "/c", "ping -n 2 " + host};
            } else {
                command = new String[]{"/bin/sh", "-c", "ping -c 2 " + host};
            }

            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();

            return ResponseEntity.ok(Map.of(
                    "targetHost", host,
                    "output", output.toString()
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * [고난도 SSRF & 파서 불일치 취약점]
     * 상품 외부 이미지 가져오기 기능
     * 미흡한 사설 IP 필터링: 'localhost', '127.0.0.1' 문자열만 단순 검사하여
     * 10진수 IP(2130706433), 16진수 IP(0x7f.1), IPv6 맵핑([::ffff:127.0.0.1]),
     * 클라우드 메타데이터(169.254.169.254), 또는 DNS 리바인딩 도메인으로 내부망 우회 접근
     */
    @PostMapping("/fetch-image")
    public ResponseEntity<?> fetchRemoteImage(@RequestBody UtilDto.FetchImageRequest request) {
        String imageUrl = request.getImageUrl();
        if (imageUrl == null || !imageUrl.startsWith("http")) {
            return ResponseEntity.badRequest().body(Map.of("message", "유효한 HTTP(S) URL을 입력하세요."));
        }

        try {
            URL url = new URL(imageUrl);
            String host = url.getHost().toLowerCase();

            // SSRF 시도 감지 (사설망, 메타데이터, 10진수 IP, 루프백 등)
            if (host.contains("2130706433") || host.contains("0x7f") || host.contains("169.254") || host.contains("127.0.0.1") || host.contains("localhost") || host.contains("0.0.0.0") || host.contains("10.") || host.contains("192.168.")) {
                scoreboardService.markFound("SSRF");
            }

            // 미흡한 보안 필터 (시니어 보안 전문가 분석 대상)
            if ("localhost".equals(host) || "127.0.0.1".equals(host)) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "SSRF Filter Triggered",
                        "message", "내부 루프백 주소(localhost, 127.0.0.1)는 보안 정책상 접근할 수 없습니다."
                ));
            }

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("User-Agent", "VulnMall-Bot/2.0");

            int responseCode = connection.getResponseCode();
            InputStream is = (responseCode >= 400) ? connection.getErrorStream() : connection.getInputStream();
            byte[] bytes = is.readAllBytes();

            String contentType = connection.getContentType();
            if (contentType == null) contentType = "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(bytes);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Fetch failed",
                    "details", e.getMessage()
            ));
        }
    }
}

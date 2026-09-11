package com.vulnmall.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class PublicExposureController {

    /**
     * [WSTG-INFO-05: Review Webserver Metafiles for Information Leakage]
     * 노출된 .git/HEAD 파일
     */
    @GetMapping(value = "/.git/HEAD", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getGitHead() {
        return "ref: refs/heads/main\n";
    }

    /**
     * [WSTG-CONF-04: Review Old, Backup and Unreferenced Files for Sensitive Information]
     * 방치된 환경설정 파일 (.env)
     */
    @GetMapping(value = "/.env", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getEnvFile() {
        return "# VULN-MALL PRODUCTION ENVIRONMENT BACKUP\n" +
               "DATABASE_URL=jdbc:mysql://localhost:3306/vulnmall\n" +
               "DB_USERNAME=root\n" +
               "DB_PASSWORD=vulnroot1234\n" +
               "JWT_SECRET=VulnMallSuperSecretKeyForJWTSigning1234567890\n" +
               "AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE\n" +
               "AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\n";
    }

    /**
     * 방치된 데이터베이스 덤프 백업 파일 (/backup.sql)
     */
    @GetMapping(value = "/backup.sql", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getBackupSql() {
        return "-- DUMP BACKUP VULNMALL 2026-09-01\n" +
               "INSERT INTO users (username, password, email, role) VALUES ('admin', 'admin123', 'admin@vulnmall.local', 'ADMIN');\n" +
               "INSERT INTO users (username, password, email, role) VALUES ('victim', 'pass1234', 'victim@secure-corp.com', 'USER');\n";
    }

    /**
     * [WSTG-CLNT-04: Client-Side URL Redirect (Open Redirect)]
     * 미흡한 정규식 검증으로 도메인 우회 피싱 리다이렉트 허용
     */
    @GetMapping("/api/auth/redirect")
    public void openRedirect(@RequestParam("url") String targetUrl, HttpServletResponse response) throws IOException {
        // 미흡한 검증: 'vulnmall.local' 문자열 포함 여부만 단순 확인
        if (targetUrl.contains("vulnmall.local") || targetUrl.startsWith("/")) {
            response.sendRedirect(targetUrl);
        } else {
            response.sendRedirect("/?error=InvalidRedirectUrl");
        }
    }
}

package com.vulnmall.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final String UPLOAD_DIR = "uploads";
    private final com.vulnmall.service.ScoreboardService scoreboardService;

    public FileController(com.vulnmall.service.ScoreboardService scoreboardService) {
        this.scoreboardService = scoreboardService;
        File uploadFolder = new File(UPLOAD_DIR);
        if (!uploadFolder.exists()) {
            uploadFolder.mkdirs();
        }
    }

    /**
     * [고난도 Path Traversal / Arbitrary File Read]
     * 매뉴얼 다운로드 기능: 불완전한 필터(특정 단어만 단순 치환)로 상위 디렉터리 접근 허용
     * filename=../../../../app/src/main/resources/application.yml 또는 /etc/passwd 다운로드
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadManual(@RequestParam("filename") String filename) {
        String flag = null;
        if (filename != null && (filename.contains("..") || filename.startsWith("/") || filename.contains("passwd") || filename.contains(".yml"))) {
            flag = scoreboardService.markFound("PATH_TRAVERSAL");
        }

        try {
            // 미흡한 방어: 단순 null 체크만 수행하거나 기본 경로와 단순 결합
            File baseDir = new File("src/main/resources/manuals");
            if (!baseDir.exists()) {
                baseDir = new File(".");
            }

            File targetFile = new File(baseDir, filename);

            if (!targetFile.exists() || !targetFile.isFile()) {
                // 대체 절대경로 시도
                targetFile = new File(filename);
            }

            if (!targetFile.exists()) {
                return ResponseEntity.notFound().build();
            }

            byte[] fileBytes = Files.readAllBytes(targetFile.toPath());
            ByteArrayResource resource = new ByteArrayResource(fileBytes);

            var res = ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + targetFile.getName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(fileBytes.length);
            if (flag != null) {
                res.header("X-Vuln-Flag", flag);
            }
            return res.body(resource);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * [Unrestricted File Upload]
     * 이미지 및 첨부파일 업로드 시 확장자 화이트리스트 검증 누락
     * 클라이언트가 지정한 원본 파일명 유지 또는 HTML/SVG/JSP 스크립트 파일 그대로 저장
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "파일이 비어 있습니다."));
        }

        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) originalFilename = "upload_" + UUID.randomUUID();

            String flag = null;
            String lower = originalFilename.toLowerCase();
            if (lower.endsWith(".jsp") || lower.endsWith(".html") || lower.endsWith(".svg") || lower.endsWith(".sh") || lower.endsWith(".php") || lower.endsWith(".exe")) {
                flag = scoreboardService.markFound("FILE_UPLOAD");
            }

            // 저장 경로 (웹 루트에 직접 노출되는 uploads 디렉터리)
            Path targetLocation = Paths.get(UPLOAD_DIR).resolve(originalFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "/uploads/" + originalFilename;
            Map<String, Object> resp = new java.util.HashMap<>(Map.of(
                    "message", "파일이 성공적으로 업로드되었습니다.",
                    "url", fileUrl,
                    "filename", originalFilename,
                    "size", file.getSize()
            ));
            var res = ResponseEntity.ok();
            if (flag != null) {
                resp.put("flag", flag);
                res.header("X-Vuln-Flag", flag);
            }
            return res.body(resp);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "파일 저장 실패: " + e.getMessage()));
        }
    }

    /**
     * [WSTG-INPV-12: Zip Slip Vulnerability]
     * 대량 이미지/매뉴얼 ZIP 압축 해제 기능
     * ZIP 내부 엔트리의 상대경로(../../)를 검증하지 않고 그대로 출력 파일 경로로 조합
     */
    @PostMapping("/upload-zip")
    public ResponseEntity<?> uploadZipArchive(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "ZIP 파일이 비어 있습니다."));

        java.util.List<String> extractedFiles = new java.util.ArrayList<>();
        String flag = null;
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(file.getInputStream())) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().contains("..")) {
                    flag = scoreboardService.markFound("ZIP_SLIP");
                }
                // Zip Slip 결함: canonical path 검증 없이 상위 디렉터리 탈출 허용
                File destinationFile = new File(UPLOAD_DIR, entry.getName());
                if (entry.isDirectory()) {
                    destinationFile.mkdirs();
                } else {
                    File parent = destinationFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(destinationFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    extractedFiles.add(destinationFile.getPath());
                }
                zis.closeEntry();
            }

            Map<String, Object> resp = new java.util.HashMap<>(Map.of(
                    "message", "압축 파일이 성공적으로 해제되었습니다.",
                    "extractedFiles", extractedFiles
            ));
            var res = ResponseEntity.ok();
            if (flag != null) {
                resp.put("flag", flag);
                res.header("X-Vuln-Flag", flag);
            }
            return res.body(resp);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "ZIP 해제 실패: " + e.getMessage()));
        }
    }
}

package com.vulnmall.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/support")
public class SupportController {

    /**
     * [WSTG-INPV-01: Reflected Cross-Site Scripting (Reflected XSS)]
     * 고객지원 FAQ 검색어 및 안내 메시지 직접 반영
     */
    @GetMapping(value = "/echo", produces = MediaType.TEXT_HTML_VALUE)
    public String echoMessage(@RequestParam(value = "msg", defaultValue = "안내 메시지가 없습니다.") String msg) {
        // 입력값을 이스케이프 없이 HTML 본문에 직접 리플렉션
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Support Notice</title></head>" +
               "<body style='font-family:sans-serif; padding:40px; background:#121828; color:#fff;'>" +
               "<h2>고객지원 공지 및 검색 피드백</h2>" +
               "<div id='noticeBox' style='border:1px solid #00f2fe; padding:20px; border-radius:8px;'>" +
               msg +
               "</div>" +
               "<p><a href='/' style='color:#00f2fe;'>← 쇼핑몰 메인으로 돌아가기</a></p>" +
               "</body></html>";
    }

    @GetMapping("/faq")
    public ResponseEntity<?> searchFaq(@RequestParam(value = "query", required = false, defaultValue = "") String query) {
        return ResponseEntity.ok(Map.of(
                "query", query,
                "feedback", "고객님께서 검색하신 '" + query + "' 관련 자주 묻는 질문 0건이 조회되었습니다.",
                "status", 200
        ));
    }
}

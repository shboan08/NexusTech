# ⚡ NEXUS TECH (vuln-mall)
> **OWASP WSTG 기반 엔터프라이즈급 실전 취약점 테스트베드 & DAST 벤치마크 타깃 애플리케이션**

![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-brightgreen?logo=springboot)
![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=java)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?logo=mysql)
![Docker](https://img.shields.io/badge/Docker-Supported-2496ED?logo=docker)
![OWASP WSTG](https://img.shields.io/badge/OWASP-WSTG_Aligned-red?logo=owasp)

**NEXUS TECH**는 실제 상용 서비스(사이버 하드웨어 이커머스 쇼핑몰)와 100% 동일한 외형과 사용자 경험을 제공하면서, 내부적으로는 **경력 5년 이상의 보안 전문가/시니어 모의해커가 심층 분석해야 발견할 수 있는 25개 이상의 고난도 웹 취약점 및 비즈니스 로직 결함**을 탑재한 실전형 웹 보안 테스트베드입니다.

---

## 🚀 빠른 시작 (Quick Start)

### 방법 1. Docker Compose (가장 권장 🌟)
호스트 PC에 Java나 Maven, MySQL이 전혀 설치되어 있지 않아도 **도커만 있으면 원클릭으로 빌드 및 실행**됩니다.

```bash
# 1. 저장소 클론
git clone https://github.com/shboan08/nexustech.git
cd nexustech

# 2. 컨테이너 빌드 및 백그라운드 실행
docker compose up --build -d

# 3. 브라우저 접속
# http://localhost:8080/
```

- 종료: `docker compose down`

---

### 방법 2. 로컬 Java 실행 (H2 인메모리 모드)
도커 없이 JDK 17 이상만 설치되어 있다면 별도의 DB 설치 없이 즉시 실행할 수 있습니다.

```bash
# 사전 빌드된 JAR 실행 (H2 내장 DB + 시드 데이터 자동 로드)
java -jar backend/target/vuln-mall-backend-1.0.0.jar --spring.profiles.active=h2
```

---

## 🔑 기본 계정 정보 (Default Accounts)

| 계정명 (Username) | 비밀번호 (Password) | 권한 (Role) | 보유 잔액 | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| **admin** | `admin123` | `ADMIN` | ₩9,999,999 | 관리자 계정 (전체 회원/주문 열람 가능) |
| **alice** | `alice123` | `USER` | ₩250,000 | 일반 회원 |
| **bob** | `bob123` | `USER` | ₩150,000 | 일반 회원 |
| **victim** | `pass1234` | `USER` | ₩500,000 | VIP 고객 (IDOR 및 BOLA 기밀 주문 대상) |

---

## 🛡️ 탑재된 OWASP WSTG 취약점 매트릭스 (25개 시나리오)

### 1. SQL Injection 5대 변형군 (WSTG-INPV-05)
1. **Error-Based SQLi**: `GET /api/products?keyword='` — 복합 `LIKE` 괄호 불일치로 500 에러 스택 트레이스에 SQL 구조 노출
2. **Union-Based SQLi**: `GET /api/products/filter?category=' UNION SELECT id, username, role, balance, email FROM users -- ` — 전체 회원 DB 결합 추출
3. **Boolean-Based Blind SQLi**: `GET /api/coupons/verify?code=WELCOME2026' AND 1=1 -- ` — 에러 없이 `valid: true/false` 응답 차이로 1비트씩 데이터 추론
4. **Time-Based Blind SQLi**: `GET /api/orders/track?code=KR-LOGI-88219' AND SLEEP(2) -- ` — `SLEEP(2)` 함수 실행으로 2초 응답 지연 유발
5. **Second-Order SQLi (2차 인젝션)**: 1단계 `POST /api/inquiries`에 안전하게 저장된 문의글 제목이 2단계 `GET /api/inquiries/{id}/audit` 관리자 감사 로그 조회 시 2차 쿼리로 탈출 실행

### 2. XSS 3대 변형군 (WSTG-INPV-01/02 & WSTG-CLNT-01)
1. **Reflected XSS**: `GET /api/support/echo?msg=<script>alert('XSS')</script>` — 검색어/공지 메시지가 HTML 응답 본문에 직접 반영
2. **Stored XSS**: `POST /api/inquiries` (Q&A) 및 `POST /api/reviews` (리뷰) — DB에 무필터 영속화 후 타 사용자 화면에 렌더링
3. **DOM-Based XSS**: `http://localhost:8080/#notice=<img src=x onerror=alert('DOM-XSS')>` — 클라이언트 스크립트가 URL 해시를 읽어 상단 공지 바 `innerHTML`에 삽입

### 3. 입력값 검증 & 파일 처리 (WSTG-INPV)
1. **Zip Slip (압축 탈출 업로드)**: `POST /api/files/upload-zip` — ZIP 내부 `../../` 경로를 이용해 상위 디렉터리 파일 임의 덮어쓰기
2. **SpEL SSTI (서버 템플릿 인젝션)**: `POST /api/orders/receipt/template` — `#{7*7}` 주입 시 서버 사이드 동적 연산(`49 %`) 실행
3. **CRLF Log Injection**: `POST /api/support/log` — `\r\n` 미필터링으로 서버 로그 파일에 가짜 감사 로그 위조 삽입

### 4. 암호학적 결함 (WSTG-CRYP)
1. **AES-ECB 모드 블록 셔플링**: `POST /api/auth/crypto/remember-me` — IV가 없는 16바이트 블록 암호화로 블록 교체를 통한 `role=ADMIN` 조작
2. **Predictable Password Reset Token**: `POST /api/auth/crypto/forgot-password-link` — `MD5(username + 시간초)` 기반 토큰 발급으로 무차별 대입 가능

### 5. 인가 & 세션 결함 (WSTG-ATHZ / SESS / CLNT)
1. **HTTP Parameter Pollution (HPP)**: `POST /api/orders/{id}/status-update?status=PENDING&status=REFUNDED` — 중복 파라미터 시 최종 인자 채택하여 환불 상태 강제 변경
2. **Path Traversal (임의 파일 다운로드)**: `GET /api/files/download?filename=../application.yml` — 서버 내부 설정 파일 및 DB 자격증명 유출
3. **BOLA / IDOR**: `GET /api/orders/{id}`, `PUT /api/orders/{id}/shipping` — 타인 주문 상세 조회 및 배송지 무단 변경
4. **CSRF (Cross-Site Request Forgery)**: `POST /api/auth/change-email` — CSRF 토큰 부재로 타 회원 이메일 강제 변조
5. **Open Redirect**: `GET /api/auth/redirect?url=http://attacker.com?vulnmall.local` — 미흡한 정규식 우회로 외부 피싱 도메인 리다이렉트

### 6. 비즈니스 로직 결함 (WSTG-BUSL)
1. **Price Tampering (가격 조작)**: `POST /api/orders` — 클라이언트 전달 `totalAmount: 1`을 서버가 신뢰하여 1원에 주문 승인
2. **부동소수점 오차 포인트 차익거래**: `POST /api/points/exchange` — `0.8`원 환전 시 **잔액 차감 0원, 포인트 1지급** 무한 파밍
3. **동시성 Race Condition**: `POST /api/coupons/redeem` — DB 락 부재(TOCTOU)로 동일 쿠폰을 동시 다중 요청 시 중복 소모 충전
4. **Step Skipping (단계 건너뛰기)**: `POST /api/orders/{id}/direct-confirm` — 결제 없이 임의 주문 'PAID' 승인

### 7. 정보 노출 및 관리자 설정 결함 (WSTG-INFO / CONF / ATHN)
1. **JWT Algorithm Confusion (Key Confusion)**: RSA 공개키(PEM)를 HMAC 대칭키로 오인하는 Key Confusion 결함
2. **엔터프라이즈 WAF 우회**: `X-Forwarded-For: 127.0.0.1` 헤더를 신뢰하여 관리자 API(`/api/admin/users`) 비인가 접근 허용
3. **메타데이터 노출**: `/.git/HEAD`, `/.env`, `/backup.sql`, `/actuator/env`, `/actuator/heapdump`

---

## 📂 프로젝트 구조

```
nexustech/
├── docker-compose.yml              # MySQL + Spring Boot 멀티 컨테이너 설정
├── db/
│   └── init.sql                    # MySQL 테이블 스키마 및 시드 데이터
├── backend/
│   ├── Dockerfile                  # Multi-stage 빌드 Dockerfile
│   ├── pom.xml                     # Maven 의존성 설정
│   └── src/
│       └── main/
│           ├── java/com/vulnmall/   # Spring Boot 소스 코드 (취약점 로직)
│           └── resources/
│               ├── application.yml  # MySQL 연동 설정
│               ├── application-h2.yml # H2 연동 설정
│               ├── schema-h2.sql    # H2 스키마 및 시드
│               └── static/          # 모던 글래스모피즘 이커머스 SPA 프론트엔드
│                   ├── index.html
│                   ├── app.js
│                   └── style.css
└── README.md
```

---

## ⚠️ 면책 조항 (Disclaimer)

본 소프트웨어는 **웹 보안 연구, 모의해킹 실습, 보안 취약점 진단 및 DAST(동적 애플리케이션 보안 점검) 도구 평가**를 위한 교육/테스트 목적으로 제작되었습니다. 본 프로젝트의 코드를 허가받지 않은 실제 상용 시스템 공격에 악용하는 행위는 엄격히 금지되며, 그로 인한 법적 책임은 행위자 본인에게 있습니다.

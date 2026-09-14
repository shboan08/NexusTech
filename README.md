# NEXUS TECH (vuln-mall)
> OWASP WSTG 기반 엔터프라이즈 취약점 테스트베드 및 DAST 벤치마크 타깃 시스템

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://www.oracle.com/java/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Supported-2496ED)](https://www.docker.com/)
[![OWASP WSTG](https://img.shields.io/badge/OWASP-WSTG_Aligned-red)](https://owasp.org/www-project-web-security-testing-guide/)

NEXUS TECH는 상용 이커머스 서비스(하이엔드 테크 하드웨어 쇼핑몰)의 구조와 사용자 인터페이스를 갖춘 웹 애플리케이션입니다. 

내부적으로는 동적 애플리케이션 보안 점검(DAST) 엔진 평가, 웹 취약점 진단, 모의해킹 훈련을 목적으로 **OWASP Web Security Testing Guide(WSTG) 기준 총 61개의 고난도 보안 취약점 및 비즈니스 로직 결함**이 의도적으로 설계되어 있습니다.

각 취약점을 성공적으로 트리거하면 고유한 **CTF 플래그(`FLAG{...}`)가 발급**되며, **실시간 스코어보드([/scoreboard.html](http://localhost:8080/scoreboard.html))**에 즉시 발견 처리됩니다. 언제든지 '발견 기록 초기화' 버튼을 통해 진척도를 리셋하고 반복 훈련할 수 있습니다.

각 취약점의 상세 검증 절차(curl 명령어, 페이로드, 기대 응답)는 [WALKTHROUGH.md](./WALKTHROUGH.md)를 참조하십시오.

---

## 1. 주요 쇼핑몰 비즈니스 기능

1. **출석체크 & 사이버 룰렛 포인트 적립 및 복합 결제 (Gamification)**:
   - 일일 출석체크(+1,000P 지급) 및 6섹터 사이버 룰렛 휠(최대 5,000P 당첨)
   - 주문 결제 시 현금 잔액 + 포인트 복합 결제(`pointsUsed`) 및 1% 캐시백 자동 적립
2. **1:1 고객지원 헬프데스크 & 증빙 파일 첨부 시스템**:
   - 일반 문의, 제품 불량/파손 신고, 반품/환불 기술검토, 펌웨어 기술지원, VIP 전담 상담
   - 불량 사진 및 장비 로그 업로드 지원, 비공개 티켓 암호화 보호
3. **실시간 상품 재고(Stock) 차감 및 품절(Sold-out) / 재입고 알림 웹훅**:
   - 결제 시 실시간 재고 차감, 재고 소진(`stock <= 0`) 시 `SOLD OUT` 뱃지 노출 및 구매 자동 차단
   - 품절 상품에 대한 Webhook URL / 이메일 입고 알림 신청 및 관리자 일괄 발송
4. **전자 세금계산서 / 거래명세서 발행 및 인쇄/다운로드 시스템**:
   - 공급자(NEXUS TECH), 품목별 공급가액, 부가세(10%), 포인트 할인, 전자직인 자동 바인딩
   - 브라우저 인쇄 전용 스타일(`@media print`) 및 PDF 저장 지원
5. **주문 취소 및 환불 / 반품 워크플로우 시스템**:
   - 마이페이지 내 주문 건별 환불 신청, 사유 메모 기재, 지갑 잔액 즉시 복원
6. **위시리스트 (찜하기) & 사이버 커스텀 덱 공유 시스템**:
   - 메인 스토어 찜하기(위시리스트), 찜 상품 기반 나만의 사이버 덱 구성 및 고유 토큰 공유 링크 발급
7. **NEXUS PRIME VIP 멤버십 구독 & 전용 시크릿 특가관**:
   - 월 구독료 결제, VIP 무료배송 혜택, 전용 5만원 바우처 발급, 일반 미노출 전용관
8. **배송지 주소록 원장 & 사이버 지갑 잔액 충전**:
   - 다중 배송지 등록/관리, 가상 결제 게이트웨이 시뮬레이션 및 프로모션 바우처 등록
9. **엔터프라이즈 통합 관리자 제어 포털 (`/admin.html`)**:
   - 대시보드(GMV/회원/주문 메트릭), 상품 원장, 회원 권한/잔액 조정, 직권 환불, 1:1 티켓 관리, 시스템 진단

---

## 2. 시스템 요구사항 및 빠른 실행

### Option A. Docker Compose (권장)
호스트 환경에 Java, Maven, MySQL 설치 없이 Docker 환경만으로 Multi-stage 빌드를 거쳐 즉시 구동됩니다.

```bash
# 1. 저장소 복제
git clone https://github.com/shboan08/NexusTech.git
cd NexusTech

# 2. 컨테이너 빌드 및 백그라운드 실행
docker compose up --build -d

# 3. 서비스 접속
# http://localhost:8080/
```

- 컨테이너 종료: `docker compose down`

---

### Option B. 로컬 독립 실행 (H2 인메모리 모드)
도커를 사용하지 않는 환경에서는 JDK 17 이상이 설치된 환경에서 사전 패키징된 JAR 파일을 바로 실행할 수 있습니다.

```bash
java -jar backend/target/vuln-mall-backend-1.0.0.jar --spring.profiles.active=h2
```

---

## 3. 기본 계정 정보

| 계정명 (Username) | 비밀번호 (Password) | 권한 (Role) | 초기 잔액 | 보유 포인트 | 비고 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **admin** | `admin123` | `ADMIN` | ₩9,999,999 | 10,000 P | 관리자 계정 (전체 원장 제어) |
| **alice** | `alice123` | `USER` | ₩250,000 | 2,500 P | 일반 회원 |
| **bob** | `bob123` | `USER` | ₩150,000 | 2,500 P | 일반 회원 |
| **victim** | `pass1234` | `USER` | ₩500,000 | 5,000 P | VIP 고객 (BOLA / IDOR 검증 타깃) |

---

## 4. 취약점 카탈로그 (전체 61개, WALKTHROUGH.md 번호 1:1 매핑)

| 번호 | WSTG ID | 취약점 키 (Key) | 취약점 명칭 | 분류 |
|:---:|:---|:---|:---|:---|
| 1 | WSTG-INPV-05 | `SQLI_LIKE` | SQL Injection - LIKE 문자열 결합 | Injection |
| 2 | WSTG-INPV-05 | `SQLI_UNION` | SQL Injection - UNION-Based | Injection |
| 3 | WSTG-INPV-05 | `SQLI_ORDERBY` | SQL Injection - ORDER BY Blind | Injection |
| 4 | WSTG-INPV-05 | `SQLI_BOOL_BLIND` | SQL Injection - Boolean-Based Blind | Injection |
| 5 | WSTG-INPV-05 | `SQLI_TIME_BLIND` | SQL Injection - Time-Based Blind | Injection |
| 6 | WSTG-INPV-05 | `SQLI_SECOND` | Second-Order SQL Injection | Injection |
| 7 | WSTG-INPV-01 | `XSS_REFLECTED` | Reflected XSS (HTML 응답) | XSS |
| 8 | WSTG-INPV-02 | `XSS_STORED_REVIEW` | Stored XSS (리뷰 댓글) | XSS |
| 9 | WSTG-INPV-02 | `XSS_STORED_INQ` | Stored XSS (문의글) | XSS |
| 10 | WSTG-INPV-09 | `PATH_TRAVERSAL` | Path Traversal (임의 파일 읽기) | Injection |
| 11 | WSTG-INPV-07 | `XXE` | XML External Entity (XXE) | Injection |
| 12 | WSTG-INPV-18 | `SSTI` | Server-Side Template Injection (SpEL) | Injection |
| 13 | WSTG-INPV-12 | `CMD_INJECTION` | Command Injection (OS 명령 실행) | Injection |
| 14 | WSTG-INPV-19 | `SSRF` | SSRF (서버 사이드 요청 위조) | Injection |
| 15 | WSTG-INPV-12 | `ZIP_SLIP` | Zip Slip (경로 탈출) | Injection |
| 16 | WSTG-INPV-15 | `CRLF_LOG` | CRLF / Log Injection | Injection |
| 17 | WSTG-CRYP-02 | `AES_ECB` | AES-ECB 모드 블록 셔플링 | Crypto |
| 18 | WSTG-CRYP-03 | `PREDICTABLE_TOKEN` | 취약한 비밀번호 재설정 토큰 | Crypto |
| 19 | WSTG-ATHN-08 | `JWT_CONFUSION` | JWT Algorithm Confusion | Auth |
| 20 | WSTG-ATHN-02 | `USER_ENUM` | 계정 열거 (Username Enumeration) | Auth |
| 21 | WSTG-ATHN-08 | `MASS_ASSIGN_PROFILE` | Mass Assignment (프로필 권한 상승) | Auth |
| 22 | WSTG-ATHN-08 | `MASS_ASSIGN_REG` | Mass Assignment (회원가입 권한 지정) | Auth |
| 23 | WSTG-ATHZ-04 | `BOLA_READ` | BOLA/IDOR (타인 주문 조회) | Auth |
| 24 | WSTG-ATHZ-04 | `BOLA_WRITE` | BOLA/IDOR (타인 배송지 변경) | Auth |
| 25 | WSTG-ATHZ-04 | `HPP` | HTTP Parameter Pollution | Auth |
| 26 | WSTG-SESS-05 | `CSRF` | CSRF (이메일 강제 변경) | Client |
| 27 | WSTG-CLNT-01 | `XSS_DOM` | DOM-Based XSS | Client |
| 28 | WSTG-CLNT-04 | `OPEN_REDIRECT` | Open Redirect | Client |
| 29 | WSTG-BUSL-09 | `PRICE_TAMPER` | Price Tampering (가격 조작) | BizLogic |
| 30 | WSTG-BUSL-09 | `NEG_QUANTITY` | Negative Quantity (음수 수량) | BizLogic |
| 31 | WSTG-BUSL-03 | `ROUNDING_ERROR` | 부동소수점 오차 포인트 차익거래 | BizLogic |
| 32 | WSTG-BUSL-04 | `RACE_CONDITION` | 동시성 Race Condition | BizLogic |
| 33 | WSTG-BUSL-02 | `WORKFLOW_SKIP` | Workflow Step Skipping | BizLogic |
| 34 | WSTG-CONF-05 | `WAF_BYPASS` | 엔터프라이즈 WAF 우회 | Config |
| 35 | WSTG-INFO-05 | `INFO_EXPOSURE` | 민감정보 노출 (.env/.git/backup.sql) | Config |
| 36 | WSTG-INPV-12 | `FILE_UPLOAD` | 무제한 파일 업로드 | Injection |
| 37 | WSTG-ATHZ-04 | `BOLA_ADDRESS` | BOLA/IDOR (타인 배송지 조회/수정/삭제) | Auth |
| 38 | WSTG-INPV-02 | `XSS_STORED_ADDRESS` | Stored XSS (배송지 및 배송 메모) | XSS |
| 39 | WSTG-INPV-05 | `SQLI_ADDRESS` | SQL Injection (주소 및 우편번호 검색) | Injection |
| 40 | WSTG-BUSL-09 | `WALLET_NEGATIVE_CHARGE` | 결제 금액 음수 충전 및 PG 변조 | BizLogic |
| 41 | WSTG-BUSL-04 | `WALLET_RACE_CONDITION` | 바우처 동시성 Race Condition | BizLogic |
| 42 | WSTG-SESS-05 | `CSRF_WALLET` | CSRF (지갑 잔액 무단 송금) | Client |
| 43 | WSTG-INPV-02 | `XSS_STORED_CART` | Stored XSS (장바구니 요청 메모) | XSS |
| 44 | WSTG-ATHZ-04 | `BOLA_CART` | BOLA/IDOR (타인 장바구니 품목 변조/삭제) | Auth |
| 45 | WSTG-ATHZ-02 | `ADMIN_BYPASS` | 통합 관리자 포털 권한 우회 | Auth |
| 46 | WSTG-BUSL-09 | `MEMBERSHIP_PRICE_TAMPER` | Membership Price Tampering (가입 금액 변조) | BizLogic |
| 47 | WSTG-ATHZ-02 | `MEMBERSHIP_BFLA_BYPASS` | VIP Exclusive Deals BFLA (인가 우회) | Auth |
| 48 | WSTG-BUSL-04 | `MEMBERSHIP_COUPON_RACE` | VIP Coupon Race Condition (쿠폰 무한 중복 발급) | BizLogic |
| 49 | WSTG-INPV-02 | `MEMBERSHIP_STORED_XSS` | Membership Welcome Note Stored XSS | XSS |
| 50 | WSTG-BUSL-04 | `REFUND_RACE_CONDITION` | Double Refund Concurrency Race Condition (이중 환불) | BizLogic |
| 51 | WSTG-BUSL-02 | `REFUND_WORKFLOW_BYPASS` | Refund State & Workflow Step Skipping (반품 검수 우회) | BizLogic |
| 52 | WSTG-INPV-02 | `REFUND_STORED_XSS` | Refund Reason & Memo Stored XSS | XSS |
| 53 | WSTG-ATHZ-04 | `WISHLIST_BOLA_IDOR` | Wishlist & Custom Deck BOLA/IDOR (비공개 덱 무단 열람) | Auth |
| 54 | WSTG-INPV-02 | `WISHLIST_STORED_XSS` | Custom Deck Name & Description Stored XSS | XSS |
| 55 | WSTG-BUSL-04 | `ATTENDANCE_DATE_TAMPER` | Attendance Check-in Date Manipulation & Multi-Claim Race | BizLogic |
| 56 | WSTG-CLNT-01 | `ROULETTE_CLIENT_TAMPER` | Roulette Client-Side Prize Manipulation | Client |
| 57 | WSTG-BUSL-09 | `POINTS_NEGATIVE_EXPLOIT` | Negative Points Usage & Compound Payment Tampering | BizLogic |
| 58 | WSTG-ATHZ-04 | `INQUIRY_BOLA_IDOR` | BOLA/IDOR on Support Ticket & Secret Inquiries | Auth |
| 59 | WSTG-INPV-12 | `TICKET_FILE_UPLOAD` | Unrestricted File Upload on Support Tickets | Injection |
| 60 | WSTG-BUSL-04 | `STOCK_RACE_CONDITION` | Inventory Overselling Concurrency Race Condition | BizLogic |
| 61 | WSTG-INPV-19 | `RESTOCK_WEBHOOK_SSRF` | Restock Notification Callback SSRF | Injection |

---

## 5. 실시간 스코어보드 & CTF 플래그 시스템

Vuln-Mall은 사용자가 취약점을 성공적으로 익스플로잇할 때 즉각적인 피드백을 제공받을 수 있도록 내장 스코어보드를 지원합니다.

- **스코어보드 대시보드 URL**: `http://localhost:8080/scoreboard.html` (별도 브라우저 탭에서 접속 권장)
- **플래그 형식**: `FLAG{VULN_KEY_XXXXXXXX}`
- **WebSocket OOB 자동 감지**: 취약점 공격 요청이 발생하면 백엔드가 내부적으로 이를 감지하고, HTTP 응답을 오염시키지 않는 독립된 **WebSocket Out-Of-Band 채널**(`/ws/scoreboard`)을 통해 스코어보드 대시보드로 실시간 플래그를 푸시합니다. DAST 진단 도구에 힌트나 플래그가 노출되지 않으므로 순수한 벤치마크 평가가 가능합니다.
- **진척도 초기화**: 스코어보드 화면의 `발견 기록 초기화` 버튼을 클릭하면 즉시 모든 취약점 상태가 리셋되어 반복 훈련 및 벤치마크를 수행할 수 있습니다.

---

## 6. 디렉터리 구조

```
NexusTech/
├── docker-compose.yml              # MySQL 및 백엔드 멀티 컨테이너 오케스트레이션
├── db/
│   └── init.sql                    # 초기 데이터베이스 스키마 및 시드 데이터
├── backend/
│   ├── Dockerfile                  # Multi-stage 빌드 Dockerfile
│   ├── pom.xml                     # Maven 의존성 정의
│   └── src/
│       └── main/
│           ├── java/com/vulnmall/   # Spring Boot 백엔드 소스 코드
│           └── resources/
│               ├── application.yml  # MySQL 데이터소스 설정
│               ├── application-h2.yml # H2 인메모리 데이터소스 설정
│               ├── schema-h2.sql    # H2 스키마 및 호환 함수 정의
│               └── static/          # 바닐라 JS/HTML/CSS 프론트엔드 리소스
├── WALKTHROUGH.md                  # 전체 61개 취약점 실습 및 PoC 상세 가이드
└── README.md                       # 프로젝트 소개 및 취약점 카탈로그
```

---

## 7. 법적 고지 (Disclaimer)

본 소프트웨어는 **보안 교육, 공인된 모의해킹 훈련, 보안 취약점 연구 및 DAST 진단 도구 벤치마크 평가**를 위한 목적으로 제작되었습니다. 

본 프로젝트에 포함된 취약점 코드 및 기법을 사전 인가받지 않은 실제 정보통신망이나 타인의 시스템에 적용하는 행위는 관련 법령(정보통신망 이용촉진 및 정보보호 등에 관한 법률 등)에 따라 형사 처벌의 대상이 될 수 있습니다. 제작자는 사용자의 오용 및 불법적 활용으로 인해 발생하는 일체의 민·형사상 책임을 지지 않습니다.

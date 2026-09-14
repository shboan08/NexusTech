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
   - 메인 스토어 찜하기(`❤️`), 찜 상품 기반 나만의 사이버 덱 구성 및 고유 토큰 공유 링크 발급
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

## 4. 취약점 매트릭스 (OWASP WSTG 분류 기준, 총 61개)

### WSTG-INPV: 데이터 검증 및 인젝션 (21개)
1. **SQL Injection - LIKE 문자열 결합** (`SQLI_LIKE`) - 상품 검색 키워드 바인딩 누락
2. **SQL Injection - UNION-Based** (`SQLI_UNION`) - 카테고리 필터링 UNION 쿼리 결합
3. **SQL Injection - ORDER BY Blind** (`SQLI_ORDERBY`) - 정렬 컬럼 및 방향 동적 결합
4. **SQL Injection - Boolean-Based Blind** (`SQLI_BOOL_BLIND`) - 쿠폰 코드 검증 블라인드 신호
5. **SQL Injection - Time-Based Blind** (`SQLI_TIME_BLIND`) - 배송 추적 코드 시간 지연 함수 주입
6. **Second-Order SQL Injection** (`SQLI_SECOND`) - 문의글 제목 저장 후 감사 로그 검색 시 2차 실행
7. **Reflected XSS (HTML 응답)** (`XSS_REFLECTED`) - 고객지원 에코 파라미터 반사
8. **Stored XSS (리뷰 댓글)** (`XSS_STORED_REVIEW`) - 상품 리뷰 본문 영구 저장 스크립트 실행
9. **Stored XSS (고객 문의)** (`XSS_STORED_INQ`) - 1:1 Q&A 문의 본문 스토어드 스크립트
10. **Path Traversal (임의 파일 다운로드)** (`PATH_TRAVERSAL`) - 매뉴얼 다운로드 상위 경로 탐색
11. **XML External Entity (XXE)** (`XXE`) - XML 영수증 파싱 시 외부 엔티티 참조
12. **Server-Side Template Injection (SpEL)** (`SSTI`) - 영수증 템플릿 SpEL 표현식 동적 실행
13. **Command Injection (OS 명령 실행)** (`CMD_INJECTION`) - 물류망 네트워크 핑 진단 인자 탈출
14. **SSRF (서버 사이드 요청 위조)** (`SSRF`) - 이미지 프록시 다운로드 내부망 접근
15. **Zip Slip (Archive Directory Traversal)** (`ZIP_SLIP`) - 압축 해제 엔트리 상위 경로 탈출
16. **CRLF / Log Injection** (`CRLF_LOG`) - 감사 로그 개행 문자 미필터링 조작
17. **무제한 파일 업로드** (`FILE_UPLOAD`) - 공용 업로드 확장자 검증 부재
18. **Stored XSS (배송지 메모)** (`XSS_STORED_ADDRESS`) - 주소록 배송 메모 스토어드 XSS
19. **SQL Injection (주소록 검색)** (`SQLI_ADDRESS`) - 주소록 도로명/우편번호 검색 SQLi
20. **Stored XSS (장바구니 메모)** (`XSS_STORED_CART`) - 장바구니 품목 요청 메모 XSS
21. **1:1 헬프데스크 무제한 파일 업로드** (`TICKET_FILE_UPLOAD`) - 문의 증빙 웹쉘 업로드

### WSTG-CRYP: 암호학적 취약점 (2개)
22. **AES-ECB 모드 블록 셔플링** (`AES_ECB`) - 고정 블록 치환을 통한 Remember-Me 권한 조작
23. **취약한 비밀번호 재설정 토큰** (`PREDICTABLE_TOKEN`) - 예측 가능한 타임스탬프 토큰 생성

### WSTG-ATHN & ATHZ: 인증 및 인가 통제 (13개)
24. **JWT Algorithm Confusion** (`JWT_CONFUSION`) - RSA 공개키를 HMAC 대칭키로 오인하는 알고리즘 혼동
25. **계정 열거 (Username Enumeration)** (`USER_ENUM`) - 로그인 실패 시 차별적 에러 메시지
26. **Mass Assignment (프로필 권한 상승)** (`MASS_ASSIGN_PROFILE`) - 프로필 업데이트 시 role 임의 승격
27. **Mass Assignment (회원가입 권한 지정)** (`MASS_ASSIGN_REG`) - 회원가입 시 ADMIN 등급 강제 부여
28. **BOLA / IDOR (타인 주문 조회)** (`BOLA_READ`) - 타인 주문 식별자 무단 조회
29. **BOLA / IDOR (타인 배송지 변경)** (`BOLA_WRITE`) - 타인 주문 배송 정보 무단 변조
30. **HTTP Parameter Pollution (HPP)** (`HPP`) - 중복 파라미터를 통한 주문 상태 조작
31. **BOLA / IDOR (타인 배송지 주소록)** (`BOLA_ADDRESS`) - 타인 배송지 원장 무단 조회/수정/삭제
32. **BOLA / IDOR (타인 장바구니)** (`BOLA_CART`) - 타인 장바구니 품목 변조 및 삭제
33. **통합 관리자 포털 권한 우회** (`ADMIN_BYPASS`) - 내부 프록시 헤더 조작을 통한 Master 제어 콘솔 접근
34. **VIP 전용관 BFLA 인가 우회** (`MEMBERSHIP_BFLA_BYPASS`) - 비구독자의 VIP 단독 특가 품목 무단 조회
35. **커스텀 덱 BOLA / IDOR** (`WISHLIST_BOLA_IDOR`) - 비공개(Private) 기밀 덱 및 시크릿 메모 무단 열람
36. **1:1 고객지원 티켓 BOLA / IDOR** (`INQUIRY_BOLA_IDOR`) - 타인의 비공개 불량 상담 티켓 무단 열람

### WSTG-SESS & CLNT: 세션 및 클라이언트 취약점 (6개)
37. **Cross-Site Request Forgery (CSRF)** (`CSRF`) - 이메일 강제 변경
38. **DOM-Based XSS** (`XSS_DOM`) - 클라이언트 innerHTML을 통한 XSS
39. **Open Redirect** (`OPEN_REDIRECT`) - 로그인 후 목적지 URL 화이트리스트 검증 부재
40. **CSRF (지갑 잔액 무단 송금)** (`CSRF_WALLET`) - 사용자 모르게 공격자 계정으로 잔액 송금
41. **커스텀 덱 소개글 Stored XSS** (`WISHLIST_STORED_XSS`) - 덱 이름 및 설명 비위생화 DOM 주입
42. **룰렛 당첨 포인트 조작** (`ROULETTE_CLIENT_TAMPER`) - 클라이언트 전달 당첨 포인트 무검증 수용

### WSTG-BUSL: 비즈니스 로직 결함 (13개)
43. **Price Tampering (가격 조작)** (`PRICE_TAMPER`) - 결제 금액 클라이언트 값 신뢰
44. **Negative Quantity (음수 수량)** (`NEG_QUANTITY`) - 장바구니 품목 음수 수량 주입
45. **부동소수점 오차 포인트 차익거래** (`ROUNDING_ERROR`) - 잔액 환전 시 소수점 절삭 오차
46. **동시성 Race Condition** (`RACE_CONDITION`) - 1회용 프로모션 쿠폰 동시 중복 사용
47. **Workflow Step Skipping** (`WORKFLOW_SKIP`) - 주문 승인 단계 건너뛰기
48. **지갑 음수 충전 결제 변조** (`WALLET_NEGATIVE_CHARGE`) - 잔액 충전 시 음수 금액 주입
49. **프로모션 바우처 동시성 Race Condition** (`WALLET_RACE_CONDITION`) - 1회용 바우처 동시 다중 등록
50. **멤버십 가입비 변조** (`MEMBERSHIP_PRICE_TAMPER`) - VIP 구독료 0원 및 음수 결제
51. **VIP 쿠폰 동시성 Race Condition** (`MEMBERSHIP_COUPON_RACE`) - 5만원 VIP 바우처 무한 중복 발급
52. **이중 환불 동시성 Race Condition** (`REFUND_RACE_CONDITION`) - 동일 주문 다중 환불 요청 잔액 증식
53. **반품 검수 절차 우회** (`REFUND_WORKFLOW_BYPASS`) - 배송 완료 건 direct 우회 즉시 환불
54. **출석체크 날짜 변조 및 중복 수령** (`ATTENDANCE_DATE_TAMPER`) - 임의 날짜 주입 및 동시 다중 출석
55. **음수 포인트 복합 결제 악용** (`POINTS_NEGATIVE_EXPLOIT`) - pointsUsed 음수 주입 잔액 부당 증식
56. **재고 초과 판매 레이스 컨디션** (`STOCK_RACE_CONDITION`) - 재고 1개 상품 동시 다중 결제

### WSTG-INPV (추가): SSRF 및 파일 처리 (2개)
57. **재입고 알림 콜백 Webhook SSRF** (`RESTOCK_WEBHOOK_SSRF`) - 재입고 Webhook URL 내부망 조회

### WSTG-INPV (추가): 멤버십 및 환불 스토어드 XSS (2개)
58. **VIP 프로필 환영글 Stored XSS** (`MEMBERSHIP_STORED_XSS`) - 소개글 innerHTML 주입
59. **환불 사유 및 메모 Stored XSS** (`REFUND_STORED_XSS`) - 관리자 포털 및 주문서 XSS

### WSTG-INFO & CONF: 정보 노출 및 WAF 설정 (2개)
60. **엔터프라이즈 WAF 우회** (`WAF_BYPASS`) - X-Forwarded-For 및 X-Admin-Role 스푸핑
61. **민감정보 노출** (`INFO_EXPOSURE`) - `.env`, `.git/HEAD`, `backup.sql` 유출

---

## 5. 실시간 스코어보드 & CTF 플래그 시스템

---

## 4. 실시간 스코어보드 & CTF 플래그 시스템

Vuln-Mall은 사용자가 취약점을 성공적으로 익스플로잇할 때 즉각적인 피드백을 제공받을 수 있도록 내장 스코어보드를 지원합니다.

- **스코어보드 대시보드 URL**: `http://localhost:8080/scoreboard.html` (별도 브라우저 탭에서 접속 권장)
- **플래그 형식**: `FLAG{VULN_KEY_XXXXXXXX}`
- **WebSocket OOB 자동 감지**: 취약점 공격 요청이 발생하면 백엔드가 내부적으로 이를 감지하고, HTTP 응답을 오염시키지 않는 독립된 **WebSocket Out-Of-Band 채널**(`/ws/scoreboard`)을 통해 스코어보드 대시보드로 실시간 플래그를 푸시합니다. DAST 진단 도구에 힌트나 플래그가 노출되지 않으므로 순수한 벤치마크 평가가 가능합니다.
- **진척도 초기화**: 스코어보드 화면의 `발견 기록 초기화` 버튼을 클릭하면 즉시 모든 취약점 상태가 리셋되어 반복 훈련 및 벤치마크를 수행할 수 있습니다.

---

## 5. 디렉터리 구조

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
└── README.md
```

---

## 6. 법적 고지 (Disclaimer)

본 소프트웨어는 **보안 교육, 공인된 모의해킹 훈련, 보안 취약점 연구 및 DAST 진단 도구 벤치마크 평가**를 위한 목적으로 제작되었습니다. 

본 프로젝트에 포함된 취약점 코드 및 기법을 사전 인가받지 않은 실제 정보통신망이나 타인의 시스템에 적용하는 행위는 관련 법령(정보통신망 이용촉진 및 정보보호 등에 관한 법률 등)에 따라 형사 처벌의 대상이 될 수 있습니다. 제작자는 사용자의 오용 및 불법적 활용으로 인해 발생하는 일체의 민·형사상 책임을 지지 않습니다.

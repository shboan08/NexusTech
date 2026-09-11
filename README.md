# NEXUS TECH (vuln-mall)
> OWASP WSTG 기반 엔터프라이즈 취약점 테스트베드 및 DAST 벤치마크 타깃 시스템

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://www.oracle.com/java/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Supported-2496ED)](https://www.docker.com/)
[![OWASP WSTG](https://img.shields.io/badge/OWASP-WSTG_Aligned-red)](https://owasp.org/www-project-web-security-testing-guide/)

NEXUS TECH는 상용 이커머스 서비스(하이엔드 테크 하드웨어 쇼핑몰)의 구조와 사용자 인터페이스를 갖춘 웹 애플리케이션입니다. 

내부적으로는 동적 애플리케이션 보안 점검(DAST) 엔진 평가, 웹 취약점 진단, 모의해킹 훈련을 목적으로 **OWASP Web Security Testing Guide(WSTG) 기준 36개의 고난도 보안 취약점 및 비즈니스 로직 결함**이 의도적으로 설계되어 있습니다.

각 취약점을 성공적으로 트리거하면 고유한 **CTF 플래그(`FLAG{...}`)가 발급**되며, **실시간 스코어보드([/scoreboard.html](http://localhost:8080/scoreboard.html))**에 즉시 발견 처리됩니다. 언제든지 '발견 기록 초기화' 버튼을 통해 진척도를 리셋하고 반복 훈련할 수 있습니다.

각 취약점의 상세 검증 절차(curl 명령어, 페이로드, 기대 응답)는 [WALKTHROUGH.md](./WALKTHROUGH.md)를 참조하십시오.

---

## 1. 시스템 요구사항 및 빠른 실행

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

## 2. 기본 계정 정보

| 계정명 (Username) | 비밀번호 (Password) | 권한 (Role) | 초기 잔액 | 비고 |
| :--- | :--- | :--- | :--- | :--- |
| **admin** | `admin123` | `ADMIN` | ₩9,999,999 | 관리자 계정 (전체 회원 및 주문 열람) |
| **alice** | `alice123` | `USER` | ₩250,000 | 일반 회원 |
| **bob** | `bob123` | `USER` | ₩150,000 | 일반 회원 |
| **victim** | `pass1234` | `USER` | ₩500,000 | VIP 고객 (BOLA / IDOR 검증 타깃) |

---

## 3. 취약점 매트릭스 (OWASP WSTG 분류 기준, 36개)

### WSTG-INPV: 데이터 검증 및 인젝션 (17개)
1. **SQL Injection - LIKE 문자열 결합**: `GET /api/products?keyword=...`  
   - `LIKE` 절에 사용자 입력을 직접 결합하여 Error-Based / 데이터 추출 가능
2. **SQL Injection - UNION-Based**: `GET /api/products/filter?category=...`  
   - 5개 컬럼 조회 쿼리에 문자열 직접 결합으로 타 테이블(users) 레코드 결합 추출
3. **SQL Injection - ORDER BY Blind**: `GET /api/products?sortBy=...`  
   - `ORDER BY` 절에 동적 결합되어 `CASE WHEN` 서브쿼리 기반 Blind 인젝션 가능
4. **SQL Injection - Boolean-Based Blind**: `GET /api/coupons/verify?code=...`  
   - 에러 스택 은폐 후 `valid: true/false` 논리 신호만을 이용한 1비트씩 데이터 추론
5. **SQL Injection - Time-Based Blind**: `GET /api/orders/track?code=...`  
   - 배송 추적 쿼리에 `SLEEP(n)` 함수 주입을 통한 응답 시간 지연 검증
6. **Second-Order SQL Injection**: `POST /api/inquiries` → `GET /api/inquiries/{id}/audit`  
   - 입력 시점에는 안전하게 저장된 데이터가 감사 로그 쿼리 실행 시 2차 동적 쿼리로 발현
7. **Reflected XSS (HTML 응답)**: `GET /api/support/echo?msg=...`  
   - 사용자 입력값이 `text/html` 응답 본문에 이스케이프 없이 직접 삽입
8. **Stored XSS (리뷰 댓글)**: `POST /api/reviews`  
   - 리뷰 `comment` 필드가 이스케이프 없이 DB에 저장 후 프론트엔드 `innerHTML`로 렌더링
9. **Stored XSS (문의글)**: `POST /api/inquiries`  
   - 문의 `content` 필드가 이스케이프 없이 DB에 저장 후 프론트엔드 `innerHTML`로 렌더링
10. **Path Traversal (임의 파일 읽기)**: `GET /api/files/download?filename=...`  
    - 상위 디렉터리 접근 필터 미흡 및 절대 경로 대체 시도로 시스템 파일 유출
11. **XML External Entity (XXE)**: `POST /api/orders/xml-receipt`  
    - `DocumentBuilderFactory` 기본 설정(외부 엔티티 비활성화 안 함)으로 서버 파일 읽기
12. **Server-Side Template Injection (SpEL SSTI)**: `POST /api/orders/receipt/template`  
    - Spring Expression Language 동적 평가로 산술 연산 및 `Runtime.exec()` 실행
13. **Command Injection (OS 명령 실행)**: `POST /api/util/ping`  
    - `/bin/sh -c "ping -c 2 " + host` 형태로 쉘에 직접 전달되어 세미콜론/파이프 체이닝 가능
14. **SSRF (서버 사이드 요청 위조)**: `POST /api/util/fetch-image`  
    - `localhost`/`127.0.0.1` 문자열 비교만 수행하여 10진수 IP, 16진수 IP, IPv6 맵핑으로 우회
15. **Zip Slip (Archive Directory Traversal)**: `POST /api/files/upload-zip`  
    - ZIP 엔트리 경로의 `../../` 검증 누락으로 상위 디렉터리에 파일 임의 생성
16. **CRLF / Log Injection**: `POST /api/support/log`  
    - `\r\n` 개행 문자 미필터링으로 서버 로그 파일 내 허위 감사 로그 삽입
17. **무제한 파일 업로드 (Unrestricted File Upload)**: `POST /api/files/upload`  
    - 확장자 화이트리스트 검증 없이 HTML, SVG, JSP 등 스크립트 파일 그대로 저장 및 실행 가능

### WSTG-CRYP: 암호학적 취약점 (2개)
18. **AES-ECB 모드 블록 셔플링**: `POST /api/auth/crypto/remember-me`  
    - 초기화 벡터(IV)가 없는 16바이트 블록 암호화로 블록 재배치를 통한 권한(`role=ADMIN`) 조작
19. **취약한 비밀번호 재설정 토큰**: `POST /api/auth/crypto/forgot-password-link`  
    - `MD5(username + 시간초)` 기반 토큰 생성으로 인한 예측 및 무차별 대입 공격 허용

### WSTG-ATHN & ATHZ: 인증 및 인가 통제 (7개)
20. **JWT Algorithm Confusion (Key Confusion)**: `POST /api/auth/login` → `JwtTokenProvider`  
    - 서버의 RSA 공개키(PEM)를 HMAC(HS256) 대칭키로 오인하는 알고리즘 혼동 결함
21. **계정 열거 (Username Enumeration)**: `POST /api/auth/login`  
    - 아이디 유무에 따른 차별화된 에러 메시지(`UserNotFound` vs `BadCredentials`) 반환
22. **Mass Assignment (프로필 업데이트 권한 상승)**: `PUT /api/auth/profile`  
    - 요청 본문의 `role`, `balance` 필드를 검증 없이 수용하여 일반 사용자의 ADMIN 승격 허용
23. **Mass Assignment (회원가입 시 권한 지정)**: `POST /api/auth/register`  
    - 가입 요청에 `role: "ADMIN"`, `balance: 99999999` 전송 시 그대로 적용
24. **BOLA / IDOR (타인 주문 조회)**: `GET /api/orders/{id}`  
    - 인증 여부만 확인하고 주문 소유권 검증 누락으로 타 고객의 기밀 배송 정보 열람
25. **BOLA / IDOR (타인 배송지 변경)**: `PUT /api/orders/{id}/shipping`  
    - 소유자 확인 없이 임의 주문의 수령인, 배송 주소, 연락처 무단 변경
26. **HTTP Parameter Pollution (HPP)**: `POST /api/orders/{id}/status-update`  
    - 동일 파라미터 복수 전송 시 프레임워크 바인딩 특성(마지막 인자 수용)을 악용한 상태 조작

### WSTG-SESS & CLNT: 세션 및 클라이언트 취약점 (3개)
27. **Cross-Site Request Forgery (CSRF)**: `GET/POST /api/auth/change-email`  
    - GET 요청만으로 타인의 이메일 변경 가능, CSRF 토큰 검증 부재
28. **DOM-Based XSS**: `http://localhost:8080/#notice=...`  
    - 클라이언트 스크립트가 URL 해시 파라미터를 `innerHTML`에 직접 삽입
29. **Open Redirect**: `GET /api/auth/redirect?url=...`  
    - `vulnmall.local` 문자열 포함 여부만 단순 검사하여 외부 악성 사이트 리다이렉트 허용

### WSTG-BUSL: 비즈니스 로직 결함 (5개)
30. **Price Tampering (가격 조작)**: `POST /api/orders`  
    - 클라이언트가 전달한 결제 금액(`totalAmount`)을 서버가 재계산 없이 신뢰
31. **Negative Quantity (음수 수량)**: `PUT /api/cart/update/{id}`  
    - 음수 수량 검증 부재로 장바구니 총액이 마이너스가 되어 결제 시 잔액 증가
32. **부동소수점 오차 포인트 차익거래**: `POST /api/points/exchange`  
    - 정수 변환 및 소수점 절삭 오류(`(long)0.9 = 0`)로 잔액 차감 없이 무한 포인트 획득
33. **동시성 Race Condition**: `POST /api/coupons/redeem`  
    - DB 트랜잭션 락 부재(TOCTOU)로 동일 할인 쿠폰을 다중 스레드로 동시 중복 사용
34. **Workflow Step Skipping**: `POST /api/orders/{id}/direct-confirm`  
    - 인가 및 결제 검증 없이 내부 결제 승인 엔드포인트 직접 호출로 주문 상태 즉시 변경

### WSTG-INFO & CONF: 정보 노출 및 관리자 설정 (2개)
35. **엔터프라이즈 WAF 우회**: `X-Forwarded-For: 127.0.0.1`, `X-Custom-IP-Authorization: 127.0.0.1`  
    - 클라이언트 제공 프록시 헤더를 신뢰하여 관리자 API(`/api/admin/users`) 비인가 접근 허용
36. **민감정보 노출**: `/.git/HEAD`, `/.env`, `/backup.sql`  
    - DB 접속 정보, JWT 시크릿, AWS 키, 관리자 계정 평문 비밀번호 등 핵심 인증 정보 노출

---

## 4. 실시간 스코어보드 & CTF 플래그 시스템

Vuln-Mall은 사용자가 취약점을 성공적으로 익스플로잇할 때 즉각적인 피드백을 제공받을 수 있도록 내장 스코어보드를 지원합니다.

- **스코어보드 대시보드 URL**: `http://localhost:8080/scoreboard.html`
- **플래그 형식**: `FLAG{VULN_KEY_XXXXXXXX}`
- **자동 감지**: 취약점 공격 요청 발생 시 백엔드가 자동으로 해당 취약점을 찾아내고 응답 헤더(`X-Vuln-Flag`)에 플래그를 실어 보냅니다.
- **초기화**: 스코어보드 우측 상단의 `발견 기록 초기화` 버튼(또는 `POST /api/scoreboard/reset`)을 클릭하면 즉시 모든 발견 상태가 초기화됩니다.

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

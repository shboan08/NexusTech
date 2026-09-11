# NEXUS TECH (vuln-mall)
> OWASP WSTG 기반 엔터프라이즈 취약점 테스트베드 및 DAST 벤치마크 타깃 시스템

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://www.oracle.com/java/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Supported-2496ED)](https://www.docker.com/)
[![OWASP WSTG](https://img.shields.io/badge/OWASP-WSTG_Aligned-red)](https://owasp.org/www-project-web-security-testing-guide/)

NEXUS TECH는 상용 이커머스 서비스(하이엔드 테크 하드웨어 쇼핑몰)의 구조와 사용자 인터페이스를 갖춘 웹 애플리케이션입니다. 

내부적으로는 동적 애플리케이션 보안 점검(DAST) 엔진 평가, 웹 취약점 진단, 모의해킹 훈련을 목적으로 **OWASP Web Security Testing Guide(WSTG) 기준 25개 이상의 고난도 보안 취약점 및 비즈니스 로직 결함**이 의도적으로 설계되어 있습니다.

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

## 3. 취약점 매트릭스 (OWASP WSTG 분류 기준)

### WSTG-INPV: 데이터 검증 및 인젝션
1. **Error-Based SQL Injection**: `GET /api/products?keyword=...`  
   - 복합 `LIKE` 조건문 괄호 불일치 시 500 에러 스택 트레이스에 SQL 구조 노출
2. **Union-Based SQL Injection**: `GET /api/products/filter?category=...`  
   - 5개 컬럼 조회 쿼리에 문자열을 직접 결합하여 타 테이블(users) 레코드 결합 추출
3. **Boolean-Based Blind SQL Injection**: `GET /api/coupons/verify?code=...`  
   - 에러 스택 은폐 후 `valid: true/false` 논리 신호만을 이용한 1비트씩 데이터 추론
4. **Time-Based Blind SQL Injection**: `GET /api/orders/track?code=...`  
   - 배송 추적 쿼리에 `SLEEP(n)` 함수 주입을 통한 시간 지연 검증
5. **Second-Order SQL Injection**: `POST /api/inquiries` $\to$ `GET /api/inquiries/{id}/audit`  
   - 입력 시점에는 안전하게 저장된 데이터가 관리자 감사 쿼리 실행 시 2차 동적 쿼리로 발현
6. **Reflected XSS**: `GET /api/support/echo?msg=...`  
   - 사용자 입력값이 HTML 본문에 필터링 없이 리플렉션
7. **Stored XSS**: `POST /api/inquiries` (Q&A 게시판), `POST /api/reviews` (상품 리뷰)  
   - DB에 영속화된 스크립트가 타 사용자 상세 화면에 비이스케이프 렌더링
8. **Zip Slip (Archive Directory Traversal)**: `POST /api/files/upload-zip`  
   - ZIP 아카이브 해제 시 상대 경로(`../../`) 검증 누락으로 상위 디렉터리 파일 임의 생성
9. **Server-Side Template Injection (SpEL SSTI)**: `POST /api/orders/receipt/template`  
   - 전자 영수증 안내 문구 템플릿 처리 시 Spring Expression Language 동적 평가 허용
10. **CRLF / Log Injection**: `POST /api/support/log`  
    - `\r\n` 개행 문자 미필터링으로 서버 로그 파일 내 허위 감사 로그 삽입

### WSTG-CRYP: 암호학적 취약점
1. **AES-ECB 모드 블록 셔플링**: `POST /api/auth/crypto/remember-me`  
   - 초기화 벡터(IV)가 없는 16바이트 블록 암호화로 블록 재배치를 통한 권한(`role=ADMIN`) 조작
2. **취약한 비밀번호 재설정 토큰**: `POST /api/auth/crypto/forgot-password-link`  
   - `MD5(username + 시간초)` 기반 토큰 생성으로 인한 무차별 대입 및 예측 공격 허용

### WSTG-ATHN & ATHZ: 인증 및 인가 통제
1. **JWT Algorithm Confusion (Key Confusion)**: `POST /api/auth/login` $\to$ `JwtTokenProvider`  
   - 서버의 RSA 공개키(PEM)를 HMAC(HS256) 대칭키로 오인하는 알고리즘 혼동 결함
2. **계정 열거 (Username Enumeration)**: `POST /api/auth/login`  
   - 아이디 유무에 따른 차별화된 에러 메시지 반환
3. **HTTP Parameter Pollution (HPP)**: `POST /api/orders/{id}/status-update`  
   - 동일 파라미터 복수 전송 시 프레임워크 바인딩 특성(마지막 인자 수용)을 악용한 상태 조작
4. **Path Traversal (임의 파일 다운로드)**: `GET /api/files/download?filename=...`  
   - 상위 디렉터리 접근 필터 미흡으로 `application.yml` 등 시스템 설정 파일 유출
5. **BOLA / IDOR**: `GET /api/orders/{id}`, `PUT /api/orders/{id}/shipping`  
   - 객체 수준 소유권 검증 부재로 타 고객의 기밀 배송 정보 및 연락처 무단 열람/수정

### WSTG-SESS & CLNT: 세션 및 클라이언트 취약점
1. **Cross-Site Request Forgery (CSRF)**: `POST /api/auth/change-email`  
   - CSRF 토큰 검증 부재로 외부 페이지를 통한 회원 이메일 강제 변조
2. **DOM-Based XSS**: `http://localhost:8080/#notice=...`  
   - 클라이언트 스크립트가 URL 해시 파라미터를 읽어 DOM `innerHTML`에 직접 삽입
3. **Open Redirect**: `GET /api/auth/redirect?url=...`  
   - 미흡한 도메인 정규식 검증 우회로 외부 악성 사이트 리다이렉트 허용

### WSTG-BUSL: 비즈니스 로직 결함
1. **Price Tampering (가격 조작)**: `POST /api/orders`  
   - 클라이언트가 전달한 결제 금액(`totalAmount`)을 서버가 재계산 없이 신뢰
2. **부동소수점 오차 포인트 차익거래**: `POST /api/points/exchange`  
   - 정수 변환 및 소수점 절삭 오류로 잔액 차감 없이 무한 포인트 획득 가능
3. **동시성 Race Condition**: `POST /api/coupons/redeem`  
   - DB 트랜잭션 락 부재(TOCTOU)로 동일 할인 쿠폰을 다중 스레드로 동시 중복 사용
4. **Workflow Step Skipping**: `POST /api/orders/{id}/direct-confirm`  
   - 단계 검증 없이 내부 결제 승인 엔드포인트 직접 호출 가능

### WSTG-INFO & CONF: 정보 노출 및 관리자 설정
1. **엔터프라이즈 WAF 우회**: `X-Forwarded-For: 127.0.0.1`  
   - 신뢰할 수 없는 클라이언트 헤더를 신뢰하여 관리자 API(`/api/admin/users`) 비인가 접근 허용
2. **형상관리 및 백업 파일 노출**: `/.git/HEAD`, `/.env`, `/backup.sql`  
   - 웹 루트에 방치된 설정 파일 및 데이터베이스 덤프 노출
3. **Spring Actuator 정보 노출**: `/actuator/env`, `/actuator/heapdump`, `/actuator/beans`

---

## 4. 디렉터리 구조

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

## 5. 법적 고지 (Disclaimer)

본 소프트웨어는 **보안 교육, 공인된 모의해킹 훈련, 보안 취약점 연구 및 DAST 진단 도구 벤치마크 평가**를 위한 목적으로 제작되었습니다. 

본 프로젝트에 포함된 취약점 코드 및 기법을 사전 인가받지 않은 실제 정보통신망이나 타인의 시스템에 적용하는 행위는 관련 법령(정보통신망 이용촉진 및 정보보호 등에 관한 법률 등)에 따라 형사 처벌의 대상이 될 수 있습니다. 제작자는 사용자의 오용 및 불법적 활용으로 인해 발생하는 일체의 민·형사상 책임을 지지 않습니다.

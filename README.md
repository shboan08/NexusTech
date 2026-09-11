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
1. **SQL Injection - LIKE 문자열 결합**  
   - 사용자 입력값에 대한 파라미터 바인딩 부재로 인한 LIKE 절 구문 조작 및 데이터 유출
2. **SQL Injection - UNION-Based**  
   - 동적 쿼리 내 컬럼 결합 결함으로 타 테이블(회원 등) 레코드 통합 조회
3. **SQL Injection - ORDER BY Blind**  
   - 동적 정렬 구문에 사용자 입력을 결합하여 CASE WHEN 조건부 참/거짓 기반 데이터 추출
4. **SQL Injection - Boolean-Based Blind**  
   - 에러 스택 은폐 환경에서 서버의 논리적 조건 판별(True/False) 신호를 통한 1비트 블라인드 데이터 추론
5. **SQL Injection - Time-Based Blind**  
   - 시간 지연 함수(SLEEP) 주입을 통한 응답 대기 시간 기반 블라인드 인젝션
6. **Second-Order SQL Injection**  
   - 저장 시점에는 안전하게 적재된 데이터가 후속 비즈니스(감사 로그) 조회 시 동적 쿼리로 재실행
7. **Reflected XSS (HTML 응답)**  
   - 사용자 입력값이 HTML 본문에 적절한 이스케이프 없이 반사되어 브라우저 스크립트 실행
8. **Stored XSS (리뷰 댓글)**  
   - 악성 스크립트가 DB에 영구 저장된 후 클라이언트 렌더링 시 여과 없이 실행
9. **Stored XSS (고객 문의)**  
   - 문의 본문이 DB에 저장된 후 프론트엔드 렌더링 과정에서 스크립트 실행
10. **Path Traversal (임의 파일 다운로드)**  
    - 경로 이동 문자(`../`) 검증 미흡으로 웹 루트 외부의 시스템 파일 유출
11. **XML External Entity (XXE)**  
    - XML 파서의 외부 엔티티(DOCTYPE) 비활성화 누락으로 서버 내부 파일 참조 및 SSRF 유발
12. **Server-Side Template Injection (SpEL SSTI)**  
    - 표현식 언어(SpEL) 동적 평가 취약점을 악용한 서버 사이드 임의 코드 및 명령어 실행
13. **Command Injection (OS 명령 실행)**  
    - 시스템 명령어 실행 인자에 대한 검증 및 파이프/세미콜론 메타문자 체이닝 결함
14. **SSRF (서버 사이드 요청 위조)**  
    - URL 필터링 우회(진법 변환, 로컬호스트 우회 등)를 통한 내부 인프라 비인가 HTTP 요청
15. **Zip Slip (Archive Directory Traversal)**  
    - 압축 해제 시 아카이브 엔트리의 상대 경로 검증 누락으로 상위 디렉터리 임의 파일 덮어쓰기
16. **CRLF / Log Injection**  
    - 개행 문자(`\r\n`) 미필터링으로 인한 시스템 감사 로그 위변조 및 스플리팅
17. **무제한 파일 업로드 (Unrestricted File Upload)**  
    - 확장자 화이트리스트 검증 부재로 임의 악성 스크립트 파일 업로드 허용

### WSTG-CRYP: 암호학적 취약점 (2개)
18. **AES-ECB 모드 블록 셔플링**  
    - 초기화 벡터(IV) 없는 고정 블록 암호화 특성을 악용한 암호문 블록 치환 및 권한 조작
19. **취약한 비밀번호 재설정 토큰**  
    - 예측 가능한 시드(타임스탬프, 취약한 단방향 해시) 기반 토큰 생성으로 인한 무차별 대입 및 가로채기

### WSTG-ATHN & ATHZ: 인증 및 인가 통제 (7개)
20. **JWT Algorithm Confusion (Key Confusion)**  
    - 서버 비대칭 공개키(RSA PEM)를 HMAC(대칭키) 검증 키로 오인하는 알고리즘 혼동 결함
21. **계정 열거 (Username Enumeration)**  
    - 로그인 실패 시 아이디 존재 유무에 따른 차별적 에러 메시지 노출
22. **Mass Assignment (프로필 업데이트 권한 상승)**  
    - 바인딩 객체의 민감 필드(`role` 등)에 대한 화이트리스트 검증 부재로 일반 사용자 권한 승격
23. **Mass Assignment (회원가입 시 권한 지정)**  
    - 가입 요청 모델의 검증 누락으로 관리자 권한 및 비정상 잔액을 부여한 계정 생성
24. **BOLA / IDOR (타인 주문 조회)**  
    - 식별자 기반 리소스 조회 시 현재 인증된 사용자와의 소유권 검증 누락
25. **BOLA / IDOR (타인 배송지 변경)**  
    - 리소스 수정 시 접근 제어 검증 부재로 타 고객의 기밀 정보 및 주문 배송지 무단 조작
26. **HTTP Parameter Pollution (HPP)**  
    - 동일 파라미터 복수 전송 시 프레임워크의 파라미터 파싱 특성을 악용한 비즈니스 상태 조작

### WSTG-SESS & CLNT: 세션 및 클라이언트 취약점 (3개)
27. **Cross-Site Request Forgery (CSRF)**  
    - 상태 변경 요청에 대한 안티 CSRF 토큰 부재로 희생자의 의도치 않은 계정 정보 변경
28. **DOM-Based XSS**  
    - 클라이언트 스크립트에서 안전하지 않은 DOM 싱크(innerHTML)로 사용자 제어 소스 직접 전달
29. **Open Redirect**  
    - 리다이렉트 목적지 URL에 대한 불충분한 도메인 화이트리스트 검증으로 악성 사이트 피싱 유도

### WSTG-BUSL: 비즈니스 로직 결함 (5개)
30. **Price Tampering (결제 금액 변조)**  
    - 클라이언트가 전송한 결제 금액을 서버 사이드에서 장바구니 실단가와 대조 없이 신뢰
31. **Negative Quantity (음수 수량 조작)**  
    - 품목 수량에 음수 값 검증 누락으로 장바구니 총액 감액 및 비정상 결제 승인
32. **부동소수점 오차 포인트 차익거래**  
    - 정수 캐스팅 시 소수점 절삭(Rounding Error) 오류를 악용한 무한 포인트 취득
33. **동시성 Race Condition**  
    - 트랜잭션 락 부재(TOCTOU 결함)로 1회용 프로모션 쿠폰의 다중 스레드 동시 중복 사용
34. **Workflow Step Skipping (단계 건너뛰기)**  
    - 주문 및 결제 라이프사이클 절차를 거치지 않고 중간 승인 단계를 직접 호출하여 주문 상태 변경

### WSTG-INFO & CONF: 정보 노출 및 관리자 설정 (2개)
35. **엔터프라이즈 WAF 우회 (프록시 헤더 신뢰)**  
    - 클라이언트가 위조한 역방향 프록시 헤더를 검증 없이 신뢰하여 관리자 영역 인가 통제 우회
36. **민감정보 노출 (백업 파일 및 버전 관리 아티팩트)**  
    - 웹 루트 상에 배포된 불필요한 설정 파일, 소스코드 저장소 메타데이터, DB 덤프 파일 유출

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

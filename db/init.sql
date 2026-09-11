-- vuln-mall Database Initialization Script
CREATE DATABASE IF NOT EXISTS vulnmall CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE vulnmall;

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    role VARCHAR(20) DEFAULT 'USER',
    balance DECIMAL(12, 2) DEFAULT 100000.00,
    avatar_url VARCHAR(255) DEFAULT '/uploads/default-avatar.png',
    security_question VARCHAR(255) DEFAULT '어린 시절 가장 기억에 남는 장소는?',
    security_answer VARCHAR(255) DEFAULT '서울',
    reset_token VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Products Table
CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(50) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 50,
    description TEXT,
    image_url VARCHAR(255),
    manual_filename VARCHAR(100) DEFAULT 'manual_sample.pdf',
    is_hidden BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Cart Items Table
CREATE TABLE IF NOT EXISTS cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- 4. Orders Table
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(12, 2) NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    shipping_address TEXT NOT NULL,
    phone VARCHAR(30) NOT NULL,
    status VARCHAR(30) DEFAULT 'PAID',
    tracking_code VARCHAR(100) DEFAULT 'LOGI-KR-998822',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 5. Order Items Table
CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(150),
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

-- 6. Reviews Table
CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    rating INT DEFAULT 5,
    comment TEXT NOT NULL,
    image_path VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 7. Coupons Table
CREATE TABLE IF NOT EXISTS coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    discount_amount DECIMAL(10, 2) NOT NULL,
    is_used BOOLEAN DEFAULT FALSE
);

-- 8. Inquiries Table
CREATE TABLE IF NOT EXISTS inquiries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_secret BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 9. Audit Logs Table
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(100),
    username VARCHAR(100),
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==========================================================
-- SEED DATA
-- ==========================================================

-- Default Users (Pass: admin123, user123, guest123)
-- Admin & normal users for authentication & IDOR testing
INSERT INTO users (username, password, email, role, balance, security_question, security_answer) VALUES
('admin', 'admin123', 'admin@vulnmall.local', 'ADMIN', 9999999.00, '최초 개설 지점명은?', '본점'),
('alice', 'alice123', 'alice@example.com', 'USER', 250000.00, '어린 시절 가장 기억에 남는 장소는?', '제주도'),
('bob', 'bob123', 'bob@example.com', 'USER', 150000.00, '가장 좋아하는 영화는?', '매트릭스'),
('victim', 'pass1234', 'victim@secure-corp.com', 'USER', 500000.00, '보물 1호는?', '가족사진');

-- Products Seed (Tech & Cyber Gadgets)
INSERT INTO products (name, category, price, stock, description, image_url, manual_filename, is_hidden) VALUES
('Quantum Cyber Deck X1', 'Laptops', 1890000.00, 15, '차세대 양자 암호화 프로세서가 탑재된 하이엔드 사이버 덱입니다. 전문가용 터미널 인터페이스 탑재.', 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600&auto=format&fit=crop', 'quantum_x1_spec.pdf', FALSE),
('Neural Link Headset Pro', 'Wearables', 620000.00, 30, '초저지연 뇌파 인터페이스 및 생체 신호 모니터링 기능이 내장된 프리미엄 스마트 헤드셋.', 'https://images.unsplash.com/photo-1546435770-a3e426bf472b?w=600&auto=format&fit=crop', 'neural_headset_v2.pdf', FALSE),
('Hacker Mechanical Keyboard RGB', 'Accessories', 145000.00, 80, '핫스왑 커스텀 기계식 키보드. 프로그래밍 가능한 매크로 키 및 사이버펑크 RGB 백라이트 지원.', 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=600&auto=format&fit=crop', 'keyboard_firmware.pdf', FALSE),
('Zero-Trust Stealth Phone Z9', 'Smartphones', 1350000.00, 12, '하드웨어 킬스위치 및 분산 스토리지 아키텍처가 적용된 프라이버시 전용 플래그십 스마트폰.', 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=600&auto=format&fit=crop', 'stealth_phone_guide.pdf', FALSE),
('Tactical Cyber Drone Phantom', 'Drones', 890000.00, 22, '열화상 카메라 및 장거리 자동 비행 경로 지원. 자율 비행 메쉬 네트워크 기능 탑재.', 'https://images.unsplash.com/photo-1527977966376-1c8408f9f108?w=600&auto=format&fit=crop', 'drone_manual.pdf', FALSE),
('Titan Encrypted Cold Storage 2TB', 'Storage', 280000.00, 50, '군사 등급 AES-256 하드웨어 암호화 및 자폭 방지 센서가 내장된 알루미늄 외장 SSD.', 'https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?w=600&auto=format&fit=crop', 'titan_ssd_security.pdf', FALSE),
('HoloLens Augmented Glasses', 'Wearables', 780000.00, 18, '증강 현실 실시간 HUD 및 공간 인식 센서가 탑재된 차세대 스마트 안경.', 'https://images.unsplash.com/photo-1572635196237-14b3f281503f?w=600&auto=format&fit=crop', 'hololens_quickstart.pdf', FALSE),
('DeepLearning Edge Server Box', 'Servers', 3400000.00, 5, '로컬 AI 추론을 위한 초소형 쿼드 GPU 엣지 서버. 실시간 영상 분석 및 네트워크 침입 탐지.', 'https://images.unsplash.com/photo-1558494949-ef010cbdcc31?w=600&auto=format&fit=crop', 'edge_server_admin.pdf', FALSE),
('RESTRICTED: Government Exploit Kit v3.1', 'Security', 99999999.00, 1, '[SECRET] 내부 비공개 품목. 관계자 외 열람 및 구매 금지.', 'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=600&auto=format&fit=crop', 'classified_exploit.pdf', TRUE);

-- Reviews Seed (Stored XSS demonstrations / realistic reviews)
INSERT INTO reviews (product_id, user_id, username, rating, comment) VALUES
(1, 2, 'alice', 5, '키보드 타건감과 덱 성능이 정말 뛰어납니다. 컴파일 속도가 3배는 빨라졌어요!'),
(1, 3, 'bob', 4, '배터리가 조금 빨리 닳지만 전반적으로 만족스럽습니다.'),
(3, 2, 'alice', 5, 'RGB 조명이 너무 예쁩니다. 개발할 때 기분이 좋아져요!'),
(2, 4, 'victim', 5, '노이즈 캔슬링과 생체 신호 피드백이 환상적입니다.');

-- Coupons Seed
INSERT INTO coupons (code, discount_amount, is_used) VALUES
('WELCOME2026', 10000.00, FALSE),
('VIP_CYBER_50K', 50000.00, FALSE),
('SUPER_SALE_90', 90000.00, FALSE);

-- Pre-seeded Orders for IDOR testing
INSERT INTO orders (id, user_id, total_amount, recipient_name, shipping_address, phone, status, tracking_code) VALUES
(1, 2, 1890000.00, '앨리스 (Alice)', '서울특별시 강남구 테헤란로 152 강남파이낸스센터 12층', '010-1234-5678', 'DELIVERED', 'KR-LOGI-88219'),
(2, 4, 1350000.00, '김피해 (VIP)', '경기도 성남시 분당구 판교역로 235 비밀연구소 702호 [기밀 배송]', '010-9999-8888', 'SHIPPED', 'KR-LOGI-77192');

INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price) VALUES
(1, 1, 'Quantum Cyber Deck X1', 1, 1890000.00),
(2, 4, 'Zero-Trust Stealth Phone Z9', 1, 1350000.00);

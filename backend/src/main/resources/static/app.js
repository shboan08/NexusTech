/**
 * NEXUS TECH Frontend Application Logic
 */
const app = {
    token: localStorage.getItem('nexus_token') || null,
    currentUser: null,
    currentCategory: 'ALL',
    currentKeyword: '',
    sortBy: 'id',
    sortOrder: 'ASC',
    cart: [],
    discount: 0,

    init() {
        this.setupAuth();
        this.loadProducts();
        this.checkAuthStatus();
        this.checkUrlParams();
    },

    setupAuth() {
        if (this.token) {
            this.fetchProfile();
            this.loadCart();
        } else {
            this.updateNavUI();
        }
    },

    checkUrlParams() {
        const params = new URLSearchParams(window.location.search);
        const search = params.get('search');
        if (search) {
            document.getElementById('searchInput').value = search;
            this.currentKeyword = search;
            this.loadProducts();
        }

        const hash = window.location.hash;
        if (hash && hash.startsWith('#notice=')) {
            const raw = decodeURIComponent(hash.substring(8));
            const bar = document.getElementById('globalNotice');
            if (bar) {
                bar.style.display = 'block';
                bar.innerHTML = '🔔 ' + raw;
            }
        }
    },

    async fetchWithAuth(url, options = {}) {
        options.headers = options.headers || {};
        if (this.token) {
            options.headers['Authorization'] = `Bearer ${this.token}`;
        }
        if (!options.headers['Content-Type'] && !(options.body instanceof FormData)) {
            options.headers['Content-Type'] = 'application/json';
        }
        return await fetch(url, options);
    },

    updateNavUI() {
        const navAuth = document.getElementById('navAuthSection');
        const navLogged = document.getElementById('navUserLogged');

        if (this.currentUser) {
            navAuth.style.display = 'none';
            navLogged.style.display = 'flex';
            document.getElementById('navUsername').textContent = this.currentUser.username;
            document.getElementById('navUserBalance').textContent = `₩${Number(this.currentUser.balance).toLocaleString()}`;
        } else {
            navAuth.style.display = 'flex';
            navLogged.style.display = 'none';
        }
    },

    async checkAuthStatus() {
        if (this.token) {
            await this.fetchProfile();
        }
    },

    async fetchProfile() {
        try {
            const res = await this.fetchWithAuth('/api/auth/profile');
            if (res.ok) {
                this.currentUser = await res.json();
                this.updateNavUI();
            } else {
                this.logout();
            }
        } catch (e) {
            console.error(e);
        }
    },

    // ==========================================
    // Product Catalog
    // ==========================================
    async loadProducts() {
        const grid = document.getElementById('productGrid');
        grid.innerHTML = '<div class="text-muted">최신 장비 목록을 로드하는 중...</div>';

        let url = `/api/products?sortBy=${encodeURIComponent(this.sortBy)}&sortOrder=${this.sortOrder}`;
        if (this.currentCategory && this.currentCategory !== 'ALL') {
            url += `&category=${encodeURIComponent(this.currentCategory)}`;
        }
        if (this.currentKeyword) {
            url += `&keyword=${encodeURIComponent(this.currentKeyword)}`;
        }

        try {
            const res = await fetch(url);
            const data = await res.json();

            // 검색 결과 배너에 사용자 검색어 표시
            const banner = document.getElementById('searchBanner');
            if (this.currentKeyword) {
                banner.style.display = 'flex';
                banner.innerHTML = `<span><strong>검색 결과:</strong> "${this.currentKeyword}" (${data.totalCount}건 발견)</span>
                                    <button class="btn btn-ghost" onclick="app.clearSearch()">초기화</button>`;
            } else {
                banner.style.display = 'none';
            }

            if (!data.products || data.products.length === 0) {
                grid.innerHTML = '<div class="text-muted" style="grid-column: span 3; padding: 40px 0;">조건에 맞는 상품이 없습니다.</div>';
                return;
            }

            grid.innerHTML = data.products.map(p => `
                <div class="product-card" onclick="app.openProductModal(${p.id})">
                    <div class="product-img-wrapper">
                        <img src="${p.imageUrl || 'https://images.unsplash.com/photo-1550751827-4bd374c3f58b?w=600'}" alt="${p.name}">
                        <span class="product-cat-tag">${p.category}</span>
                    </div>
                    <div class="product-info">
                        <h3 class="product-title">${p.name}</h3>
                        <p class="product-desc-short">${p.description}</p>
                        <div class="product-footer">
                            <span class="product-price">₩${Number(p.price).toLocaleString()}</span>
                            <button class="btn btn-primary" onclick="event.stopPropagation(); app.addToCartQuick(${p.id}, ${p.price})">담기</button>
                        </div>
                    </div>
                </div>
            `).join('');

        } catch (e) {
            grid.innerHTML = `<div class="text-danger">상품을 불러오지 못했습니다: ${e.message}</div>`;
        }
    },

    handleSearch() {
        const input = document.getElementById('searchInput');
        this.currentKeyword = input.value.trim();
        this.loadProducts();
    },

    clearSearch() {
        document.getElementById('searchInput').value = '';
        this.currentKeyword = '';
        this.loadProducts();
    },

    filterCategory(cat) {
        this.currentCategory = cat;
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.category === cat);
        });
        this.loadProducts();
    },

    quickFilter(cat) {
        this.filterCategory(cat);
        this.scrollToProducts();
    },

    resetFilter() {
        this.currentCategory = 'ALL';
        this.clearSearch();
    },

    handleSortChange() {
        this.sortBy = document.getElementById('sortBySelect').value;
        this.sortOrder = document.getElementById('sortOrderSelect').value;
        this.loadProducts();
    },

    scrollToProducts() {
        document.getElementById('catalogSection').scrollIntoView({ behavior: 'smooth' });
    },

    // ==========================================
    // Product Detail & Reviews
    // ==========================================
    async openProductModal(productId) {
        try {
            const res = await fetch(`/api/products/${productId}`);
            if (!res.ok) throw new Error('Product not found');
            const data = await res.json();
            const p = data.product;
            const reviews = data.reviews || [];

            const container = document.getElementById('productDetailContent');
            // 고객 리뷰 코멘트 렌더링
            container.innerHTML = `
                <div class="detail-img-box">
                    <img src="${p.imageUrl}" alt="${p.name}">
                </div>
                <div class="detail-meta">
                    <span class="product-cat-tag">${p.category}</span>
                    <h2 class="mt-3">${p.name}</h2>
                    <p class="detail-price">₩${Number(p.price).toLocaleString()}</p>
                    <p class="detail-desc">${p.description}</p>
                    <div class="detail-actions">
                        <button class="btn btn-primary btn-lg" onclick="app.addToCartQuick(${p.id}, ${p.price})">장바구니 담기</button>
                        <a href="/api/files/download?filename=${p.manualFilename}" class="btn btn-outline btn-lg" download>제품 매뉴얼 다운로드</a>
                    </div>
                </div>
                <div class="reviews-section">
                    <h3>고객 리뷰 & 피드백 (${reviews.length})</h3>
                    <div class="review-form mt-3 mb-3">
                        <form onsubmit="event.preventDefault(); app.submitReview(${p.id});">
                            <div class="form-group">
                                <label>리뷰 작성 (별점 및 제품 사용 소감)</label>
                                <textarea id="reviewCommentInput" rows="3" required placeholder="제품의 성능 및 실제 필드 테스트 경험을 공유해주세요."></textarea>
                            </div>
                            <button type="submit" class="btn btn-primary">리뷰 등록</button>
                        </form>
                    </div>
                    <div class="review-list mt-3">
                        ${reviews.length > 0 ? reviews.map(r => `
                            <div class="review-item">
                                <div class="review-header">
                                    <strong>${r.username}</strong>
                                    <span>${new Date(r.createdAt).toLocaleDateString()}</span>
                                </div>
                                <div class="review-comment">${r.comment}</div>
                            </div>
                        `).join('') : '<p class="text-muted">아직 등록된 리뷰가 없습니다.</p>'}
                    </div>
                </div>
            `;

            this.openModal('productModal');
        } catch (e) {
            alert('상세 정보를 불러올 수 없습니다: ' + e.message);
        }
    },

    async submitReview(productId) {
        if (!this.token) {
            alert('리뷰 작성은 로그인이 필요합니다.');
            this.openAuthModal('login');
            return;
        }

        const comment = document.getElementById('reviewCommentInput').value;
        try {
            const res = await this.fetchWithAuth('/api/reviews', {
                method: 'POST',
                body: JSON.stringify({ productId, rating: 5, comment })
            });

            if (res.ok) {
                this.openProductModal(productId);
            } else {
                const err = await res.json();
                alert(err.message || '리뷰 등록 실패');
            }
        } catch (e) {
            alert('오류 발생: ' + e.message);
        }
    },

    // ==========================================
    // Cart Management
    // ==========================================
    async addToCartQuick(productId, price) {
        if (!this.token) {
            alert('장바구니 기능은 로그인이 필요합니다.');
            this.openAuthModal('login');
            return;
        }

        try {
            const res = await this.fetchWithAuth('/api/cart/add', {
                method: 'POST',
                body: JSON.stringify({ productId, quantity: 1, unitPrice: price })
            });

            if (res.ok) {
                alert('장바구니에 상품을 담았습니다.');
                await this.loadCart();
            } else {
                const err = await res.json();
                alert(err.message || '추가 실패');
            }
        } catch (e) {
            console.error(e);
        }
    },

    async loadCart() {
        if (!this.token) return;
        try {
            const res = await this.fetchWithAuth('/api/cart');
            if (res.ok) {
                const data = await res.json();
                this.cart = data.items || [];
                document.getElementById('cartCountBadge').textContent = this.cart.reduce((acc, item) => acc + item.quantity, 0);
                this.renderCartUI(data.totalAmount || 0);
            }
        } catch (e) {
            console.error(e);
        }
    },

    renderCartUI(subtotal) {
        const list = document.getElementById('cartItemList');
        document.getElementById('cartItemCount').textContent = this.cart.length;

        if (this.cart.length === 0) {
            list.innerHTML = '<div class="text-muted" style="padding: 20px 0;">장바구니가 비어 있습니다.</div>';
            document.getElementById('cartSubtotal').textContent = '₩0';
            document.getElementById('cartFinalTotal').textContent = '₩0';
            return;
        }

        list.innerHTML = this.cart.map(item => `
            <div class="cart-item">
                <img src="${item.productImageUrl || 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=200'}" alt="${item.productName}">
                <div class="cart-item-info">
                    <div class="cart-item-title">${item.productName}</div>
                    <div class="cart-item-price">₩${Number(item.unitPrice).toLocaleString()}</div>
                </div>
                <div class="cart-qty-ctrl">
                    <input type="number" value="${item.quantity}" onchange="app.updateCartQty(${item.id}, this.value)">
                    <button class="btn btn-ghost" onclick="app.deleteCartItem(${item.id})">&times;</button>
                </div>
            </div>
        `).join('');

        const finalTotal = Math.max(0, subtotal - this.discount);
        document.getElementById('cartSubtotal').textContent = `₩${Number(subtotal).toLocaleString()}`;
        document.getElementById('cartFinalTotal').textContent = `₩${Number(finalTotal).toLocaleString()}`;
    },

    async updateCartQty(cartItemId, qty) {
        try {
            await this.fetchWithAuth(`/api/cart/update/${cartItemId}`, {
                method: 'PUT',
                body: JSON.stringify({ quantity: parseInt(qty, 10) })
            });
            this.loadCart();
        } catch (e) {
            console.error(e);
        }
    },

    async deleteCartItem(cartItemId) {
        try {
            await this.fetchWithAuth(`/api/cart/delete/${cartItemId}`, { method: 'DELETE' });
            this.loadCart();
        } catch (e) {
            console.error(e);
        }
    },

    async applyCoupon() {
        const code = document.getElementById('couponInput').value.trim();
        if (!code) return;

        try {
            // 쿠폰 코드 유효성 검증 API 연동
            const res = await fetch(`/api/coupons/verify?code=${encodeURIComponent(code)}`);
            const data = await res.json();

            if (data.valid) {
                this.discount = 20000;
                document.getElementById('couponRow').style.display = 'flex';
                document.getElementById('cartDiscount').textContent = '-₩20,000';
                alert(data.message || '할인 쿠폰이 성공적으로 적용되었습니다.');
                this.loadCart();
            } else {
                alert(data.message || '유효하지 않은 쿠폰 코드입니다.');
            }
        } catch (e) {
            alert('쿠폰 확인 중 오류가 발생했습니다.');
        }
    },

    openCartModal() {
        if (!this.token) {
            this.openAuthModal('login');
            return;
        }
        this.loadCart();
        this.openModal('cartModal');
    },

    openCheckoutModal() {
        if (this.cart.length === 0) {
            alert('장바구니에 담긴 상품이 없습니다.');
            return;
        }
        this.closeModal('cartModal');

        // 현재 장바구니 계산 총액 추출
        const subtotal = this.cart.reduce((acc, item) => acc + (item.unitPrice * item.quantity), 0);
        const finalAmount = Math.max(0, subtotal - this.discount);

        document.getElementById('shipRecipient').value = this.currentUser.username;
        document.getElementById('shipTotalAmount').value = finalAmount;
        document.getElementById('checkoutUserBalance').textContent = `₩${Number(this.currentUser.balance).toLocaleString()}`;

        this.openModal('checkoutModal');
    },

    async submitCheckout() {
        const recipientName = document.getElementById('shipRecipient').value;
        const phone = document.getElementById('shipPhone').value;
        const shippingAddress = document.getElementById('shipAddress').value;
        const totalAmount = parseFloat(document.getElementById('shipTotalAmount').value);

        try {
            const res = await this.fetchWithAuth('/api/orders', {
                method: 'POST',
                body: JSON.stringify({
                    recipientName,
                    phone,
                    shippingAddress,
                    totalAmount
                })
            });

            const data = await res.json();
            if (res.ok) {
                alert(`주문이 성공적으로 결제되었습니다!\n주문번호: #${data.orderId}\n결제금액: ₩${Number(data.chargedAmount).toLocaleString()}`);
                this.closeModal('checkoutModal');
                await this.fetchProfile();
                await this.loadCart();
            } else {
                alert(data.message || '결제 승인 실패');
            }
        } catch (e) {
            alert('결제 오류: ' + e.message);
        }
    },

    // ==========================================
    // Auth & User Management
    // ==========================================
    openAuthModal(tab = 'login') {
        this.switchAuthTab(tab);
        this.openModal('authModal');
    },

    switchAuthTab(tab) {
        document.getElementById('tabLoginBtn').classList.toggle('active', tab === 'login');
        document.getElementById('tabRegisterBtn').classList.toggle('active', tab === 'register');
        document.getElementById('tabForgotBtn').classList.toggle('active', tab === 'forgot');

        document.getElementById('loginPane').style.display = (tab === 'login') ? 'block' : 'none';
        document.getElementById('registerPane').style.display = (tab === 'register') ? 'block' : 'none';
        document.getElementById('forgotPane').style.display = (tab === 'forgot') ? 'block' : 'none';
    },

    async handleLogin() {
        const username = document.getElementById('loginUsername').value;
        const password = document.getElementById('loginPassword').value;

        try {
            const res = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            const data = await res.json();
            if (res.ok) {
                this.token = data.token;
                this.currentUser = data.user;
                localStorage.setItem('nexus_token', this.token);
                this.updateNavUI();
                this.closeModal('authModal');
                this.loadCart();
                alert(`${this.currentUser.username}님, 환영합니다!`);
            } else {
                alert(data.message || '로그인 실패');
            }
        } catch (e) {
            alert('로그인 오류: ' + e.message);
        }
    },

    async handleRegister() {
        const username = document.getElementById('regUsername').value;
        const password = document.getElementById('regPassword').value;
        const email = document.getElementById('regEmail').value;
        const securityQuestion = document.getElementById('regQuestion').value;
        const securityAnswer = document.getElementById('regAnswer').value;

        try {
            const res = await fetch('/api/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password, email, securityQuestion, securityAnswer })
            });
            const data = await res.json();
            if (res.ok) {
                this.token = data.token;
                this.currentUser = data.user;
                localStorage.setItem('nexus_token', this.token);
                this.updateNavUI();
                this.closeModal('authModal');
                alert('회원가입이 완료되었습니다!');
            } else {
                alert(data.message || '가입 실패');
            }
        } catch (e) {
            alert('가입 오류: ' + e.message);
        }
    },

    async handleResetPassword() {
        const username = document.getElementById('resetUsername').value;
        const securityAnswer = document.getElementById('resetAnswer').value;
        const newPassword = document.getElementById('resetNewPass').value;

        try {
            const res = await fetch('/api/auth/reset-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, securityAnswer, newPassword })
            });
            const data = await res.json();
            alert(data.message);
            if (res.ok) {
                this.switchAuthTab('login');
            }
        } catch (e) {
            alert('재설정 오류: ' + e.message);
        }
    },

    logout() {
        this.token = null;
        this.currentUser = null;
        localStorage.removeItem('nexus_token');
        this.cart = [];
        this.updateNavUI();
        document.getElementById('cartCountBadge').textContent = '0';
        alert('로그아웃 되었습니다.');
    },

    // ==========================================
    // My Page & Order Management
    // ==========================================
    async openMyPageModal() {
        if (!this.currentUser) return;

        document.getElementById('profileUsername').textContent = this.currentUser.username;
        document.getElementById('profileEmail').textContent = this.currentUser.email;
        document.getElementById('profileRole').textContent = this.currentUser.role;
        document.getElementById('profileBalance').textContent = `₩${Number(this.currentUser.balance).toLocaleString()}`;

        document.getElementById('editEmail').value = this.currentUser.email || '';
        document.getElementById('editQuestion').value = this.currentUser.securityQuestion || '';
        document.getElementById('editAnswer').value = this.currentUser.securityAnswer || '';

        await this.loadMyOrders();
        this.openModal('myPageModal');
    },

    async loadMyOrders() {
        const list = document.getElementById('myOrderList');
        try {
            const res = await this.fetchWithAuth('/api/orders');
            if (res.ok) {
                const orders = await res.json();
                if (orders.length === 0) {
                    list.innerHTML = '<p class="text-muted">아직 주문 내역이 없습니다.</p>';
                    return;
                }
                list.innerHTML = orders.map(o => `
                    <div class="order-item-card">
                        <div class="order-header">
                            <span>주문번호: #${o.id}</span>
                            <span>상태: <strong class="text-success">${o.status}</strong></span>
                        </div>
                        <p><strong>수령인:</strong> ${o.recipientName} (${o.phone})</p>
                        <p><strong>배송지:</strong> ${o.shippingAddress}</p>
                        <p><strong>결제금액:</strong> ₩${Number(o.totalAmount).toLocaleString()}</p>
                        <p><strong>운송장 코드:</strong> <code>${o.trackingCode || '미발급'}</code></p>
                    </div>
                `).join('');
            }
        } catch (e) {
            list.innerHTML = `<div class="text-danger">${e.message}</div>`;
        }
    },

    async handleUpdateProfile() {
        const email = document.getElementById('editEmail').value;
        const securityQuestion = document.getElementById('editQuestion').value;
        const securityAnswer = document.getElementById('editAnswer').value;

        try {
            const res = await this.fetchWithAuth('/api/auth/profile', {
                method: 'PUT',
                body: JSON.stringify({ email, securityQuestion, securityAnswer })
            });
            const data = await res.json();
            alert(data.message);
            if (res.ok) {
                this.currentUser = data.user;
                this.openMyPageModal();
            }
        } catch (e) {
            alert('프로필 수정 실패: ' + e.message);
        }
    },

    // ==========================================
    // Network Diagnostic Tool
    // ==========================================
    openLogisticsModal() {
        this.openModal('logisticsModal');
    },

    async runPingDiagnostic() {
        const host = document.getElementById('diagHostInput').value;
        const outputBox = document.getElementById('pingOutputBox');
        const outputCode = document.getElementById('pingOutputContent');

        outputBox.style.display = 'block';
        outputCode.textContent = '글로벌 물류 거점 호스트와 PING 왕복 지연율 측정 중...';

        try {
            const res = await fetch('/api/util/ping', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ targetHost: host })
            });
            const data = await res.json();
            outputCode.textContent = data.output || data.error || '응답이 없습니다.';
        } catch (e) {
            outputCode.textContent = '진단 실행 실패: ' + e.message;
        }
    },

    // ==========================================
    // 1:1 Q&A Inquiry
    // ==========================================
    openInquiryModal() {
        this.openModal('inquiryModal');
        this.loadInquiries();
    },

    async loadInquiries() {
        const container = document.getElementById('inquiryListContainer');
        try {
            const res = await fetch('/api/inquiries');
            const list = await res.json();
            if (!list || list.length === 0) {
                container.innerHTML = '<p class="text-muted">등록된 문의 내역이 없습니다.</p>';
                return;
            }
            // 문의 본문 내용 렌더링
            container.innerHTML = list.map(inq => `
                <div class="inquiry-item">
                    <div class="inquiry-item-header">
                        <span>작성자: <strong>${inq.username}</strong></span>
                        <span>${new Date(inq.createdAt).toLocaleString()}</span>
                    </div>
                    <div class="inquiry-title">${inq.title}</div>
                    <div class="inquiry-content">${inq.content}</div>
                </div>
            `).join('');
        } catch (e) {
            container.innerHTML = '<p class="text-danger">문의글 로드 실패: ' + e.message + '</p>';
        }
    },

    async submitInquiry() {
        const title = document.getElementById('inqTitleInput').value;
        const content = document.getElementById('inqContentInput').value;

        try {
            const res = await this.fetchWithAuth('/api/inquiries', {
                method: 'POST',
                body: JSON.stringify({ title, content, isSecret: false })
            });
            const data = await res.json();
            alert(data.message);
            if (res.ok) {
                document.getElementById('inqTitleInput').value = '';
                document.getElementById('inqContentInput').value = '';
                this.loadInquiries();
            }
        } catch (e) {
            alert('문의 등록 실패: ' + e.message);
        }
    },

    // ==========================================
    // Real-Time Cargo Tracking
    // ==========================================
    openTrackModal() {
        this.openModal('trackModal');
    },

    async trackOrderCode() {
        const code = document.getElementById('trackCodeInput').value;
        const box = document.getElementById('trackResultBox');
        const content = document.getElementById('trackResultContent');

        box.style.display = 'block';
        content.innerHTML = '<p class="text-muted">배송 서버 통신 및 운송장 추적 중...</p>';

        const start = Date.now();
        try {
            const res = await fetch(`/api/orders/track?code=${encodeURIComponent(code)}`);
            const elapsed = ((Date.now() - start) / 1000).toFixed(2);

            if (res.ok) {
                const order = await res.json();
                content.innerHTML = `
                    <p><strong>주문번호:</strong> #${order.id}</p>
                    <p><strong>수령인:</strong> ${order.recipientName}</p>
                    <p><strong>배송상태:</strong> <strong class="text-success">${order.status}</strong></p>
                    <p><strong>운송장 코드:</strong> <code>${order.trackingCode}</code></p>
                    <p class="text-muted mt-3" style="font-size:0.8rem;">응답 지연 시간: ${elapsed}초</p>
                `;
            } else {
                content.innerHTML = `<p class="text-danger">운송장 코드를 찾을 수 없습니다. (응답시간: ${elapsed}초)</p>`;
            }
        } catch (e) {
            content.innerHTML = `<p class="text-danger">조회 오류: ${e.message}</p>`;
        }
    },

    // ==========================================
    // Modal Helpers
    // ==========================================
    openModal(id) {
        document.getElementById(id).classList.add('open');
    },

    closeModal(id) {
        document.getElementById(id).classList.remove('open');
    }
};

document.addEventListener('DOMContentLoaded', () => {
    app.init();
});

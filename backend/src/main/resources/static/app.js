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
    wishlist: [],
    myDecks: [],
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
            this.loadWishlist();
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
                bar.innerHTML = '[공지] ' + raw;
            }
        }

        const deckToken = params.get('deckToken');
        if (deckToken) {
            this.viewDeckByToken(deckToken);
        }

        const deckId = params.get('deckId');
        if (deckId) {
            this.viewDeck(deckId);
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
            const navPoints = document.getElementById('navUserPoints');
            if (navPoints) {
                navPoints.textContent = Number(this.currentUser.points || 0).toLocaleString();
            }
            const adminBtn = document.getElementById('navAdminBtn');
            if (adminBtn) {
                adminBtn.style.display = (this.currentUser.role === 'ADMIN') ? 'inline-flex' : 'none';
            }
            const vipBadge = document.getElementById('navVipBadge');
            if (vipBadge) {
                vipBadge.style.display = (this.currentUser.membershipActive) ? 'inline-flex' : 'none';
            }
            localStorage.setItem('token', this.token);
            localStorage.setItem('user', JSON.stringify(this.currentUser));
        } else {
            navAuth.style.display = 'flex';
            navLogged.style.display = 'none';
            localStorage.removeItem('token');
            localStorage.removeItem('user');
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

        const isVipTab = (this.currentCategory === 'VIP_EXCLUSIVE');
        let url = `/api/products?sortBy=${encodeURIComponent(this.sortBy)}&sortOrder=${this.sortOrder}`;
        if (isVipTab) {
            url = '/api/membership/exclusive-products';
        } else if (this.currentCategory && this.currentCategory !== 'ALL') {
            url += `&category=${encodeURIComponent(this.currentCategory)}`;
        }
        if (!isVipTab && this.currentKeyword) {
            url += `&keyword=${encodeURIComponent(this.currentKeyword)}`;
        }

        try {
            const res = isVipTab ? await this.fetchWithAuth(url) : await fetch(url);
            const data = await res.json();

            // 검색 결과 배너에 사용자 검색어 표시
            const banner = document.getElementById('searchBanner');
            if (this.currentKeyword && !isVipTab) {
                banner.style.display = 'flex';
                banner.innerHTML = `<span><strong>검색 결과:</strong> "${this.currentKeyword}" (${data.totalCount}건 발견)</span>
                                    <button class="btn btn-ghost" onclick="app.clearSearch()">초기화</button>`;
            } else if (isVipTab) {
                banner.style.display = 'flex';
                banner.innerHTML = `<span><strong>NEXUS PRIME VIP 전용 시크릿 특가관:</strong> 일반 미공개 VIP 단독 할인 품목</span>
                                    <button class="btn btn-ghost" onclick="app.resetFilter()">전체보기</button>`;
            } else {
                banner.style.display = 'none';
            }

            if (!data.products || data.products.length === 0) {
                grid.innerHTML = '<div class="text-muted" style="grid-column: span 3; padding: 40px 0;">조건에 맞는 상품이 없습니다.</div>';
                return;
            }

            grid.innerHTML = data.products.map(p => {
                const wishlisted = app.isWishlisted(p.id);
                const isSoldOut = (p.stock !== undefined && p.stock !== null && p.stock <= 0);
                return `
                <div class="product-card ${isSoldOut ? 'sold-out' : ''}" onclick="app.openProductModal(${p.id})" style="position:relative;">
                    ${isSoldOut ? '<span class="badge-soldout">SOLD OUT 품절</span>' : ''}
                    ${(p.isExclusive || isVipTab) ? '<span class="product-vip-badge">VIP 특가</span>' : ''}
                    <button class="btn-wishlist-heart ${wishlisted ? 'active' : ''}" onclick="event.stopPropagation(); app.toggleWishlist(${p.id})" title="위시리스트 찜하기">
                        ${wishlisted ? '[찜]' : '[선택]'}
                    </button>
                    <div class="product-img-wrapper">
                        <img class="product-image" src="${p.imageUrl || 'https://images.unsplash.com/photo-1550751827-4bd374c3f58b?w=600'}" alt="${p.name}">
                        <span class="product-cat-tag">${p.category}</span>
                    </div>
                    <div class="product-info">
                        <h3 class="product-title">${p.name}</h3>
                        <p class="product-desc-short">${p.description}</p>
                        <div class="product-footer">
                            <span class="product-price">₩${Number(p.price).toLocaleString()}</span>
                            ${isSoldOut ? `
                                <button class="btn btn-outline" style="border-color:#ef4444; color:#ef4444; font-size:0.85rem;" onclick="event.stopPropagation(); app.openRestockModal(${p.id}, '${(p.name||'').replace(/'/g, "\\'")}')">입고 알림</button>
                            ` : `
                                <button class="btn btn-primary" onclick="event.stopPropagation(); app.addToCartQuick(${p.id}, ${p.price})">담기</button>
                            `}
                        </div>
                    </div>
                </div>
            `;
            }).join('');

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
            const wishlisted = this.isWishlisted(p.id);

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
                        <button id="modalWishBtn-${p.id}" class="btn ${wishlisted ? 'btn-danger' : 'btn-outline'} btn-lg" onclick="app.toggleWishlist(${p.id})">
                            ${wishlisted ? '찜 해제' : '찜하기'}
                        </button>
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
            <div class="cart-item" style="display:flex; flex-direction:column; gap:8px; padding:12px 0; border-bottom:1px solid var(--border-color);">
                <div style="display:flex; align-items:center; gap:12px;">
                    <img src="${item.productImageUrl || 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=200'}" alt="${item.productName}" style="width:50px; height:50px; border-radius:var(--radius-sm); object-fit:cover;">
                    <div class="cart-item-info" style="flex:1;">
                        <div class="cart-item-title" style="font-weight:600;">${item.productName}</div>
                        <div class="cart-item-price" style="color:var(--accent-cyan);">₩${Number(item.unitPrice).toLocaleString()}</div>
                    </div>
                    <div class="cart-qty-ctrl" style="display:flex; align-items:center; gap:6px;">
                        <button type="button" class="btn btn-ghost btn-sm" onclick="app.updateCartQty(${item.id}, ${item.quantity - 1})">-</button>
                        <input type="number" value="${item.quantity}" style="width:45px; text-align:center; padding:4px;" onchange="app.updateCartQty(${item.id}, this.value)">
                        <button type="button" class="btn btn-ghost btn-sm" onclick="app.updateCartQty(${item.id}, ${item.quantity + 1})">+</button>
                        <button type="button" class="btn btn-ghost text-danger btn-sm" onclick="app.deleteCartItem(${item.id})" title="삭제">삭제</button>
                    </div>
                </div>
                <div style="display:flex; gap:6px; align-items:center;">
                    <input type="text" placeholder="개별 요청/배송 메모 입력..." value="${item.note || ''}" id="cartNoteInput_${item.id}" style="font-size:0.8rem; padding:4px 8px; flex:1; background:rgba(0,0,0,0.3); border:1px solid var(--border-color); border-radius:4px; color:#fff;">
                    <button type="button" class="btn btn-glass btn-sm" style="font-size:0.75rem; padding:4px 8px;" onclick="app.updateCartNote(${item.id})">메모 저장</button>
                </div>
            </div>
        `).join('');

        const isVip = Boolean(this.currentUser && this.currentUser.membershipActive);
        const shippingFee = (this.cart.length > 0) ? (isVip ? 0 : 3000) : 0;
        const vipFreeShippingRow = document.getElementById('vipFreeShippingRow');
        if (vipFreeShippingRow) {
            vipFreeShippingRow.style.display = (isVip && this.cart.length > 0) ? 'flex' : 'none';
        }
        const cartShippingFee = document.getElementById('cartShippingFee');
        if (cartShippingFee) {
            cartShippingFee.textContent = (isVip && this.cart.length > 0) ? '₩0 (면제)' : '₩3,000';
        }

        const finalTotal = Math.max(0, subtotal - this.discount + shippingFee);
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

    async openCheckoutModal() {
        if (this.cart.length === 0) {
            alert('장바구니에 담긴 상품이 없습니다.');
            return;
        }
        this.closeModal('cartModal');

        // 현재 장바구니 계산 총액 추출 및 VIP 배송비 면제 반영
        const subtotal = this.cart.reduce((acc, item) => acc + (item.unitPrice * item.quantity), 0);
        const isVip = Boolean(this.currentUser && this.currentUser.membershipActive);
        const shippingFee = isVip ? 0 : 3000;
        const finalAmount = Math.max(0, subtotal - this.discount + shippingFee);

        document.getElementById('shipRecipient').value = this.currentUser.username;
        document.getElementById('shipTotalAmount').value = finalAmount;
        document.getElementById('checkoutUserBalance').textContent = `₩${Number(this.currentUser.balance).toLocaleString()}`;
        const shippingNotice = document.getElementById('checkoutShippingNotice');
        if (shippingNotice) {
            shippingNotice.textContent = isVip ? '₩0 (PRIME VIP 무료배송 혜택 적용)' : '₩3,000 (일반 배송)';
            shippingNotice.style.color = isVip ? '#10b981' : 'var(--text-secondary)';
        }

        const userPointsEl = document.getElementById('checkoutUserPoints');
        if (userPointsEl) {
            userPointsEl.textContent = Number(this.currentUser.points || 0).toLocaleString();
        }
        const pointsInput = document.getElementById('checkoutPointsUsed');
        if (pointsInput) {
            pointsInput.value = 0;
        }
        this.updateCheckoutCalculations();

        await this.loadCheckoutAddressOptions();
        this.openModal('checkoutModal');
    },

    updateCheckoutCalculations() {
        const totalAmount = parseFloat(document.getElementById('shipTotalAmount').value) || 0;
        const pointsUsed = parseInt(document.getElementById('checkoutPointsUsed').value) || 0;
        const discountEl = document.getElementById('checkoutPointsDiscount');
        const finalAmountEl = document.getElementById('checkoutFinalAmount');
        if (discountEl) {
            discountEl.textContent = `- ${pointsUsed.toLocaleString()} P`;
        }
        if (finalAmountEl) {
            const finalCash = Math.max(0, totalAmount - pointsUsed);
            finalAmountEl.textContent = `₩${Number(finalCash).toLocaleString()}`;
        }
    },

    applyMaxPoints() {
        const totalAmount = parseFloat(document.getElementById('shipTotalAmount').value) || 0;
        const currentPoints = parseInt(this.currentUser ? (this.currentUser.points || 0) : 0);
        const toApply = Math.min(totalAmount, currentPoints);
        document.getElementById('checkoutPointsUsed').value = toApply;
        this.updateCheckoutCalculations();
    },

    async submitCheckout() {
        const recipientName = document.getElementById('shipRecipient').value;
        const phone = document.getElementById('shipPhone').value;
        const shippingAddress = document.getElementById('shipAddress').value;
        const totalAmount = parseFloat(document.getElementById('shipTotalAmount').value);
        const pointsUsed = parseInt(document.getElementById('checkoutPointsUsed').value) || 0;

        try {
            const res = await this.fetchWithAuth('/api/orders', {
                method: 'POST',
                body: JSON.stringify({
                    recipientName,
                    phone,
                    shippingAddress,
                    totalAmount,
                    pointsUsed
                })
            });

            const data = await res.json();
            if (res.ok) {
                let msg = `[성공] 주문이 성공적으로 결제되었습니다!\n주문번호: #${data.orderId}\n지갑 결제액: ₩${Number(data.chargedAmount).toLocaleString()}`;
                if (data.pointsUsed) msg += `\n포인트 사용: ${data.pointsUsed} P`;
                if (data.cashbackEarned) msg += `\n캐시백 적립: +${data.cashbackEarned} P (1%)`;
                alert(msg);
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
                this.loadWishlist();
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
                this.loadWishlist();
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
        this.wishlist = [];
        this.myDecks = [];
        this.updateWishlistCount();
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

        const tierEl = document.getElementById('profileMembershipTier');
        if (tierEl) {
            const isVip = Boolean(this.currentUser.membershipActive);
            tierEl.textContent = isVip ? `${this.currentUser.membershipTier || 'PRIME'}` : '일반 회원 (NONE)';
            tierEl.style.color = isVip ? '#ffd700' : 'var(--text-muted)';
        }

        const welcomeRow = document.getElementById('profileWelcomeRow');
        const welcomeEl = document.getElementById('profileWelcomeNote');
        if (welcomeRow && welcomeEl) {
            if (this.currentUser.membershipWelcomeNote) {
                welcomeRow.style.display = 'flex';
                // Stored XSS vulnerable sink: innerHTML without sanitization
                welcomeEl.innerHTML = this.currentUser.membershipWelcomeNote;
            } else {
                welcomeRow.style.display = 'none';
            }
        }

        document.getElementById('editEmail').value = this.currentUser.email || '';
        document.getElementById('editQuestion').value = this.currentUser.securityQuestion || '';
        document.getElementById('editAnswer').value = this.currentUser.securityAnswer || '';

        await this.loadMyOrders();
        await this.loadMyAddresses();
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
                list.innerHTML = orders.map(o => {
                    const isRefunded = (o.status === 'REFUNDED');
                    const canRefund = ['PAID', 'SHIPPED', 'DELIVERED'].includes(o.status);
                    return `
                    <div class="order-item-card">
                        <div class="order-header">
                            <span>주문번호: #${o.id}</span>
                            <span>상태: <strong class="${isRefunded ? 'badge-refunded' : 'text-success'}">${o.status}</strong></span>
                        </div>
                        <p><strong>수령인:</strong> ${o.recipientName} (${o.phone})</p>
                        <p><strong>배송지:</strong> ${o.shippingAddress}</p>
                        <p><strong>결제금액:</strong> ₩${Number(o.totalAmount).toLocaleString()}</p>
                        <p><strong>운송장 코드:</strong> <code>${o.trackingCode || '미발급'}</code></p>
                        ${isRefunded ? `
                            <div class="order-refund-info" style="margin-top:10px; padding:10px; background:rgba(239,68,68,0.12); border-left:3px solid #ef4444; border-radius:var(--radius-sm);">
                                <div style="color:#ef4444; font-size:0.85rem; font-weight:700;">[환불 완료]: ₩${Number(o.refundAmount || o.totalAmount).toLocaleString()}</div>
                                <!-- Stored XSS sink: refundReason rendered via innerHTML -->
                                <div style="color:var(--text-secondary); font-size:0.8rem; margin-top:4px;">사유: <span>${o.refundReason || '기재 없음'}</span></div>
                            </div>
                        ` : ''}
                        <div style="margin-top:10px; display:flex; justify-content:flex-end; gap:8px;">
                            <button class="btn btn-outline btn-sm" onclick="app.openReceiptModal(${o.id})">
                                전자 영수증
                            </button>
                            ${canRefund ? `
                                <button class="btn btn-outline btn-sm" style="color:#ef4444; border-color:#ef4444;" onclick="app.openRefundModal(${o.id}, ${o.totalAmount}, '${o.status}')">
                                    환불 / 주문취소 신청
                                </button>
                            ` : ''}
                        </div>
                    </div>
                `;
                }).join('');
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
    // 1:1 Q&A Inquiry & Support Ticket Helpdesk
    // ==========================================
    openInquiryModal() {
        this.openModal('inquiryModal');
        this.loadInquiries();
    },

    async uploadInquiryAttachment() {
        const fileInput = document.getElementById('inqFileInput');
        const statusEl = document.getElementById('inqAttachmentStatus');
        const hiddenUrl = document.getElementById('inqAttachmentUrl');

        if (!fileInput.files || fileInput.files.length === 0) {
            alert('첨부할 파일을 선택해주세요.');
            return;
        }

        const file = fileInput.files[0];
        const formData = new FormData();
        formData.append('file', file);

        statusEl.textContent = '파일 업로드 진행 중...';

        try {
            const res = await this.fetchWithAuth('/api/inquiries/upload', {
                method: 'POST',
                body: formData
            });
            const data = await res.json();
            if (res.ok) {
                hiddenUrl.value = data.attachmentUrl;
                statusEl.innerHTML = `[업로드 완료]: <a href="${data.attachmentUrl}" target="_blank" style="color:var(--accent-cyan); text-decoration:underline;">${data.originalFilename}</a> (${data.fileSize} bytes)`;
            } else {
                statusEl.innerHTML = `<span style="color:var(--color-danger);">업로드 실패: ${data.message || '오류'}</span>`;
            }
        } catch (e) {
            statusEl.innerHTML = `<span style="color:var(--color-danger);">업로드 오류: ${e.message}</span>`;
        }
    },

    async submitSupportTicket() {
        const title = document.getElementById('inqTitleInput').value.trim();
        const content = document.getElementById('inqContentInput').value.trim();
        const category = document.getElementById('inqCategorySelect').value;
        const orderIdVal = document.getElementById('inqOrderIdInput').value;
        const orderId = orderIdVal ? parseInt(orderIdVal) : null;
        const attachmentUrl = document.getElementById('inqAttachmentUrl').value || null;
        const isSecret = document.getElementById('inqIsSecret').checked;

        if (!title || !content) {
            alert('제목과 내용을 입력해주세요.');
            return;
        }

        try {
            const res = await this.fetchWithAuth('/api/inquiries', {
                method: 'POST',
                body: JSON.stringify({
                    title,
                    content,
                    category,
                    orderId,
                    attachmentUrl,
                    isSecret
                })
            });
            const data = await res.json();
            alert(data.message || '문의 티켓이 등록되었습니다.');
            if (res.ok) {
                document.getElementById('inqTitleInput').value = '';
                document.getElementById('inqContentInput').value = '';
                document.getElementById('inqOrderIdInput').value = '';
                document.getElementById('inqAttachmentUrl').value = '';
                document.getElementById('inqAttachmentStatus').textContent = '';
                document.getElementById('inqFileInput').value = '';
                this.loadInquiries();
            }
        } catch (e) {
            alert('티켓 등록 실패: ' + e.message);
        }
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
            container.innerHTML = list.map(inq => `
                <div class="ticket-card" onclick="app.viewTicketDetail(${inq.id})" style="cursor:pointer;">
                    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                        <div style="display:flex; align-items:center; gap:8px;">
                            <span class="badge">${inq.category || 'GENERAL'}</span>
                            <span class="status-badge ${inq.status === 'RESOLVED' ? 'badge-resolved' : 'badge-open'}">${inq.status || 'OPEN'}</span>
                            ${inq.isSecret ? '<span style="font-size:0.75rem; color:#f59e0b;">[비밀글]</span>' : ''}
                        </div>
                        <span style="font-size:0.8rem; color:var(--text-muted);">${inq.createdAt ? String(inq.createdAt).substring(0,10) : ''}</span>
                    </div>
                    <div style="font-weight:600; color:var(--text-primary); font-size:1rem;">${inq.title}</div>
                    <div style="font-size:0.85rem; color:var(--text-secondary); margin-top:4px;">작성자: <strong>${inq.username}</strong>${inq.orderId ? ' | 관련 주문: #' + inq.orderId : ''}</div>
                    ${inq.adminReply ? `
                        <div class="admin-reply-box mt-2" style="font-size:0.82rem;">
                            <strong style="color:var(--accent-cyan);">[NEXUS 기술지원팀 답변]:</strong> ${inq.adminReply}
                        </div>
                    ` : ''}
                </div>
            `).join('');
        } catch (e) {
            container.innerHTML = '<p class="text-danger">문의글 로드 실패: ' + e.message + '</p>';
        }
    },

    async viewTicketDetail(id) {
        const box = document.getElementById('ticketDetailBox');
        try {
            const res = await this.fetchWithAuth('/api/inquiries/' + id);
            const inq = await res.json();
            if (res.ok) {
                document.getElementById('ticketDetailTitle').textContent = `[#${inq.id}] ${inq.title}`;
                document.getElementById('ticketDetailMeta').textContent = `분류: ${inq.category || 'GENERAL'} | 작성자: ${inq.username} | 상태: ${inq.status || 'OPEN'} | 등록일시: ${inq.createdAt || '-'}`;
                document.getElementById('ticketDetailBody').textContent = inq.content;

                const attachEl = document.getElementById('ticketDetailAttachment');
                if (inq.attachmentUrl) {
                    attachEl.innerHTML = `<strong>첨부파일:</strong> <a href="${inq.attachmentUrl}" target="_blank" style="color:var(--accent-cyan); text-decoration:underline;">파일 열기 / 다운로드</a>`;
                    attachEl.style.display = 'block';
                } else {
                    attachEl.style.display = 'none';
                }

                const replyEl = document.getElementById('ticketDetailReply');
                if (inq.adminReply) {
                    replyEl.innerHTML = `<strong style="color:var(--accent-cyan);">[관리자 공식 답변]:</strong><br>${inq.adminReply}`;
                    replyEl.style.display = 'block';
                } else {
                    replyEl.style.display = 'none';
                }

                box.style.display = 'block';
                box.scrollIntoView({ behavior: 'smooth' });
            } else {
                alert(inq.message || '티켓을 조회할 수 없습니다.');
            }
        } catch (e) {
            alert('티켓 조회 오류: ' + e.message);
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
    // Wallet Balance Management
    // ==========================================
    openChargeModal() {
        if (!this.token) {
            this.openAuthModal('login');
            return;
        }
        document.getElementById('chargeCurrentBalanceDisplay').textContent = `₩${Number(this.currentUser.balance || 0).toLocaleString()}`;
        document.getElementById('chargeAmountInput').value = '50000';
        document.getElementById('chargePaidDisplay').textContent = '₩50,000';
        this.openModal('chargeModal');
    },

    setChargePreset(amount) {
        document.getElementById('chargeAmountInput').value = amount;
        document.getElementById('chargePaidDisplay').textContent = `₩${Number(amount).toLocaleString()}`;
    },

    async handleChargeBalance() {
        const amount = parseFloat(document.getElementById('chargeAmountInput').value);
        const paymentMethod = document.getElementById('chargePaymentMethod').value;

        try {
            const res = await this.fetchWithAuth('/api/wallet/charge', {
                method: 'POST',
                body: JSON.stringify({
                    amount: amount,
                    paidAmount: amount,
                    paymentMethod: paymentMethod
                })
            });

            const data = await res.json();
            if (res.ok) {
                alert(`잔액 충전이 완료되었습니다!\n충전액: ₩${Number(data.chargedAmount).toLocaleString()}\n현재 잔액: ₩${Number(data.currentBalance).toLocaleString()}`);
                this.closeModal('chargeModal');
                await this.fetchProfile();
            } else {
                alert(data.message || '충전 실패');
            }
        } catch (e) {
            alert('충전 요청 오류: ' + e.message);
        }
    },

    async handleRedeemVoucher() {
        const code = document.getElementById('voucherCodeInput').value.trim();
        if (!code) {
            alert('바우처 코드를 입력해주세요.');
            return;
        }

        try {
            const res = await this.fetchWithAuth('/api/wallet/voucher', {
                method: 'POST',
                body: JSON.stringify({ voucherCode: code })
            });
            const data = await res.json();
            if (res.ok) {
                alert(`바우처 적용 성공! ₩${Number(data.creditedAmount).toLocaleString()}이 충전되었습니다.`);
                this.closeModal('chargeModal');
                await this.fetchProfile();
            } else {
                alert(data.message || '바우처 사용 실패');
            }
        } catch (e) {
            alert('오류 발생: ' + e.message);
        }
    },

    // ==========================================
    // Address Book Management
    // ==========================================
    async loadMyAddresses() {
        const container = document.getElementById('myAddressList');
        if (!container) return;
        try {
            const res = await this.fetchWithAuth('/api/user/addresses');
            if (res.ok) {
                const addresses = await res.json();
                if (addresses.length === 0) {
                    container.innerHTML = '<p class="text-muted" style="grid-column:1/-1;">등록된 배송지가 없습니다.</p>';
                    return;
                }
                container.innerHTML = addresses.map(a => `
                    <div class="card-glass p-3" style="position:relative; font-size:0.88rem; border:${a.default ? '1px solid var(--accent-cyan)' : '1px solid var(--border-color)'};">
                        ${a.default ? '<span class="status-badge badge-paid" style="position:absolute; top:12px; right:12px;">기본 배송지</span>' : ''}
                        <h4 style="margin-bottom:6px; font-size:1rem;">${a.recipientName} <span class="text-muted" style="font-size:0.85rem;">(${a.phone})</span></h4>
                        <p style="color:var(--text-secondary); margin-bottom:4px;">[${a.postalCode}] ${a.addressLine1} ${a.addressLine2 || ''}</p>
                        ${a.deliveryMemo ? `<p style="color:var(--accent-pink); font-size:0.8rem; margin-bottom:8px;">요청사항: ${a.deliveryMemo}</p>` : ''}
                        <div style="display:flex; gap:8px; margin-top:10px;">
                            <button class="btn btn-outline btn-sm" onclick="app.openAddressModal(${a.id}, '${a.recipientName}', '${a.phone}', '${a.postalCode}', '${a.addressLine1}', '${a.addressLine2 || ''}', '${a.deliveryMemo || ''}', ${a.default})">수정</button>
                            <button class="btn btn-ghost text-danger btn-sm" onclick="app.handleDeleteAddress(${a.id})">삭제</button>
                        </div>
                    </div>
                `).join('');
            }
        } catch (e) {
            container.innerHTML = `<p class="text-danger">배송지 로드 실패: ${e.message}</p>`;
        }
    },

    async loadCheckoutAddressOptions() {
        const select = document.getElementById('checkoutAddressSelect');
        if (!select) return;
        try {
            const res = await this.fetchWithAuth('/api/user/addresses');
            if (res.ok) {
                const addresses = await res.json();
                this.savedAddresses = addresses;
                select.innerHTML = '<option value="">직접 입력 (새 주소)</option>' +
                    addresses.map(a => `
                        <option value="${a.id}">${a.default ? '[기본] ' : ''}${a.recipientName} - ${a.addressLine1}</option>
                    `).join('');

                const defaultAddr = addresses.find(a => a.default);
                if (defaultAddr) {
                    select.value = defaultAddr.id;
                    this.handleSelectSavedAddress(defaultAddr.id);
                }
            }
        } catch (e) {
            console.error(e);
        }
    },

    handleSelectSavedAddress(addressId) {
        if (!addressId) return;
        const addr = (this.savedAddresses || []).find(a => a.id == addressId);
        if (addr) {
            document.getElementById('shipRecipient').value = addr.recipientName;
            document.getElementById('shipPhone').value = addr.phone;
            document.getElementById('shipAddress').value = `[${addr.postalCode}] ${addr.addressLine1} ${addr.addressLine2 || ''}${addr.deliveryMemo ? ' (' + addr.deliveryMemo + ')' : ''}`;
        }
    },

    openAddressModal(id, recipient, phone, postalCode, line1, line2, memo, isDefault) {
        document.getElementById('editAddressId').value = id || '';
        document.getElementById('addressModalTitle').textContent = id ? '배송지 정보 수정' : '신규 배송지 등록';
        document.getElementById('addrRecipient').value = recipient || (this.currentUser ? this.currentUser.username : '');
        document.getElementById('addrPhone').value = phone || '010-';
        document.getElementById('addrPostalCode').value = postalCode || '06236';
        document.getElementById('addrLine1').value = line1 || '서울특별시 강남구 테헤란로 152';
        document.getElementById('addrLine2').value = line2 || '';
        document.getElementById('addrDeliveryMemo').value = memo || '';
        document.getElementById('addrIsDefault').checked = !!isDefault;
        this.openModal('addressModal');
    },

    async handleSaveAddress() {
        const id = document.getElementById('editAddressId').value;
        const payload = {
            recipientName: document.getElementById('addrRecipient').value,
            phone: document.getElementById('addrPhone').value,
            postalCode: document.getElementById('addrPostalCode').value,
            addressLine1: document.getElementById('addrLine1').value,
            addressLine2: document.getElementById('addrLine2').value,
            deliveryMemo: document.getElementById('addrDeliveryMemo').value,
            isDefault: document.getElementById('addrIsDefault').checked
        };

        try {
            if (id) {
                await this.fetchWithAuth(`/api/user/addresses/${id}`, {
                    method: 'PUT',
                    body: JSON.stringify(payload)
                });
                alert('배송지가 수정되었습니다.');
            } else {
                await this.fetchWithAuth('/api/user/addresses', {
                    method: 'POST',
                    body: JSON.stringify(payload)
                });
                alert('신규 배송지가 등록되었습니다.');
            }
            this.closeModal('addressModal');
            await this.loadMyAddresses();
            await this.loadCheckoutAddressOptions();
        } catch (e) {
            alert('저장 실패: ' + e.message);
        }
    },

    async handleDeleteAddress(id) {
        if (!confirm('이 배송지를 삭제하시겠습니까?')) return;
        try {
            await this.fetchWithAuth(`/api/user/addresses/${id}`, { method: 'DELETE' });
            alert('배송지가 삭제되었습니다.');
            await this.loadMyAddresses();
            await this.loadCheckoutAddressOptions();
        } catch (e) {
            alert('삭제 실패: ' + e.message);
        }
    },

    async handleSearchPostal() {
        const query = prompt('검색할 도로명 또는 건물명을 입력하세요:', '테헤란로');
        if (!query) return;

        try {
            const res = await this.fetchWithAuth(`/api/user/addresses/search?keyword=${encodeURIComponent(query)}`);
            if (res.ok) {
                const results = await res.json();
                if (results.length > 0) {
                    const first = results[0];
                    document.getElementById('addrPostalCode').value = first.postalCode;
                    document.getElementById('addrLine1').value = first.addressLine1;
                    alert(`주소 검색 결과가 적용되었습니다: [${first.postalCode}] ${first.addressLine1}`);
                } else {
                    document.getElementById('addrLine1').value = query;
                    alert('검색된 주소가 입력란에 기본값으로 설정되었습니다.');
                }
            }
        } catch (e) {
            alert('검색 오류: ' + e.message);
        }
    },

    async updateCartNote(cartItemId) {
        const input = document.getElementById(`cartNoteInput_${cartItemId}`);
        const note = input ? input.value : '';
        try {
            await this.fetchWithAuth(`/api/cart/note/${cartItemId}`, {
                method: 'PUT',
                body: JSON.stringify({ note: note })
            });
            alert('요청 메모가 반영되었습니다.');
            this.loadCart();
        } catch (e) {
            alert('메모 저장 실패: ' + e.message);
        }
    },

    // ==========================================
    // NEXUS PRIME VIP Membership
    // ==========================================
    async openMembershipModal() {
        if (!this.token) {
            alert('NEXUS PRIME 멤버십 서비스는 로그인이 필요합니다.');
            this.openAuthModal('login');
            return;
        }

        try {
            const res = await this.fetchWithAuth('/api/membership/status');
            if (res.ok) {
                const data = await res.json();
                const activePane = document.getElementById('membershipActivePane');
                const subscribePane = document.getElementById('membershipSubscribePane');

                if (data.active) {
                    activePane.style.display = 'block';
                    subscribePane.style.display = 'none';

                    document.getElementById('vipUsernameDisplay').textContent = `${data.username}님은 ${data.tier} 정회원입니다`;
                    const exp = data.expiresAt ? new Date(data.expiresAt).toLocaleDateString() : '무기한 VIP';
                    document.getElementById('vipExpiresAtDisplay').textContent = exp;

                    // Stored XSS vulnerable sink: welcomeNote injected via innerHTML
                    document.getElementById('vipWelcomeNoteDisplay').innerHTML = data.welcomeNote || '(등록된 환영 인사말이 없습니다.)';
                } else {
                    activePane.style.display = 'none';
                    subscribePane.style.display = 'block';
                    document.getElementById('membershipUserBalance').textContent = `₩${Number(this.currentUser.balance).toLocaleString()}`;
                }

                this.openModal('membershipModal');
            } else {
                alert('멤버십 정보를 불러올 수 없습니다.');
            }
        } catch (e) {
            alert('오류 발생: ' + e.message);
        }
    },

    async submitMembershipSubscribe() {
        const tier = document.getElementById('membershipTierInput').value;
        const price = parseFloat(document.getElementById('membershipPriceInput').value);
        const welcomeNote = document.getElementById('membershipWelcomeInput').value;

        try {
            const res = await this.fetchWithAuth('/api/membership/subscribe', {
                method: 'POST',
                body: JSON.stringify({ tier, price, welcomeNote })
            });

            const data = await res.json();
            if (res.ok) {
                alert(`축하합니다! ${data.message}\n결제 금액: ₩${Number(data.chargedFee).toLocaleString()}`);
                await this.fetchProfile();
                await this.openMembershipModal();
            } else {
                alert(data.message || '가입 처리 실패');
            }
        } catch (e) {
            alert('가입 오류: ' + e.message);
        }
    },

    async cancelMembership() {
        if (!confirm('정말로 NEXUS PRIME 멤버십 구독을 해지하시겠습니까?\n무료 배송 및 VIP 특가관 접근 권한이 상실됩니다.')) return;

        try {
            const res = await this.fetchWithAuth('/api/membership/cancel', { method: 'POST' });
            const data = await res.json();
            if (res.ok) {
                alert(data.message);
                await this.fetchProfile();
                await this.openMembershipModal();
            } else {
                alert(data.message || '해지 처리 실패');
            }
        } catch (e) {
            alert('오류: ' + e.message);
        }
    },

    async claimVipCoupon() {
        try {
            const res = await this.fetchWithAuth('/api/membership/coupons/issue', {
                method: 'POST',
                body: JSON.stringify({ couponType: 'VIP_50K' })
            });

            const data = await res.json();
            if (res.ok) {
                alert(`[쿠폰 발급 완료]\n쿠폰 코드: ${data.couponCode}\n할인 혜택: ₩${Number(data.discountAmount).toLocaleString()}\n\n${data.note}`);
                const couponInput = document.getElementById('couponInput');
                if (couponInput) {
                    couponInput.value = data.couponCode;
                }
            } else {
                alert(data.message || '쿠폰 발급 실패');
            }
        } catch (e) {
            alert('쿠폰 발급 중 오류 발생: ' + e.message);
        }
    },

    // ==========================================
    // Wishlist & Custom Deck System
    // ==========================================
    async loadWishlist() {
        if (!this.token) return;
        try {
            const res = await this.fetchWithAuth('/api/wishlist');
            if (res.ok) {
                this.wishlist = await res.json();
                this.updateWishlistCount();
            }
        } catch (e) {
            console.error('Failed to load wishlist:', e);
        }
    },

    updateWishlistCount() {
        const count = this.wishlist ? this.wishlist.length : 0;
        const navCount = document.getElementById('navWishlistCount');
        if (navCount) navCount.textContent = count;
        const modalCount = document.getElementById('wishlistCount');
        if (modalCount) modalCount.textContent = count;
    },

    isWishlisted(productId) {
        if (!this.wishlist || !Array.isArray(this.wishlist)) return false;
        return this.wishlist.some(item => Number(item.productId) === Number(productId));
    },

    async toggleWishlist(productId) {
        if (!this.currentUser) {
            alert('로그인이 필요한 기능입니다.');
            this.openAuthModal('login');
            return;
        }

        try {
            const res = await this.fetchWithAuth('/api/wishlist/toggle', {
                method: 'POST',
                body: JSON.stringify({ productId: Number(productId) })
            });
            const data = await res.json();
            if (res.ok) {
                await this.loadWishlist();
                this.loadProducts(); // Update heart icons on product grid
                const btn = document.getElementById(`modalWishBtn-${productId}`);
                if (btn) {
                    const wishlisted = this.isWishlisted(productId);
                    btn.className = `btn ${wishlisted ? 'btn-danger' : 'btn-outline'} btn-lg`;
                    btn.textContent = wishlisted ? '찜 해제' : '찜하기';
                }
                if (document.getElementById('wishlistModal').classList.contains('open')) {
                    this.renderWishlistItems();
                }
            } else {
                alert(data.message || '위시리스트 처리 실패');
            }
        } catch (e) {
            alert('위시리스트 오류: ' + e.message);
        }
    },

    openWishlistModal(tab = 'items') {
        if (!this.currentUser) {
            alert('로그인이 필요한 기능입니다.');
            this.openAuthModal('login');
            return;
        }
        this.openModal('wishlistModal');
        this.switchWishlistTab(tab);
    },

    switchWishlistTab(tab) {
        const tabWish = document.getElementById('tabWishlistBtn');
        const tabDeck = document.getElementById('tabDecksBtn');
        const tabCreate = document.getElementById('tabCreateDeckBtn');

        if (tabWish) tabWish.classList.toggle('active', tab === 'items');
        if (tabDeck) tabDeck.classList.toggle('active', tab === 'decks');
        if (tabCreate) tabCreate.classList.toggle('active', tab === 'create');

        const paneWish = document.getElementById('wishlistItemsPane');
        const paneDeck = document.getElementById('wishlistDecksPane');
        const paneCreate = document.getElementById('wishlistCreatePane');

        if (paneWish) paneWish.style.display = (tab === 'items') ? 'block' : 'none';
        if (paneDeck) paneDeck.style.display = (tab === 'decks') ? 'block' : 'none';
        if (paneCreate) paneCreate.style.display = (tab === 'create') ? 'block' : 'none';

        if (tab === 'items') {
            this.renderWishlistItems();
        } else if (tab === 'decks') {
            this.loadMyDecks();
        } else if (tab === 'create') {
            this.renderDeckProductChecklist();
        }
    },

    renderWishlistItems() {
        const container = document.getElementById('wishlistItemsList');
        if (!container) return;

        if (!this.wishlist || this.wishlist.length === 0) {
            container.innerHTML = `
                <div class="text-muted" style="grid-column: 1 / -1; padding: 40px; text-align: center;">
                    <p style="font-size: 1.1rem; margin-bottom: 8px;">찜한 상품이 없습니다.</p>
                    <p style="font-size: 0.9rem;">스토어에서 관심 있는 사이버 하드웨어를 찜해 나만의 커스텀 덱을 만들어보세요.</p>
                </div>`;
            return;
        }

        container.innerHTML = this.wishlist.map(item => `
            <div class="card-glass" style="padding: 16px; display: flex; flex-direction: column; justify-content: space-between;">
                <div>
                    <img src="${item.productImageUrl || 'https://images.unsplash.com/photo-1550751827-4bd374c3f58b?w=600'}" alt="${item.productName}" style="width: 100%; height: 140px; object-fit: cover; border-radius: var(--radius-sm); margin-bottom: 12px;">
                    <span class="product-cat-tag">${item.productCategory || 'HARDWARE'}</span>
                    <h4 style="margin: 8px 0 4px; font-size: 1rem;">${item.productName}</h4>
                    <p class="text-success" style="font-weight: 700; margin-bottom: 8px;">₩${Number(item.productPrice || 0).toLocaleString()}</p>
                </div>
                <div style="display: flex; gap: 8px; margin-top: 12px;">
                    <button class="btn btn-primary btn-sm" style="flex: 1;" onclick="app.addToCartQuick(${item.productId}, ${item.productPrice || 0})">담기</button>
                    <button class="btn btn-outline btn-sm" style="color: #ef4444; border-color: #ef4444;" onclick="app.toggleWishlist(${item.productId})">삭제</button>
                </div>
            </div>
        `).join('');
    },

    async loadMyDecks() {
        const container = document.getElementById('myDecksList');
        if (!container) return;
        container.innerHTML = '<p class="text-muted">커스텀 덱을 불러오는 중...</p>';

        try {
            const res = await this.fetchWithAuth('/api/wishlist/decks');
            if (!res.ok) throw new Error('덱 목록을 불러오지 못했습니다.');
            const decks = await res.json();
            this.myDecks = decks;

            if (decks.length === 0) {
                container.innerHTML = `
                    <div class="text-muted" style="padding: 40px; text-align: center;">
                        <p style="font-size: 1.1rem; margin-bottom: 8px;">생성된 커스텀 덱이 없습니다.</p>
                        <p style="font-size: 0.9rem;">[+ 새 덱 만들기] 탭에서 찜한 하드웨어 부품들을 조합해 나만의 덱을 공유해보세요.</p>
                    </div>`;
                return;
            }

            container.innerHTML = decks.map(d => `
                <div class="deck-card mb-3">
                    <div class="deck-header">
                        <div>
                            <span class="deck-title">${d.deckName}</span>
                            <span class="deck-badge ${d.isPublic ? 'badge-public' : 'badge-private'}">
                                ${d.isPublic ? '[PUBLIC 공개]' : '[PRIVATE 기밀]'}
                            </span>
                        </div>
                        <div style="display: flex; gap: 8px;">
                            <button class="btn btn-outline btn-sm" onclick="app.viewDeck(${d.id})">상세</button>
                            <button class="btn btn-ghost btn-sm" style="color: #ef4444;" onclick="app.deleteDeck(${d.id})">삭제</button>
                        </div>
                    </div>
                    <!-- Stored XSS sink: deck description rendered via innerHTML -->
                    <div class="deck-desc">${d.description || '설명 없음'}</div>
                    ${d.secretNote ? `
                        <div class="deck-secret-note">
                            <strong>SECRET NOTE:</strong> ${d.secretNote}
                        </div>
                    ` : ''}
                    <div class="deck-items-summary mt-2" style="font-size: 0.85rem; color: var(--text-secondary);">
                        구성 부품: <strong>${d.items ? d.items.length : 0}개</strong>
                        ${d.items && d.items.length > 0 ? `(${d.items.map(i => i.productName).slice(0, 3).join(', ')}${d.items.length > 3 ? ' ...' : ''})` : ''}
                    </div>
                    <div class="mt-3" style="display: flex; gap: 8px; align-items: center;">
                        <button class="btn btn-primary btn-sm" onclick="app.copyDeckShareUrl('${d.shareToken}')">
                            공유 링크 복사
                        </button>
                        <span style="font-size: 0.75rem; color: var(--text-muted);">토큰: <code>${d.shareToken}</code></span>
                    </div>
                </div>
            `).join('');

        } catch (e) {
            container.innerHTML = `<div class="text-danger">${e.message}</div>`;
        }
    },

    renderDeckProductChecklist() {
        const container = document.getElementById('deckProductChecklist');
        if (!container) return;

        if (!this.wishlist || this.wishlist.length === 0) {
            container.innerHTML = '<span class="text-muted">찜한 상품이 없습니다. 먼저 상품을 찜해주세요.</span>';
            return;
        }

        container.innerHTML = this.wishlist.map(w => `
            <label style="display: flex; align-items: center; gap: 8px; cursor: pointer; padding: 4px 0;">
                <input type="checkbox" name="deckSelectedProduct" value="${w.productId}" checked>
                <span style="font-size: 0.9rem;">${w.productName} (₩${Number(w.productPrice || 0).toLocaleString()})</span>
            </label>
        `).join('');
    },

    async submitCreateDeck() {
        const name = document.getElementById('deckNameInput').value.trim();
        const desc = document.getElementById('deckDescInput').value.trim();
        const secretNote = document.getElementById('deckSecretNoteInput').value.trim();
        const isPublic = document.getElementById('deckIsPublicCheck').checked;

        const checkedBoxes = document.querySelectorAll('input[name="deckSelectedProduct"]:checked');
        const productIds = Array.from(checkedBoxes).map(cb => Number(cb.value));

        if (!name) {
            alert('덱 명칭을 입력해주세요.');
            return;
        }

        try {
            const res = await this.fetchWithAuth('/api/wishlist/decks', {
                method: 'POST',
                body: JSON.stringify({
                    deckName: name,
                    description: desc,
                    secretNote: secretNote,
                    isPublic: isPublic,
                    productIds: productIds
                })
            });

            const data = await res.json();
            if (res.ok) {
                alert(`[커스텀 덱 생성 완료!]\n공유 토큰: ${data.shareToken}\n공유 링크가 생성되었습니다.`);
                document.getElementById('deckNameInput').value = '';
                document.getElementById('deckDescInput').value = '';
                document.getElementById('deckSecretNoteInput').value = '';
                this.switchWishlistTab('decks');
            } else {
                alert(data.message || '덱 생성 실패');
            }
        } catch (e) {
            alert('덱 생성 오류: ' + e.message);
        }
    },

    async deleteDeck(deckId) {
        if (!confirm('이 커스텀 덱을 삭제하시겠습니까?')) return;
        try {
            const res = await this.fetchWithAuth(`/api/wishlist/decks/${deckId}`, {
                method: 'DELETE'
            });
            const data = await res.json();
            if (res.ok) {
                alert(data.message);
                this.loadMyDecks();
            } else {
                alert(data.message || '삭제 실패');
            }
        } catch (e) {
            alert('오류: ' + e.message);
        }
    },

    copyDeckShareUrl(token) {
        const url = `${window.location.origin}/?deckToken=${token}`;
        navigator.clipboard.writeText(url).then(() => {
            alert(`공유 링크가 클립보드에 복사되었습니다:\n${url}`);
        }).catch(() => {
            prompt('공유 링크를 복사하세요:', url);
        });
    },

    async viewDeck(deckId) {
        try {
            // BOLA / IDOR vulnerability test: user can request any deckId directly
            const res = await this.fetchWithAuth(`/api/wishlist/decks/${deckId}`);
            if (!res.ok) throw new Error('덱 정보를 조회할 수 없습니다. (ID: ' + deckId + ')');
            const deck = await res.json();

            let itemsList = (deck.items && deck.items.length > 0)
                ? deck.items.map(i => `• ${i.productName} (₩${Number(i.productPrice || 0).toLocaleString()})`).join('\n')
                : '(등록된 부품 없음)';

            let msg = `[커스텀 덱 상세 조회]\n` +
                      `ID: ${deck.id}\n` +
                      `소유자: ${deck.ownerUsername || 'User #' + deck.userId}\n` +
                      `덱 명칭: ${deck.deckName}\n` +
                      `공개 여부: ${deck.isPublic ? '공개 (PUBLIC)' : '비공개 (PRIVATE/기밀)'}\n` +
                      `소개: ${deck.description || '없음'}\n` +
                      (deck.secretNote ? `기밀 메모: ${deck.secretNote}\n` : '') +
                      `\n[구성 부품 목록]\n${itemsList}`;
            alert(msg);
        } catch (e) {
            alert('덱 조회 오류: ' + e.message);
        }
    },

    async viewDeckByToken(token) {
        try {
            const res = await fetch(`/api/wishlist/decks/share/${token}`);
            if (!res.ok) throw new Error('공유된 덱을 찾을 수 없습니다.');
            const deck = await res.json();

            let itemsList = (deck.items && deck.items.length > 0)
                ? deck.items.map(i => `• ${i.productName} (₩${Number(i.productPrice || 0).toLocaleString()})`).join('\n')
                : '(등록된 부품 없음)';

            let msg = `[공유받은 커스텀 덱: ${deck.deckName}]\n` +
                      `작성자: ${deck.ownerUsername || '익명'}\n` +
                      `설명: ${deck.description || '없음'}\n` +
                      `\n[구성 부품 목록]\n${itemsList}`;
            alert(msg);
        } catch (e) {
            console.error('Share deck error:', e);
        }
    },

    // ==========================================
    // Order Refund & Cancellation
    // ==========================================
    openRefundModal(orderId, totalAmount, status) {
        document.getElementById('refundOrderId').value = orderId;
        document.getElementById('refundOrderDisplay').textContent = `#${orderId} (${status})`;
        document.getElementById('refundAmountDisplay').textContent = `₩${Number(totalAmount).toLocaleString()}`;
        document.getElementById('refundReasonDetails').value = '';
        document.getElementById('refundDirectBypassCheck').checked = false;
        this.openModal('refundModal');
    },

    async submitRefundRequest() {
        const orderId = document.getElementById('refundOrderId').value;
        const reasonSelect = document.getElementById('refundReasonSelect').value;
        const details = document.getElementById('refundReasonDetails').value.trim();
        const direct = document.getElementById('refundDirectBypassCheck').checked;

        const reason = details ? `${reasonSelect} - ${details}` : reasonSelect;

        try {
            const res = await this.fetchWithAuth(`/api/orders/${orderId}/refund`, {
                method: 'POST',
                body: JSON.stringify({
                    refundAmount: null,
                    reason: reasonSelect,
                    reasonDetails: details,
                    refundReason: reason,
                    direct: direct
                })
            });

            const data = await res.json();
            if (res.ok) {
                const amount = data.refundedAmount !== undefined ? data.refundedAmount : data.refundAmount;
                alert(`[환불 승인 완료]\n환불 금액: ₩${Number(amount || 0).toLocaleString()}\n${data.message}`);
                this.closeModal('refundModal');
                await this.fetchProfile();
                await this.loadMyOrders();
            } else {
                alert(`[경고] 환불 실패: ${data.message || '요청이 거부되었습니다.'}`);
            }
        } catch (e) {
            alert('환불 요청 오류: ' + e.message);
        }
    },

    // ==========================================
    // Feature 1: Gamification (Attendance & Roulette)
    // ==========================================
    rouletteAngle: 0,
    rouletteSpinning: false,
    roulettePrizes: [100, 5000, 300, 2000, 500, 1000],
    rouletteColors: ['#00f2fe', '#ffd700', '#4facfe', '#ff0080', '#7928ca', '#10b981'],

    async openRouletteModal() {
        if (!this.token) {
            this.openAuthModal('login');
            return;
        }
        this.openModal('rouletteModal');
        await this.loadPointStatus();
        this.drawRouletteWheel(this.rouletteAngle);
    },

    async loadPointStatus() {
        try {
            const res = await this.fetchWithAuth('/api/points/status');
            if (res.ok) {
                const data = await res.json();
                const statusEl = document.getElementById('attendanceStatusText');
                const btnClaim = document.getElementById('btnClaimAttendance');
                if (data.attendedToday) {
                    statusEl.innerHTML = `<strong style="color:var(--color-success);">오늘 출석 완료</strong> (${data.todayDate})`;
                    if (btnClaim) {
                        btnClaim.disabled = true;
                        btnClaim.textContent = '출석 완료됨';
                        btnClaim.style.opacity = '0.6';
                    }
                } else {
                    statusEl.innerHTML = `⏳ <strong style="color:#f59e0b;">미출석 상태</strong> (+1,000P 수령 가능)`;
                    if (btnClaim) {
                        btnClaim.disabled = false;
                        btnClaim.textContent = '출석체크 (+1,000 P)';
                        btnClaim.style.opacity = '1';
                    }
                }
                if (this.currentUser) {
                    this.currentUser.points = data.points;
                    this.updateNavUI();
                }
            }
        } catch (e) {
            console.error('Point status load error:', e);
        }
    },

    drawRouletteWheel(angle) {
        const canvas = document.getElementById('rouletteCanvas');
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        const numSlices = this.roulettePrizes.length;
        const sliceAngle = (2 * Math.PI) / numSlices;
        const centerX = canvas.width / 2;
        const centerY = canvas.height / 2;
        const radius = centerX - 8;

        ctx.clearRect(0, 0, canvas.width, canvas.height);

        for (let i = 0; i < numSlices; i++) {
            const startAngle = angle + (i * sliceAngle);
            const endAngle = startAngle + sliceAngle;

            ctx.beginPath();
            ctx.moveTo(centerX, centerY);
            ctx.arc(centerX, centerY, radius, startAngle, endAngle);
            ctx.closePath();

            ctx.fillStyle = this.rouletteColors[i % this.rouletteColors.length];
            ctx.fill();
            ctx.lineWidth = 2;
            ctx.strokeStyle = '#0a0d14';
            ctx.stroke();

            // Prize Text
            ctx.save();
            ctx.translate(centerX, centerY);
            ctx.rotate(startAngle + sliceAngle / 2);
            ctx.textAlign = 'right';
            ctx.fillStyle = '#ffffff';
            ctx.font = 'bold 15px Outfit, sans-serif';
            ctx.shadowColor = 'rgba(0,0,0,0.8)';
            ctx.shadowBlur = 4;
            ctx.fillText(`${this.roulettePrizes[i].toLocaleString()}P`, radius - 18, 5);
            ctx.restore();
        }

        // Center hub
        ctx.beginPath();
        ctx.arc(centerX, centerY, 28, 0, 2 * Math.PI);
        ctx.fillStyle = '#101522';
        ctx.fill();
        ctx.lineWidth = 3;
        ctx.strokeStyle = '#00f2fe';
        ctx.stroke();

        ctx.fillStyle = '#00f2fe';
        ctx.font = 'bold 12px Pretendard, sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText('NEXUS', centerX, centerY);
    },

    async claimAttendance(customDate = null) {
        const btn = document.getElementById('btnClaimAttendance');
        if (btn) btn.disabled = true;

        try {
            const body = customDate ? { customDate } : {};
            const res = await this.fetchWithAuth('/api/points/attendance', {
                method: 'POST',
                body: JSON.stringify(body)
            });
            const data = await res.json();
            if (res.ok) {
                alert(`[출석체크 성공]\n${data.message}\n현재 보유 포인트: ${Number(data.currentPoints).toLocaleString()} P`);
                await this.loadPointStatus();
                await this.fetchProfile();
            } else {
                alert(data.message || '출석체크 실패');
                if (btn) btn.disabled = false;
            }
        } catch (e) {
            alert('출석체크 오류: ' + e.message);
            if (btn) btn.disabled = false;
        }
    },

    async spinRoulette() {
        if (this.rouletteSpinning) return;
        this.rouletteSpinning = true;
        const btn = document.getElementById('btnSpinRoulette');
        if (btn) {
            btn.disabled = true;
            btn.textContent = '룰렛 회전 중...';
        }

        // Server request to get prize
        let serverResult = null;
        try {
            const res = await this.fetchWithAuth('/api/points/roulette', {
                method: 'POST',
                body: JSON.stringify({})
            });
            serverResult = await res.json();
        } catch (e) {
            console.error(e);
        }

        const prizePoints = (serverResult && serverResult.prizePoints) ? serverResult.prizePoints : 500;
        let targetIndex = this.roulettePrizes.indexOf(prizePoints);
        if (targetIndex === -1) targetIndex = 0;

        const numSlices = this.roulettePrizes.length;
        const sliceAngle = (2 * Math.PI) / numSlices;
        // Needle points at top (3 * Math.PI / 2)
        const targetSectorCenter = targetIndex * sliceAngle + sliceAngle / 2;
        const targetAngle = (3 * Math.PI / 2) - targetSectorCenter + (Math.PI * 2 * 6); // 6 full rotations

        const startTime = performance.now();
        const duration = 3500;
        const startAngle = this.rouletteAngle % (Math.PI * 2);

        const animate = (currentTime) => {
            const elapsed = currentTime - startTime;
            const progress = Math.min(elapsed / duration, 1);
            // Ease out cubic
            const ease = 1 - Math.pow(1 - progress, 3);
            const currentAngle = startAngle + (targetAngle - startAngle) * ease;
            this.rouletteAngle = currentAngle;
            this.drawRouletteWheel(currentAngle);

            if (progress < 1) {
                requestAnimationFrame(animate);
            } else {
                this.rouletteSpinning = false;
                if (btn) {
                    btn.disabled = false;
                    btn.textContent = '룰렛 돌리기 (무료)';
                }
                alert(`축하합니다!\n사이버 룰렛에서 ${prizePoints.toLocaleString()} P 당첨되었습니다!`);
                this.fetchProfile();
            }
        };

        requestAnimationFrame(animate);
    },

    // ==========================================
    // Feature 3: Sold-Out Restock Notification Webhook
    // ==========================================
    openRestockModal(productId, productName) {
        document.getElementById('restockProductId').value = productId;
        document.getElementById('restockProdName').textContent = productName;
        const emailInput = document.getElementById('restockEmail');
        if (emailInput && this.currentUser) {
            emailInput.value = this.currentUser.email || '';
        }
        document.getElementById('restockWebhookUrl').value = '';
        this.openModal('restockModal');
    },

    async submitRestockSubscription() {
        const productId = document.getElementById('restockProductId').value;
        const email = document.getElementById('restockEmail').value.trim();
        const webhookUrl = document.getElementById('restockWebhookUrl').value.trim();

        if (!email && !webhookUrl) {
            alert('이메일 또는 Webhook URL 중 하나는 반드시 입력해야 합니다.');
            return;
        }

        try {
            const res = await this.fetchWithAuth(`/api/products/${productId}/notify-restock`, {
                method: 'POST',
                body: JSON.stringify({ email, webhookUrl })
            });
            const data = await res.json();
            if (res.ok) {
                let msg = `[재입고 알림 신청 완료]\n${data.message}\n상품: ${data.productName}`;
                if (data.webhookVerification) {
                    msg += `\n\n[Webhook 실시간 응답 진단]:\n${data.webhookVerification}`;
                }
                alert(msg);
                this.closeModal('restockModal');
            } else {
                alert(`신청 실패: ${data.message || '오류 발생'}`);
            }
        } catch (e) {
            alert('재입고 알림 신청 오류: ' + e.message);
        }
    },

    // ==========================================
    // Feature 4: Electronic Tax Invoice & Printable Receipt
    // ==========================================
    async openReceiptModal(orderId) {
        try {
            const res = await this.fetchWithAuth(`/api/orders/${orderId}/receipt`);
            if (!res.ok) {
                const err = await res.json().catch(() => ({ message: '영수증을 불러올 수 없습니다.' }));
                alert(err.message || '영수증 조회 실패');
                return;
            }
            const data = await res.json();
            const o = data.order;
            const items = data.items || [];
            const fin = data.financial || {};

            document.getElementById('recOrderId').textContent = `#${o.id}`;
            document.getElementById('recOrderDate').textContent = o.createdAt ? String(o.createdAt).replace('T', ' ').substring(0, 19) : '-';
            document.getElementById('recRecipient').textContent = `${o.recipientName} (${o.phone})`;
            document.getElementById('recAddress').textContent = o.shippingAddress;

            const tbody = document.getElementById('recItemsBody');
            tbody.innerHTML = items.map(it => `
                <tr>
                    <td><strong>${it.productName || '상품 #' + it.productId}</strong></td>
                    <td style="text-align:right;">${it.quantity}개</td>
                    <td style="text-align:right;">₩${Number(it.price).toLocaleString()}</td>
                    <td style="text-align:right; font-weight:600;">₩${Number(it.price * it.quantity).toLocaleString()}</td>
                </tr>
            `).join('');

            document.getElementById('recSubtotal').textContent = `₩${Number(fin.supplyAmount || 0).toLocaleString()}`;
            document.getElementById('recVat').textContent = `₩${Number(fin.vat || 0).toLocaleString()}`;
            document.getElementById('recPointsDiscount').textContent = `- ₩${Number(fin.pointsDiscount || 0).toLocaleString()}`;
            document.getElementById('recTotalAmount').textContent = `₩${Number(fin.finalPaidAmount || o.totalAmount).toLocaleString()}`;

            const signEl = document.getElementById('recSignHash');
            if (signEl) signEl.textContent = data.signature || 'NEXUS-VALIDATED-STAMP';

            this.openModal('receiptModal');
        } catch (e) {
            alert('영수증 출력 오류: ' + e.message);
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

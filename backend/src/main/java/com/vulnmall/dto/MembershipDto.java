package com.vulnmall.dto;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

public class MembershipDto {

    public static class SubscribeRequest {
        private String tier; // e.g. "PRIME"
        private BigDecimal price; // e.g. 9900.00 (Price Tampering 포인트)
        private String welcomeNote; // Stored XSS 포인트

        public SubscribeRequest() {}

        public String getTier() { return tier; }
        public void setTier(String tier) { this.tier = tier; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public String getWelcomeNote() { return welcomeNote; }
        public void setWelcomeNote(String welcomeNote) { this.welcomeNote = welcomeNote; }
    }

    public static class StatusResponse {
        private String username;
        private String tier;
        private boolean active;
        private String welcomeNote;
        private Timestamp expiresAt;
        private List<String> benefits;

        public StatusResponse(String username, String tier, boolean active, String welcomeNote, Timestamp expiresAt, List<String> benefits) {
            this.username = username;
            this.tier = tier;
            this.active = active;
            this.welcomeNote = welcomeNote;
            this.expiresAt = expiresAt;
            this.benefits = benefits;
        }

        public String getUsername() { return username; }
        public String getTier() { return tier; }
        public boolean isActive() { return active; }
        public String getWelcomeNote() { return welcomeNote; }
        public Timestamp getExpiresAt() { return expiresAt; }
        public List<String> getBenefits() { return benefits; }
    }

    public static class IssueCouponRequest {
        private String couponType; // "VIP_50K", "FREE_SHIPPING"

        public IssueCouponRequest() {}
        public String getCouponType() { return couponType; }
        public void setCouponType(String couponType) { this.couponType = couponType; }
    }
}

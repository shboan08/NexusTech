package com.vulnmall.dto;

import java.math.BigDecimal;

public class OrderDto {

    public static class CheckoutRequest {
        private String recipientName;
        private String shippingAddress;
        private String phone;
        private BigDecimal totalAmount; // [비즈니스 로직 결함] 서버 재계산 없이 클라이언트 전달 금액 신뢰
        private String couponCode;
        private Integer pointsUsed;

        public String getRecipientName() { return recipientName; }
        public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
        public String getShippingAddress() { return shippingAddress; }
        public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public String getCouponCode() { return couponCode; }
        public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
        public Integer getPointsUsed() { return pointsUsed; }
        public void setPointsUsed(Integer pointsUsed) { this.pointsUsed = pointsUsed; }
    }

    public static class UpdateShippingRequest {
        private String recipientName;
        private String shippingAddress;
        private String phone;

        public String getRecipientName() { return recipientName; }
        public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
        public String getShippingAddress() { return shippingAddress; }
        public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    public static class XmlReceiptRequest {
        private String xmlData; // [XXE 대상] 외부 엔티티가 포함된 XML 영수증 데이터

        public String getXmlData() { return xmlData; }
        public void setXmlData(String xmlData) { this.xmlData = xmlData; }
    }
}

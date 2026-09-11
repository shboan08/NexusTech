package com.vulnmall.dto;

import java.math.BigDecimal;

public class CartDto {

    public static class AddItemRequest {
        private Long productId;
        private Integer quantity;
        private BigDecimal unitPrice; // 클라이언트가 단가를 임의 조작 가능

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    }

    public static class UpdateQuantityRequest {
        private Integer quantity; // 음수 수량(-1, -5 등) 전송 가능

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}

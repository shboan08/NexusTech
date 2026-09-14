package com.vulnmall.dto;

import java.math.BigDecimal;

public class WalletDto {

    public static class ChargeRequest {
        private BigDecimal amount;
        private BigDecimal paidAmount;
        private String paymentMethod;
        private String pgTransactionId;

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public BigDecimal getPaidAmount() { return paidAmount; }
        public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getPgTransactionId() { return pgTransactionId; }
        public void setPgTransactionId(String pgTransactionId) { this.pgTransactionId = pgTransactionId; }
    }

    public static class TransferRequest {
        private String toUsername;
        private BigDecimal amount;

        public String getToUsername() { return toUsername; }
        public void setToUsername(String toUsername) { this.toUsername = toUsername; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }

    public static class VoucherRedeemRequest {
        private String voucherCode;

        public String getVoucherCode() { return voucherCode; }
        public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }
    }
}

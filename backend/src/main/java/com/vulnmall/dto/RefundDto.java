package com.vulnmall.dto;

import java.math.BigDecimal;

public class RefundDto {

    public static class RefundRequest {
        private String reason;
        private String reasonDetails;
        private BigDecimal refundAmount;
        private Boolean direct;

        public RefundRequest() {}

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getReasonDetails() { return reasonDetails; }
        public void setReasonDetails(String reasonDetails) { this.reasonDetails = reasonDetails; }
        public BigDecimal getRefundAmount() { return refundAmount; }
        public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
        public Boolean getDirect() { return direct; }
        public void setDirect(Boolean direct) { this.direct = direct; }
    }
}

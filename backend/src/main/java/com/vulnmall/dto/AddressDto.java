package com.vulnmall.dto;

public class AddressDto {

    public static class CreateRequest {
        private String recipientName;
        private String phone;
        private String postalCode;
        private String addressLine1;
        private String addressLine2;
        private String deliveryMemo;
        private Boolean isDefault;

        public String getRecipientName() { return recipientName; }
        public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getPostalCode() { return postalCode; }
        public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
        public String getAddressLine1() { return addressLine1; }
        public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
        public String getAddressLine2() { return addressLine2; }
        public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
        public String getDeliveryMemo() { return deliveryMemo; }
        public void setDeliveryMemo(String deliveryMemo) { this.deliveryMemo = deliveryMemo; }
        public Boolean getIsDefault() { return isDefault; }
        public void setIsDefault(Boolean aDefault) { isDefault = aDefault; }
    }

    public static class UpdateRequest {
        private String recipientName;
        private String phone;
        private String postalCode;
        private String addressLine1;
        private String addressLine2;
        private String deliveryMemo;
        private Boolean isDefault;

        public String getRecipientName() { return recipientName; }
        public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getPostalCode() { return postalCode; }
        public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
        public String getAddressLine1() { return addressLine1; }
        public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
        public String getAddressLine2() { return addressLine2; }
        public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
        public String getDeliveryMemo() { return deliveryMemo; }
        public void setDeliveryMemo(String deliveryMemo) { this.deliveryMemo = deliveryMemo; }
        public Boolean getIsDefault() { return isDefault; }
        public void setIsDefault(Boolean aDefault) { isDefault = aDefault; }
    }
}

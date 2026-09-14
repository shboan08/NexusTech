package com.vulnmall.entity;

import java.sql.Timestamp;

public class UserAddress {
    private Long id;
    private Long userId;
    private String recipientName;
    private String phone;
    private String postalCode;
    private String addressLine1;
    private String addressLine2;
    private String deliveryMemo;
    private boolean isDefault;
    private Timestamp createdAt;

    public UserAddress() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
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
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}

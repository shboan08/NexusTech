package com.vulnmall.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class User {
    private Long id;
    private String username;
    private String password;
    private String email;
    private String role;
    private BigDecimal balance;
    private Integer points;
    private String membershipTier;
    private Boolean membershipActive;
    private String membershipWelcomeNote;
    private Timestamp membershipExpiresAt;
    private String avatarUrl;
    private String securityQuestion;
    private String securityAnswer;
    private String resetToken;
    private Timestamp createdAt;

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public Integer getPoints() { return points != null ? points : 0; }
    public void setPoints(Integer points) { this.points = points; }
    public String getMembershipTier() { return membershipTier; }
    public void setMembershipTier(String membershipTier) { this.membershipTier = membershipTier; }
    public Boolean getMembershipActive() { return membershipActive; }
    public void setMembershipActive(Boolean membershipActive) { this.membershipActive = membershipActive; }
    public String getMembershipWelcomeNote() { return membershipWelcomeNote; }
    public void setMembershipWelcomeNote(String membershipWelcomeNote) { this.membershipWelcomeNote = membershipWelcomeNote; }
    public Timestamp getMembershipExpiresAt() { return membershipExpiresAt; }
    public void setMembershipExpiresAt(Timestamp membershipExpiresAt) { this.membershipExpiresAt = membershipExpiresAt; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getSecurityQuestion() { return securityQuestion; }
    public void setSecurityQuestion(String securityQuestion) { this.securityQuestion = securityQuestion; }
    public String getSecurityAnswer() { return securityAnswer; }
    public void setSecurityAnswer(String securityAnswer) { this.securityAnswer = securityAnswer; }
    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}

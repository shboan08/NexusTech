package com.vulnmall.dto;

import com.vulnmall.entity.User;
import java.math.BigDecimal;

public class AuthDto {

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class RegisterRequest {
        private String username;
        private String password;
        private String email;
        private String securityQuestion;
        private String securityAnswer;
        private String role; // Mass assignment potential on register
        private BigDecimal balance;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getSecurityQuestion() { return securityQuestion; }
        public void setSecurityQuestion(String securityQuestion) { this.securityQuestion = securityQuestion; }
        public String getSecurityAnswer() { return securityAnswer; }
        public void setSecurityAnswer(String securityAnswer) { this.securityAnswer = securityAnswer; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
    }

    public static class ProfileUpdateRequest {
        private String email;
        private String securityQuestion;
        private String securityAnswer;
        private String role;     // Mass assignment target!
        private BigDecimal balance; // Mass assignment target!

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getSecurityQuestion() { return securityQuestion; }
        public void setSecurityQuestion(String securityQuestion) { this.securityQuestion = securityQuestion; }
        public String getSecurityAnswer() { return securityAnswer; }
        public void setSecurityAnswer(String securityAnswer) { this.securityAnswer = securityAnswer; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
    }

    public static class PasswordResetRequest {
        private String username;
        private String securityAnswer;
        private String newPassword;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getSecurityAnswer() { return securityAnswer; }
        public void setSecurityAnswer(String securityAnswer) { this.securityAnswer = securityAnswer; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    public static class AuthResponse {
        private String token;
        private User user;

        public AuthResponse(String token, User user) {
            this.token = token;
            this.user = user;
        }

        public String getToken() { return token; }
        public User getUser() { return user; }
    }
}

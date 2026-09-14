package com.vulnmall.entity;

import java.sql.Timestamp;
import java.util.List;

public class CustomDeck {
    private Long id;
    private Long userId;
    private String username;
    private String deckName;
    private String description;
    private String secretNote;
    private Boolean isPublic;
    private String shareToken;
    private Timestamp createdAt;
    private List<Product> products;

    public CustomDeck() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDeckName() { return deckName; }
    public void setDeckName(String deckName) { this.deckName = deckName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSecretNote() { return secretNote; }
    public void setSecretNote(String secretNote) { this.secretNote = secretNote; }
    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public List<Product> getProducts() { return products; }
    public void setProducts(List<Product> products) { this.products = products; }
}

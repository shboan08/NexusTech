package com.vulnmall.dto;

import java.util.List;

public class WishlistDto {

    public static class ToggleRequest {
        private Long productId;

        public ToggleRequest() {}
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
    }

    public static class CreateDeckRequest {
        private String deckName;
        private String description;
        private String secretNote;
        private Boolean isPublic;
        private List<Long> productIds;

        public CreateDeckRequest() {}

        public String getDeckName() { return deckName; }
        public void setDeckName(String deckName) { this.deckName = deckName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSecretNote() { return secretNote; }
        public void setSecretNote(String secretNote) { this.secretNote = secretNote; }
        public Boolean getIsPublic() { return isPublic; }
        public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
        public List<Long> getProductIds() { return productIds; }
        public void setProductIds(List<Long> productIds) { this.productIds = productIds; }
    }
}

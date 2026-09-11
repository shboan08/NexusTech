package com.vulnmall.dto;

public class UtilDto {

    public static class PingRequest {
        private String targetHost;

        public String getTargetHost() { return targetHost; }
        public void setTargetHost(String targetHost) { this.targetHost = targetHost; }
    }

    public static class FetchImageRequest {
        private String imageUrl;

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }
}

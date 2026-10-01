package com.tgaws.business.mon.entity;

/**
 * 采集网关实体（mon_gateway 行）。
 */
public class GatewayEntity {

    private Long id;
    private String gatewayCode;
    private String gatewayName;
    private String secret;
    private Integer protocol;
    private Integer scale;
    private Integer status;
    private Integer onlineStatus;
    private java.time.LocalDateTime lastOnlineTime;

    /** 列表标志：是否已配置 PSK（不解密、不返回密钥，评审 4.3） */
    private Boolean secretConfigured;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGatewayCode() {
        return gatewayCode;
    }

    public void setGatewayCode(String gatewayCode) {
        this.gatewayCode = gatewayCode;
    }

    public String getGatewayName() {
        return gatewayName;
    }

    public void setGatewayName(String gatewayName) {
        this.gatewayName = gatewayName;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Integer getProtocol() {
        return protocol;
    }

    public void setProtocol(Integer protocol) {
        this.protocol = protocol;
    }

    public Integer getScale() {
        return scale;
    }

    public void setScale(Integer scale) {
        this.scale = scale;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getOnlineStatus() {
        return onlineStatus;
    }

    public void setOnlineStatus(Integer onlineStatus) {
        this.onlineStatus = onlineStatus;
    }

    public java.time.LocalDateTime getLastOnlineTime() {
        return lastOnlineTime;
    }

    public void setLastOnlineTime(java.time.LocalDateTime lastOnlineTime) {
        this.lastOnlineTime = lastOnlineTime;
    }

    public Boolean getSecretConfigured() {
        return secretConfigured;
    }

    public void setSecretConfigured(Boolean secretConfigured) {
        this.secretConfigured = secretConfigured;
    }
}

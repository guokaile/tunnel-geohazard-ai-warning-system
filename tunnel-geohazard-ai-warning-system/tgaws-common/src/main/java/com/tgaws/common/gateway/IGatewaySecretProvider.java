package com.tgaws.common.gateway;

/**
 * 网关凭据提供契约（common 契约层；实现由业务层提供 mon_gateway 查询）。
 */
public interface IGatewaySecretProvider {

    /**
     * 查询网关凭据。
     *
     * @param gatewayCode 网关编号
     * @return 未注册/已停用返回 null（注册校验返回结果码 2 网关未注册）
     */
    GatewayCredential credentialOf(String gatewayCode);
}

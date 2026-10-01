package com.tgaws.web.controller;

import com.tgaws.business.mon.manager.GatewayService;
import com.tgaws.business.mon.vo.GatewayVo;
import com.tgaws.common.result.Result;
import com.tgaws.web.security.RequirePermission;
import com.tgaws.web.log.OperLog;
import com.tgaws.web.security.RateLimit;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 网关台账接口（T-602；VO 类型层无 secret 字段——密文绝不外泄，评审 4.2）。
 */
@RestController
@RequestMapping("/api/v1/mon/gateways")
public class GatewayController {

    private final GatewayService gatewayService;

    public GatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @GetMapping
    @RequirePermission("mon:gateway:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<List<GatewayVo>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(gatewayService.list(keyword));
    }

    @GetMapping("/{id}")
    @RequirePermission("mon:gateway:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<GatewayVo> get(@PathVariable long id) {
        return Result.ok(gatewayService.get(id));
    }

    @PostMapping
    @RequirePermission("mon:gateway:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "创建网关", module = "工程台账")
    public Result<Map<String, Long>> create(@RequestBody GatewayService.CreateCmd cmd) {
        return Result.ok(Map.of("id", gatewayService.create(cmd)));
    }

    /** 更新语义（评审 4.4）：secret 不传=不变更；清空需显式改密钥 */
    @PutMapping("/{id}")
    @RequirePermission("mon:gateway:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "修改网关", module = "工程台账")
    public Result<Void> update(@PathVariable long id, @RequestBody GatewayService.UpdateCmd cmd) {
        gatewayService.update(id, cmd);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @RequirePermission("mon:gateway:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "网关启停", module = "工程台账")
    public Result<Void> updateStatus(@PathVariable long id, @RequestBody Map<String, Integer> body) {
        gatewayService.updateStatus(id, body.getOrDefault("status", 1));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("mon:gateway:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "删除网关", module = "工程台账")
    public Result<Void> delete(@PathVariable long id) {
        gatewayService.delete(id);
        return Result.ok();
    }
}

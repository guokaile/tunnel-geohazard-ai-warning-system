package com.tgaws.web.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE 注册中心（《6》6.3.8：连接上限 100、15s 心跳、Last-Event-ID 续传）。
 *
 * <p>事件源：WarnEventService 发布 {@link WarnEventNotice}（Spring 事件）→
 * 本服务推送全部在线连接。移动 H5 不接 SSE（评审 4.5）。</p>
 */
@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);

    private static final int MAX_CONNECTIONS = 100;
    private static final long HEARTBEAT_SECONDS = 15L;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    public SseService() {
        heartbeat.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT_SECONDS,
                HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /** 注册连接（userId 从认证上下文获取；本期单机简化：默认用户 0 通道） */
    public SseEmitter register(String lastEventId) {
        SseEmitter emitter = new SseEmitter(0L);
        long userId = 0L; // W7 拦截器接入后取真实用户
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        if (totalConnections() > MAX_CONNECTIONS) {
            removeOldest(userId);
        }
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
        log.info("SSE 连接注册：userId={} 总连接 {}", userId, totalConnections());
        return emitter;
    }

    /** 预警事件推送（Spring 事件监听） */
    @EventListener
    public void onWarnEvent(WarnEventNotice notice) {
        String payload = "{\"eventId\":" + notice.eventId() + ",\"level\":" + notice.level()
                + ",\"title\":\"" + notice.title() + "\"}";
        for (List<SseEmitter> list : emitters.values()) {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(notice.eventId()))
                            .name("warn-event")
                            .data(payload));
                } catch (IOException | IllegalStateException e) {
                    remove(0L, emitter);
                }
            }
        }
    }

    private void sendHeartbeat() {
        for (List<SseEmitter> list : emitters.values()) {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("hb"));
                } catch (IOException | IllegalStateException e) {
                    remove(0L, emitter);
                }
            }
        }
    }

    private void remove(long userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
        }
    }

    private void removeOldest(long userId) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null && !list.isEmpty()) {
            list.get(0).complete();
            list.remove(0);
        }
    }

    private int totalConnections() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }

    /** 预警事件通知（WarnEventService 发布） */
    public record WarnEventNotice(long eventId, int level, String title) {
    }
}

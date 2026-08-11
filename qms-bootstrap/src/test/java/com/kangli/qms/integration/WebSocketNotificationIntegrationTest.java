package com.kangli.qms.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebSocket 通知实时推送集成测试（D10）：登录 -> STOMP 连接 /ws?token -> 订阅 /user/queue/notifications
 * -> 创建通知 -> 应收到推送帧。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketNotificationIntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;

    @Test
    void notification_pushedViaWebSocket() throws Exception {
        // 登录拿 token + userId
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        String login = rest.postForObject("/api/v1/auth/login",
                new HttpEntity<>(json.writeValueAsString(Map.of("account", "qms_admin", "password", "123456")), jsonHeaders),
                String.class);
        String token = JsonPath.read(login, "$.data.token");
        Number uid = JsonPath.read(login, "$.data.userInfo.userId");

        // STOMP over SockJS 连接
        WebSocketStompClient client = new WebSocketStompClient(new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        CountDownLatch pushed = new CountDownLatch(1);

        StompSession session = client.connect("http://localhost:" + port + "/ws?token=" + token,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession s, StompHeaders headers) {
                        s.subscribe("/user/queue/notifications", new StompFrameHandler() {
                            @Override
                            public Type getPayloadType(StompHeaders headers) {
                                return Object.class;
                            }

                            @Override
                            public void handleFrame(StompHeaders headers, Object payload) {
                                pushed.countDown();
                            }
                        });
                    }
                }).get(5, TimeUnit.SECONDS);

        // 创建通知给该用户（触发 convertAndSendToUser）
        rest.postForEntity("/api/v1/notifications",
                new HttpEntity<>(json.writeValueAsString(Map.of(
                        "userId", uid.longValue(), "type", "EXCEPTION",
                        "title", "WS集成测试", "content", "实时推送", "level", "提醒", "plantCode", "SZ")), jsonHeaders),
                String.class);

        assertTrue(pushed.await(5, TimeUnit.SECONDS), "5s 内未收到 WebSocket 推送帧");
        session.disconnect();
    }
}

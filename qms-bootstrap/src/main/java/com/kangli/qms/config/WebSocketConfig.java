package com.kangli.qms.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * STOMP over WebSocket 配置（D10：实时通知）。
 * <p>端点：/ws（SockJS 兼容）。鉴权不在握手期进行（SockJS 握手无法安全携带自定义头，
 * URL query 传 token 会泄露到访问日志/浏览器历史/Referer），而是由
 * {@link StompAuthChannelInterceptor} 在 STOMP CONNECT 帧上校验
 * {@code Authorization: Bearer} 头（含 authVersion/用户状态，与 HTTP 层 JwtInterceptor 一致），
 * 并绑定 {@link StompPrincipal} 支持 {@code /user/{userId}/queue/notifications} 定向推送。</p>
 * <p>跨域：允许源白名单由配置项 {@code qms.websocket.allowed-origins} 控制，
 * 默认仅放行本地开发地址；生产域名通过配置注入，不再使用 {@code *} 通配。</p>
 * <p>注意：当前使用内存版 {@code enableSimpleBroker}，适用于单实例部署（已确认当前拓扑为单实例）；
 * 若未来多实例横向扩展，应替换为 {@code enableStompBrokerRelay} 接 RabbitMQ 等外部消息代理。</p>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    /** WebSocket 允许的跨域来源白名单（默认仅本地开发地址，生产由配置文件注入） */
    @Value("${qms.websocket.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private List<String> allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // CONNECT 帧鉴权：token 经数据通道传输，不进 URL；校验失败回 ERROR 帧并断连
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 客户端可订阅的前缀：广播 /topic，点对点 /queue（配合 /user 用户目的地）
        registry.enableSimpleBroker("/topic", "/queue");
        // 客户端发往服务端的消息前缀
        registry.setApplicationDestinationPrefixes("/app");
        // 用户目的地前缀：/user/{userId}/...
        registry.setUserDestinationPrefix("/user");
    }
}

package com.buildmart.service;

import com.buildmart.common.AuthException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatAuthServiceTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void resolvesIdentityThroughConfiguredEndpointWithoutDependingOnContentType() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sns/jscode2session", exchange -> {
            byte[] response = "{\"openid\":\"openid-test\",\"unionid\":\"unionid-test\",\"session_key\":\"secret-session\"}"
                    .getBytes(StandardCharsets.UTF_8);
            // WeChat has returned JSON as text/plain in some environments.
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();

        WechatAuthService service = serviceForServer();
        WechatAuthService.WechatIdentity identity = service.resolveIdentity("temporary-code");

        assertEquals("openid-test", identity.openid());
        assertEquals("unionid-test", identity.unionid());
    }

    @Test
    void coalescesConcurrentRequestsForTheSameCode() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/sns/jscode2session", exchange -> {
            requests.incrementAndGet();
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            byte[] response = "{\"openid\":\"openid-test\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();

        WechatAuthService service = serviceForServer();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> service.resolveOpenid("one-time-code"));
            entered.await();
            Future<String> second = executor.submit(() -> service.resolveOpenid("one-time-code"));
            release.countDown();

            assertEquals("openid-test", first.get());
            assertEquals("openid-test", second.get());
            assertEquals(1, requests.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void mapsWechatInvalidCodeToStableClientError() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sns/jscode2session", exchange -> {
            byte[] response = "{\"errcode\":40029,\"errmsg\":\"invalid code\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();

        AuthException error = assertThrows(AuthException.class,
                () -> serviceForServer().resolveOpenid("temporary-code"));

        assertEquals(AuthException.Reason.CODE_INVALID, error.reason());
    }

    @Test
    void mapsWechatUsedCodeToReplayError() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sns/jscode2session", exchange -> {
            byte[] response = "{\"errcode\":40163,\"errmsg\":\"code been used\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();

        AuthException error = assertThrows(AuthException.class,
                () -> serviceForServer().resolveOpenid("temporary-code"));

        assertEquals(AuthException.Reason.CODE_REUSED, error.reason());
    }

    @Test
    void mapsMalformedWechatBodyToResponseError() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sns/jscode2session", exchange -> {
            byte[] response = "<html>gateway error</html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();

        AuthException error = assertThrows(AuthException.class,
                () -> serviceForServer().resolveOpenid("temporary-code"));

        assertEquals(AuthException.Reason.RESPONSE_INVALID, error.reason());
        assertTrue(error.getMessage().contains("响应格式无效"));
    }

    @Test
    void reportsMissingConfigurationBeforeCallingWechat() {
        WechatAuthService service = new WechatAuthService("", "");

        AuthException error = assertThrows(AuthException.class,
                () -> service.resolveOpenid("temporary-code"));

        assertEquals(AuthException.Reason.CONFIGURATION, error.reason());
    }

    private WechatAuthService serviceForServer() {
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
        return new WechatAuthService("app-id", "app-secret", endpoint,
                Duration.ofSeconds(1), Duration.ofSeconds(1), null);
    }
}

package com.proxychecker.checker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proxychecker.domain.CheckResult;
import com.proxychecker.domain.IpLocationInfo;
import com.proxychecker.domain.ProxyInfo;
import com.proxychecker.infrastructure.db.LocalIpDatabase;
import com.proxychecker.infrastructure.http.HttpClientFactory;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Checker for HTTP and HTTPS proxies.
 * Uses OkHttp asynchronous execution.
 */
public class HttpProxyChecker implements ProxyChecker {

    private static final Logger log = LoggerFactory.getLogger(HttpProxyChecker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicInteger FAILURE_LOG_COUNT = new AtomicInteger(0);
    private final OkHttpClient sharedClient;
    private final String testUrl;

    public HttpProxyChecker(OkHttpClient sharedClient, String testUrl) {
        this.sharedClient = sharedClient;
        this.testUrl = testUrl;
    }

    @Override
    public CompletableFuture<CheckResult> checkAsync(ProxyInfo proxy, long timeoutMillis,
                                                     LocalIpDatabase localIpDatabase) {
        long start = System.currentTimeMillis();
        CompletableFuture<CheckResult> future = new CompletableFuture<>();

        try {
            OkHttpClient client = HttpClientFactory.createClient(proxy, timeoutMillis, sharedClient);
            Request request = new Request.Builder()
                    .url(testUrl)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (FAILURE_LOG_COUNT.incrementAndGet() <= 5) {
                        log.warn("HTTP proxy {} I/O error: {}", proxy.toUrl(), e.toString());
                    }
                    future.complete(CheckResult.failed(proxy.toUrl(), System.currentTimeMillis() - start));
                }

                @Override
                public void onResponse(Call call, Response response) {
                    long elapsed = System.currentTimeMillis() - start;
                    try (ResponseBody body = response.body()) {
                        if (response.code() != 200) {
                            log.debug("Proxy {} returned HTTP {}", proxy.toUrl(), response.code());
                            future.complete(CheckResult.failed(proxy.toUrl(), elapsed));
                            return;
                        }

                        String bodyString = body != null ? body.string() : "";
                        String exitIp = extractExitIp(bodyString);
                        IpLocationInfo location = localIpDatabase != null ? localIpDatabase.locate(exitIp) : null;

                        future.complete(CheckResult.success(proxy.toUrl(), elapsed, exitIp, location, null));
                    } catch (Exception e) {
                        log.debug("Error processing response for {}: {}", proxy.toUrl(), e.getMessage());
                        future.complete(CheckResult.failed(proxy.toUrl(), elapsed));
                    }
                }
            });
        } catch (Exception e) {
            log.warn("Failed to initiate request for {}: {}", proxy.toUrl(), e.toString());
            future.complete(CheckResult.failed(proxy.toUrl(), System.currentTimeMillis() - start));
        }

        return future;
    }

    private String extractExitIp(String body) {
        try {
            JsonNode node = MAPPER.readTree(body);
            String origin = node.path("origin").asText("");
            if (origin != null && !origin.isBlank()) {
                int comma = origin.indexOf(',');
                return (comma == -1 ? origin : origin.substring(0, comma)).trim();
            }
        } catch (Exception ignored) {
            // Ignore JSON parse errors
        }
        return null;
    }
}
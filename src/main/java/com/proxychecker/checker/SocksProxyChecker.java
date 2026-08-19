package com.proxychecker.checker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proxychecker.domain.CheckResult;
import com.proxychecker.domain.IpLocationInfo;
import com.proxychecker.domain.ProxyInfo;
import com.proxychecker.infrastructure.db.LocalIpDatabase;
import com.proxychecker.infrastructure.http.HttpClientFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Checker for SOCKS4 and SOCKS5 proxies.
 */
public class SocksProxyChecker implements ProxyChecker {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public CheckResult check(ProxyInfo proxy, long timeoutMillis, LocalIpDatabase localIpDatabase) {
        long start = System.currentTimeMillis();
        try {
            OkHttpClient client = HttpClientFactory.createClient(proxy, timeoutMillis);
            Request request = new Request.Builder()
                    .url("https://httpbin.org/ip")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                long elapsed = System.currentTimeMillis() - start;
                if (response.code() != 200) {
                    return CheckResult.failed(proxy.toUrl(), elapsed);
                }

                String body = response.body() != null ? response.body().string() : "";
                String exitIp = extractExitIp(body);
                IpLocationInfo location = localIpDatabase != null ? localIpDatabase.locate(exitIp) : null;

                return CheckResult.success(proxy.toUrl(), elapsed, exitIp, location, null);
            }
        } catch (Exception e) {
            return CheckResult.failed(proxy.toUrl(), System.currentTimeMillis() - start);
        }
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
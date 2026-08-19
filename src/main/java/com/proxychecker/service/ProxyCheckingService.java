package com.proxychecker.service;

import com.proxychecker.checker.HttpProxyChecker;
import com.proxychecker.checker.ProxyChecker;
import com.proxychecker.checker.SocksProxyChecker;
import com.proxychecker.cli.ProgressPrinter;
import com.proxychecker.domain.CheckResult;
import com.proxychecker.domain.ProxyInfo;
import com.proxychecker.domain.ProxyProtocol;
import com.proxychecker.infrastructure.db.LocalIpDatabase;
import com.proxychecker.infrastructure.http.HttpClientFactory;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates concurrent proxy checks using asynchronous OkHttp.
 * Concurrency is controlled by OkHttp's Dispatcher.
 * No extra CompletableFuture timeout is used; OkHttp's callTimeout handles overall timing.
 */
public class ProxyCheckingService {

    private static final Logger log = LoggerFactory.getLogger(ProxyCheckingService.class);

    private final long timeoutMillis;
    private final int concurrency;
    private final LocalIpDatabase ipDatabase;
    private final OkHttpClient sharedClient;
    private final String testUrl;

    public ProxyCheckingService(long timeoutMillis, int concurrency, LocalIpDatabase ipDatabase, String testUrl) {
        this.timeoutMillis = timeoutMillis;
        this.concurrency = concurrency;
        this.ipDatabase = ipDatabase;
        this.testUrl = testUrl;
        this.sharedClient = HttpClientFactory.createSharedClient(concurrency, timeoutMillis);
    }

    public List<CheckResult> checkAll(List<ProxyInfo> proxies) {
        int total = proxies.size();
        ProgressPrinter progress = new ProgressPrinter(total);
        progress.start();

        AtomicInteger completedCount = new AtomicInteger(0);
        List<CompletableFuture<CheckResult>> futures = new ArrayList<>(total);

        for (ProxyInfo proxy : proxies) {
            ProxyChecker checker = checkerFor(proxy.protocol());
            CompletableFuture<CheckResult> future = checker
                    .checkAsync(proxy, timeoutMillis, ipDatabase)
                    .exceptionally(ex -> {
                        int currentCount = completedCount.get();
                        if (currentCount < 10) {
                            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                            log.warn("Proxy check failed for {}: {}", proxy.toUrl(), cause.toString());
                        }
                        return CheckResult.failed(proxy.toUrl(), -1);
                    })
                    .whenComplete((result, throwable) -> {
                        int done = completedCount.incrementAndGet();
                        progress.update(done);
                    });
            futures.add(future);
        }

        List<CheckResult> results = new ArrayList<>(total);
        for (CompletableFuture<CheckResult> future : futures) {
            results.add(future.join());
        }

        progress.stop();
        return results;
    }

    private ProxyChecker checkerFor(ProxyProtocol protocol) {
        return switch (protocol) {
            case HTTP, HTTPS -> new HttpProxyChecker(sharedClient, testUrl);
            case SOCKS4, SOCKS5 -> new SocksProxyChecker(sharedClient, testUrl);
        };
    }
}
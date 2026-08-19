package com.proxychecker.service;

import com.proxychecker.checker.HttpProxyChecker;
import com.proxychecker.checker.ProxyChecker;
import com.proxychecker.checker.SocksProxyChecker;
import com.proxychecker.cli.ProgressPrinter;
import com.proxychecker.domain.CheckResult;
import com.proxychecker.domain.ProxyInfo;
import com.proxychecker.domain.ProxyProtocol;
import com.proxychecker.infrastructure.db.LocalIpDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates concurrent proxy checks using virtual threads.
 * No artificial concurrency limit is imposed; virtual threads scale naturally.
 */
public class ProxyCheckingService {

    private final long timeoutMillis;
    private final int concurrency; // retained for API compatibility, currently unused
    private final LocalIpDatabase ipDatabase;

    public ProxyCheckingService(long timeoutMillis, int concurrency, LocalIpDatabase ipDatabase) {
        this.timeoutMillis = timeoutMillis;
        this.concurrency = concurrency;
        this.ipDatabase = ipDatabase;
    }

    public List<CheckResult> checkAll(List<ProxyInfo> proxies) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        int total = proxies.size();

        ProgressPrinter progress = new ProgressPrinter(total);
        progress.start();

        AtomicInteger completedCount = new AtomicInteger(0);
        List<CompletableFuture<CheckResult>> futures = new ArrayList<>(total);

        try {
            for (ProxyInfo proxy : proxies) {
                CompletableFuture<CheckResult> future = CompletableFuture
                        .supplyAsync(() -> {
                            ProxyChecker checker = checkerFor(proxy.protocol());
                            return checker.check(proxy, timeoutMillis, ipDatabase);
                        }, executor)
                        .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                        .exceptionally(ex -> CheckResult.failed(proxy.toUrl(), -1))
                        .whenComplete((result, throwable) -> {
                            int done = completedCount.incrementAndGet();
                            progress.update(done);
                        });
                futures.add(future);
            }

            Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdownNow, "proxy-checker-shutdown"));

            List<CheckResult> results = new ArrayList<>(total);
            for (CompletableFuture<CheckResult> future : futures) {
                results.add(future.join());
            }
            return results;
        } finally {
            progress.stop();
            executor.shutdownNow();
        }
    }

    private ProxyChecker checkerFor(ProxyProtocol protocol) {
        return switch (protocol) {
            case HTTP, HTTPS -> new HttpProxyChecker();
            case SOCKS4, SOCKS5 -> new SocksProxyChecker();
        };
    }
}
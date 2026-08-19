package com.proxychecker.checker;

import com.proxychecker.domain.CheckResult;
import com.proxychecker.domain.ProxyInfo;
import com.proxychecker.infrastructure.db.LocalIpDatabase;

import java.util.concurrent.CompletableFuture;

/**
 * Proxy checking strategy. Asynchronous API using OkHttp's dispatcher for concurrency control.
 */
public interface ProxyChecker {

    CompletableFuture<CheckResult> checkAsync(ProxyInfo proxy, long timeoutMillis, LocalIpDatabase localIpDatabase);
}
package com.proxychecker.checker;

import com.proxychecker.domain.CheckResult;
import com.proxychecker.domain.ProxyInfo;
import com.proxychecker.infrastructure.db.LocalIpDatabase;

/**
 * Proxy checking strategy.
 */
public interface ProxyChecker {

    CheckResult check(ProxyInfo proxy, long timeoutMillis, LocalIpDatabase localIpDatabase);
}
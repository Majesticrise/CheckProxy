package com.proxychecker.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of a single proxy check.
 */
public class CheckResult {

    @JsonProperty("proxy")
    private String proxy;

    @JsonProperty("working")
    private boolean working;

    @JsonProperty("responseTimeMs")
    private long responseTimeMs;

    @JsonProperty("exitIp")
    private String exitIp;

    @JsonProperty("location")
    private IpLocationInfo location;

    @JsonProperty("connectTunnelSupported")
    private Boolean connectTunnelSupported;

    public CheckResult() {
    }

    public CheckResult(String proxy, boolean working, long responseTimeMs, String exitIp,
                       IpLocationInfo location, Boolean connectTunnelSupported) {
        this.proxy = proxy;
        this.working = working;
        this.responseTimeMs = responseTimeMs;
        this.exitIp = exitIp;
        this.location = location;
        this.connectTunnelSupported = connectTunnelSupported;
    }

    public static CheckResult success(String proxy, long responseTimeMs, String exitIp,
                                      IpLocationInfo location, Boolean connectTunnelSupported) {
        return new CheckResult(proxy, true, responseTimeMs, exitIp, location, connectTunnelSupported);
    }

    public static CheckResult failed(String proxy, long responseTimeMs) {
        return new CheckResult(proxy, false, responseTimeMs, null, null, null);
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public boolean isWorking() {
        return working;
    }

    public void setWorking(boolean working) {
        this.working = working;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getExitIp() {
        return exitIp;
    }

    public void setExitIp(String exitIp) {
        this.exitIp = exitIp;
    }

    public IpLocationInfo getLocation() {
        return location;
    }

    public void setLocation(IpLocationInfo location) {
        this.location = location;
    }

    public Boolean getConnectTunnelSupported() {
        return connectTunnelSupported;
    }

    public void setConnectTunnelSupported(Boolean connectTunnelSupported) {
        this.connectTunnelSupported = connectTunnelSupported;
    }
}
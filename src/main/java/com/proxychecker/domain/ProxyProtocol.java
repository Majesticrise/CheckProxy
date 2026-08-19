package com.proxychecker.domain;

/**
 * Supported proxy protocols.
 */
public enum ProxyProtocol {
    HTTP("http"),
    HTTPS("https"),
    SOCKS4("socks4"),
    SOCKS5("socks5");

    private final String scheme;

    ProxyProtocol(String scheme) {
        this.scheme = scheme;
    }

    public String scheme() {
        return scheme;
    }

    public static ProxyProtocol fromScheme(String scheme) {
        return switch (scheme.toLowerCase()) {
            case "http" -> HTTP;
            case "https" -> HTTPS;
            case "socks4", "socks4a" -> SOCKS4;
            case "socks5", "socks" -> SOCKS5;
            default -> throw new IllegalArgumentException("Unsupported proxy scheme: " + scheme);
        };
    }
}
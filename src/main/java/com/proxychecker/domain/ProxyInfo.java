package com.proxychecker.domain;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Parsed proxy information.
 */
public final class ProxyInfo {

    private final String original;
    private final ProxyProtocol protocol;
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    private ProxyInfo(String original, ProxyProtocol protocol, String host, int port,
                      String username, String password) {
        this.original = original;
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public static ProxyInfo parse(String input) {
        String original = input;
        ProxyProtocol protocol = ProxyProtocol.HTTP;
        String rest = input;

        int schemeEnd = input.indexOf("://");
        if (schemeEnd > 0) {
            String scheme = input.substring(0, schemeEnd);
            protocol = ProxyProtocol.fromScheme(scheme);
            rest = input.substring(schemeEnd + 3);
        }

        String userinfo = null;
        int at = rest.lastIndexOf('@');
        if (at >= 0) {
            userinfo = rest.substring(0, at);
            rest = rest.substring(at + 1);
        }

        String host;
        int port;
        if (rest.startsWith("[")) {
            int endBracket = rest.indexOf(']');
            if (endBracket == -1) {
                throw new IllegalArgumentException("Invalid IPv6 literal: " + input);
            }
            host = rest.substring(1, endBracket);
            if (rest.length() > endBracket + 1 && rest.charAt(endBracket + 1) == ':') {
                port = parsePort(rest.substring(endBracket + 2));
            } else {
                port = defaultPort(protocol);
            }
        } else {
            int colon = rest.lastIndexOf(':');
            if (colon < 0) {
                host = rest;
                port = defaultPort(protocol);
            } else {
                host = rest.substring(0, colon);
                port = parsePort(rest.substring(colon + 1));
            }
        }

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host is empty: " + input);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + input);
        }

        String username = null;
        String password = null;
        if (userinfo != null) {
            int colon = userinfo.indexOf(':');
            if (colon >= 0) {
                username = urlDecode(userinfo.substring(0, colon));
                password = urlDecode(userinfo.substring(colon + 1));
            } else {
                username = urlDecode(userinfo);
            }
        }

        return new ProxyInfo(original, protocol, host, port, username, password);
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port: " + value);
        }
    }

    private static int defaultPort(ProxyProtocol protocol) {
        return switch (protocol) {
            case HTTP, HTTPS -> 8080;
            case SOCKS4, SOCKS5 -> 1080;
        };
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public String toUrl() {
        String scheme = protocol.scheme();
        String hostPart = host.contains(":") ? "[" + host + "]" : host;
        StringBuilder sb = new StringBuilder(scheme).append("://");
        if (username != null && !username.isBlank()) {
            sb.append(urlEncode(username))
                    .append(':')
                    .append(urlEncode(password == null ? "" : password))
                    .append('@');
        }
        sb.append(hostPart).append(':').append(port);
        return sb.toString();
    }

    public String original() {
        return original;
    }

    public ProxyProtocol protocol() {
        return protocol;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    @Override
    public String toString() {
        return toUrl();
    }
}
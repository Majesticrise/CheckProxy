package com.proxychecker.checker;

import com.proxychecker.domain.ProxyInfo;
import com.proxychecker.domain.ProxyProtocol;
import com.proxychecker.infrastructure.http.HttpClientFactory;
import okhttp3.Credentials;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Tests whether an HTTP/HTTPS proxy supports the CONNECT tunnel method.
 */
public class ConnectTunnelTester {

    public boolean test(ProxyInfo proxy, long timeoutMillis) {
        if (proxy.protocol() != ProxyProtocol.HTTP && proxy.protocol() != ProxyProtocol.HTTPS) {
            return false;
        }

        try (Socket socket = createProxySocket(proxy, timeoutMillis)) {
            if (!socket.isConnected()) {
                socket.connect(new InetSocketAddress(proxy.host(), proxy.port()), (int) timeoutMillis);
            }
            socket.setSoTimeout((int) timeoutMillis);

            OutputStream out = socket.getOutputStream();
            String connectRequest = buildConnectRequest(proxy);
            out.write(connectRequest.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();

            InputStream in = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
            String statusLine = reader.readLine();
            return statusLine != null && statusLine.contains(" 200 ");
        } catch (IOException e) {
            return false;
        }
    }

    private Socket createProxySocket(ProxyInfo proxy, long timeoutMillis) throws IOException {
        if (proxy.protocol() == ProxyProtocol.HTTPS) {
            SSLSocketFactory factory = HttpClientFactory.getTrustAllSslSocketFactory();
            SSLSocket socket = (SSLSocket) factory.createSocket();
            socket.connect(new InetSocketAddress(proxy.host(), proxy.port()), (int) timeoutMillis);
            return socket;
        }
        return new Socket();
    }

    private String buildConnectRequest(ProxyInfo proxy) {
        StringBuilder sb = new StringBuilder("CONNECT httpbin.org:443 HTTP/1.1\r\n");
        sb.append("Host: httpbin.org:443\r\n");
        if (proxy.username() != null && !proxy.username().isBlank()) {
            String credential = Credentials.basic(
                    proxy.username(),
                    proxy.password() == null ? "" : proxy.password(),
                    StandardCharsets.UTF_8);
            sb.append("Proxy-Authorization: ").append(credential).append("\r\n");
        }
        sb.append("\r\n");
        return sb.toString();
    }
}
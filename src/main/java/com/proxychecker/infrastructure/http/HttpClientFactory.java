package com.proxychecker.infrastructure.http;

import com.proxychecker.domain.ProxyInfo;
import com.proxychecker.domain.ProxyProtocol;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * Builds OkHttp clients for different proxy protocols with trust-all SSL.
 */
public final class HttpClientFactory {

    private static final X509TrustManager TRUST_ALL_MANAGER = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    private static final SSLSocketFactory TRUST_ALL_SSL_FACTORY;

    static {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new javax.net.ssl.TrustManager[]{TRUST_ALL_MANAGER}, new SecureRandom());
            TRUST_ALL_SSL_FACTORY = context.getSocketFactory();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private HttpClientFactory() {
    }

    public static SSLSocketFactory getTrustAllSslSocketFactory() {
        return TRUST_ALL_SSL_FACTORY;
    }

    public static OkHttpClient createClient(ProxyInfo proxy, long timeoutMillis) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .sslSocketFactory(TRUST_ALL_SSL_FACTORY, TRUST_ALL_MANAGER)
                .hostnameVerifier((hostname, session) -> true)
                .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .proxy(Proxy.NO_PROXY);

        switch (proxy.protocol()) {
            case HTTP -> {
                builder.proxy(new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(proxy.host(), proxy.port())));
                configureProxyAuth(builder, proxy);
            }
            case HTTPS -> {
                builder.proxy(new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(proxy.host(), proxy.port())));
                builder.socketFactory(new TlsProxySocketFactory(proxy, TRUST_ALL_SSL_FACTORY, timeoutMillis));
                configureProxyAuth(builder, proxy);
            }
            case SOCKS4, SOCKS5 -> {
                builder.socketFactory(new SocksProxySocketFactory(proxy, timeoutMillis));
            }
        }

        return builder.build();
    }

    private static void configureProxyAuth(OkHttpClient.Builder builder, ProxyInfo proxy) {
        if (proxy.username() != null && !proxy.username().isBlank()) {
            builder.proxyAuthenticator(new Authenticator() {
                @Override
                public Request authenticate(Route route, Response response) throws IOException {
                    String credential = Credentials.basic(
                            proxy.username(),
                            proxy.password() == null ? "" : proxy.password(),
                            StandardCharsets.UTF_8);
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build();
                }
            });
        }
    }

    /**
     * SocketFactory for HTTPS proxies. Creates a connected SSLSocket to the proxy,
     * then wraps it in a delegate Socket that avoids re-connecting.
     */
    private static class TlsProxySocketFactory extends SocketFactory {

        private final ProxyInfo proxy;
        private final SSLSocketFactory sslSocketFactory;
        private final long timeoutMillis;

        TlsProxySocketFactory(ProxyInfo proxy, SSLSocketFactory sslSocketFactory, long timeoutMillis) {
            this.proxy = proxy;
            this.sslSocketFactory = sslSocketFactory;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public Socket createSocket() {
            try {
                // 1. Create a plain TCP socket and connect to the proxy
                Socket plainSocket = new Socket();
                plainSocket.connect(new InetSocketAddress(proxy.host(), proxy.port()), (int) timeoutMillis);
                plainSocket.setSoTimeout((int) timeoutMillis);

                // 2. Wrap it in an SSLSocket (TLS handshake to the proxy)
                SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(
                        plainSocket, proxy.host(), proxy.port(), true);
                sslSocket.setUseClientMode(true);
                sslSocket.startHandshake();

                // 3. Return a delegate that prevents OkHttp from re-connecting
                return new PreConnectedSocket(sslSocket);

            } catch (IOException e) {
                throw new RuntimeException("Failed to establish TLS connection to proxy " + proxy.toUrl(), e);
            }
        }

        // Other createSocket methods delegate to the default factory, but they will not be used by OkHttp.
        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return sslSocketFactory.createSocket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return sslSocketFactory.createSocket(host, port, localHost, localPort);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return sslSocketFactory.createSocket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return sslSocketFactory.createSocket(address, port, localAddress, localPort);
        }
    }

    /**
     * A Socket wrapper that delegates to an already connected SSLSocket.
     * It overrides connect() to be a no-op when already connected, avoiding
     * "Already connected" exceptions from OkHttp.
     */
    private static class PreConnectedSocket extends Socket {

        private final Socket delegate;

        PreConnectedSocket(Socket delegate) {
            super();
            this.delegate = delegate;
        }

        @Override
        public void connect(SocketAddress endpoint) throws IOException {
            // Already connected, do nothing.
        }

        @Override
        public void connect(SocketAddress endpoint, int timeout) throws IOException {
            // Already connected, do nothing.
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return delegate.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return delegate.getOutputStream();
        }

        @Override
        public boolean isConnected() {
            return delegate.isConnected();
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public void setSoTimeout(int timeout) throws SocketException {
            delegate.setSoTimeout(timeout);
        }

        @Override
        public int getSoTimeout() throws SocketException {
            return delegate.getSoTimeout();
        }

        @Override
        public void setTcpNoDelay(boolean on) throws SocketException {
            delegate.setTcpNoDelay(on);
        }

        @Override
        public boolean getTcpNoDelay() throws SocketException {
            return delegate.getTcpNoDelay();
        }

        @Override
        public void setKeepAlive(boolean on) throws SocketException {
            delegate.setKeepAlive(on);
        }

        @Override
        public boolean getKeepAlive() throws SocketException {
            return delegate.getKeepAlive();
        }

        @Override
        public void setReuseAddress(boolean on) throws SocketException {
            delegate.setReuseAddress(on);
        }

        @Override
        public boolean getReuseAddress() throws SocketException {
            return delegate.getReuseAddress();
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    /**
     * SocketFactory for SOCKS4/SOCKS5 that creates custom Socket implementations.
     */
    private static class SocksProxySocketFactory extends SocketFactory {

        private final ProxyInfo proxy;
        private final long timeoutMillis;

        SocksProxySocketFactory(ProxyInfo proxy, long timeoutMillis) {
            this.proxy = proxy;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public Socket createSocket() {
            return proxy.protocol() == ProxyProtocol.SOCKS4
                    ? new Socks4ProxySocket(proxy, timeoutMillis)
                    : new Socks5ProxySocket(proxy, timeoutMillis);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            Socket socket = createSocket();
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            Socket socket = createSocket();
            socket.bind(new InetSocketAddress(localHost, localPort));
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            Socket socket = createSocket();
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            Socket socket = createSocket();
            socket.bind(new InetSocketAddress(localAddress, localPort));
            socket.connect(new InetSocketAddress(address, port));
            return socket;
        }
    }

    /**
     * SOCKS4 proxy socket. Overrides connect() to perform handshake before returning.
     */
    private static class Socks4ProxySocket extends Socket {

        private final ProxyInfo proxy;
        private final long timeoutMillis;
        private boolean handshakeDone = false;

        Socks4ProxySocket(ProxyInfo proxy, long timeoutMillis) {
            super();
            this.proxy = proxy;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public void connect(SocketAddress endpoint) throws IOException {
            connect(endpoint, (int) timeoutMillis);
        }

        @Override
        public void connect(SocketAddress endpoint, int timeout) throws IOException {
            if (handshakeDone) {
                return; // Already connected and handshake completed
            }

            super.connect(new InetSocketAddress(proxy.host(), proxy.port()), timeout);
            setSoTimeout((int) timeoutMillis);
            performHandshake((InetSocketAddress) endpoint);
            handshakeDone = true;
        }

        private void performHandshake(InetSocketAddress target) throws IOException {
            OutputStream out = getOutputStream();
            DataOutputStream dataOut = new DataOutputStream(out);

            int port = target.getPort();
            InetAddress address = target.getAddress();
            byte[] ipBytes;
            String domain = null;

            if (address instanceof Inet4Address) {
                ipBytes = address.getAddress();
            } else {
                // SOCKS4a extension for domain names
                ipBytes = new byte[]{0, 0, 0, 1};
                domain = target.getHostString();
            }

            dataOut.writeByte(4);
            dataOut.writeByte(1);
            dataOut.writeShort(port);
            dataOut.write(ipBytes);
            dataOut.writeByte(0); // userid empty

            if (domain != null) {
                dataOut.write(domain.getBytes(StandardCharsets.UTF_8));
                dataOut.writeByte(0);
            }
            dataOut.flush();

            InputStream in = getInputStream();
            DataInputStream dataIn = new DataInputStream(in);

            int vn = dataIn.readUnsignedByte();
            int cd = dataIn.readUnsignedByte();
            dataIn.readUnsignedShort(); // dst port
            dataIn.readInt();           // dst ip

            if (vn != 0 || cd != 90) {
                throw new IOException("SOCKS4 connect failed: vn=" + vn + ", cd=" + cd);
            }
        }
    }

    /**
     * SOCKS5 proxy socket. Supports no-auth and username/password authentication.
     */
    private static class Socks5ProxySocket extends Socket {

        private final ProxyInfo proxy;
        private final long timeoutMillis;
        private boolean handshakeDone = false;

        Socks5ProxySocket(ProxyInfo proxy, long timeoutMillis) {
            super();
            this.proxy = proxy;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public void connect(SocketAddress endpoint) throws IOException {
            connect(endpoint, (int) timeoutMillis);
        }

        @Override
        public void connect(SocketAddress endpoint, int timeout) throws IOException {
            if (handshakeDone) {
                return; // Already connected and handshake completed
            }

            super.connect(new InetSocketAddress(proxy.host(), proxy.port()), timeout);
            setSoTimeout((int) timeoutMillis);
            performHandshake((InetSocketAddress) endpoint);
            handshakeDone = true;
        }

        private void performHandshake(InetSocketAddress target) throws IOException {
            OutputStream out = getOutputStream();
            DataOutputStream dataOut = new DataOutputStream(out);
            InputStream in = getInputStream();
            DataInputStream dataIn = new DataInputStream(in);

            byte[] usernameBytes = (proxy.username() != null && !proxy.username().isBlank())
                    ? proxy.username().getBytes(StandardCharsets.UTF_8)
                    : null;
            boolean hasAuth = usernameBytes != null;

            int methodCount = hasAuth ? 2 : 1;
            dataOut.writeByte(0x05);
            dataOut.writeByte(methodCount);
            dataOut.writeByte(0x00);
            if (hasAuth) {
                dataOut.writeByte(0x02);
            }
            dataOut.flush();

            int version = dataIn.readUnsignedByte();
            int method = dataIn.readUnsignedByte();

            if (hasAuth && method == 0x02) {
                byte[] passwordBytes = proxy.password() != null
                        ? proxy.password().getBytes(StandardCharsets.UTF_8)
                        : new byte[0];

                dataOut.writeByte(0x01);
                dataOut.writeByte(usernameBytes.length);
                dataOut.write(usernameBytes);
                dataOut.writeByte(passwordBytes.length);
                dataOut.write(passwordBytes);
                dataOut.flush();

                int authVersion = dataIn.readUnsignedByte();
                int authStatus = dataIn.readUnsignedByte();
                if (authVersion != 0x01 || authStatus != 0x00) {
                    throw new IOException("SOCKS5 authentication failed");
                }
            } else if (method != 0x00) {
                throw new IOException("SOCKS5 no acceptable authentication method: " + method);
            }

            ByteArrayOutputStream cmd = new ByteArrayOutputStream();
            cmd.write(0x05);
            cmd.write(0x01);
            cmd.write(0x00);

            InetAddress address = target.getAddress();
            if (address instanceof Inet4Address) {
                cmd.write(0x01);
                cmd.write(address.getAddress());
            } else {
                cmd.write(0x03);
                String host = target.getHostString();
                byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
                cmd.write(hostBytes.length);
                cmd.write(hostBytes);
            }

            int port = target.getPort();
            cmd.write((port >> 8) & 0xFF);
            cmd.write(port & 0xFF);
            out.write(cmd.toByteArray());
            out.flush();

            int replyVersion = dataIn.readUnsignedByte();
            int replyStatus = dataIn.readUnsignedByte();
            dataIn.readUnsignedByte(); // reserved

            int addressType = dataIn.readUnsignedByte();
            if (addressType == 0x01) {
                dataIn.readNBytes(4);
            } else if (addressType == 0x03) {
                int len = dataIn.readUnsignedByte();
                dataIn.readNBytes(len);
            } else if (addressType == 0x04) {
                dataIn.readNBytes(16);
            } else {
                throw new IOException("SOCKS5 unknown address type: " + addressType);
            }

            dataIn.readUnsignedShort(); // port

            if (replyVersion != 0x05 || replyStatus != 0x00) {
                throw new IOException("SOCKS5 connect failed: status=" + replyStatus);
            }
        }
    }
}
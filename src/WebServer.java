import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 手机访问服务: 在本机开一个小型 HTTP 服务(默认 8765 起, 被占用则顺延),
 * 手机与电脑同一 WiFi 下, 用浏览器打开 http://电脑IP:端口 即可刷题。
 * 数据与电脑版完全共享(同一 Bridge/进度文件)。
 */
public class WebServer {

    public static String start(Bridge bridge) {
        HttpServer server = null;
        int port = 0;
        for (int p = 8765; p < 8770; p++) {
            try {
                server = HttpServer.create(new InetSocketAddress(p), 0);
                port = p;
                break;
            } catch (IOException ignored) {
            }
        }
        if (server == null) return "";

        final Bridge b = bridge;
        final int finalPort = port;
        server.createContext("/", ex -> {
            try {
                String path = ex.getRequestURI().getPath();
                Map<String, String> q = query(ex.getRequestURI().getRawQuery());

                if (path.equals("/") || path.equals("/app.html") || path.equals("/index.html")) {
                    byte[] html = readResource("/app.html");
                    if (html == null) { respond(ex, 500, "app.html missing"); return; }
                    respondBytes(ex, html);
                    return;
                }

                String resp;
                switch (path) {
                    case "/api/chapters":
                        resp = b.chapters(course(q)); break;
                    case "/api/session":
                        resp = b.session(course(q), Integer.parseInt(q.getOrDefault("ch", "1"))); break;
                    case "/api/warm":
                        resp = b.sessionWarm(course(q), Integer.parseInt(q.getOrDefault("ch", "1"))); break;
                    case "/api/ids":
                        resp = b.cardsByIds(course(q), q.getOrDefault("ids", "")); break;
                    case "/api/answer":
                        b.answer(course(q), Integer.parseInt(q.getOrDefault("id", "0")),
                                "1".equals(q.get("ok")));
                        resp = "ok"; break;
                    case "/api/zhan":
                        b.zhan(course(q), Integer.parseInt(q.getOrDefault("id", "0")));
                        resp = "ok"; break;
                    case "/api/stats":
                        resp = b.stats(course(q)); break;
                    case "/api/checkin":
                        resp = b.checkinStatus(); break;
                    case "/api/checkinmonth":
                        resp = b.checkinMonth(q.getOrDefault("ym", "")); break;
                    case "/api/loadsession":
                        resp = b.loadSession(course(q)); break;
                    case "/api/savesession": {
                        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        b.saveSession(course(q), body);
                        resp = "ok"; break;
                    }
                    default:
                        respond(ex, 404, "not found");
                        return;
                }
                respond(ex, 200, resp);
            } catch (Exception e) {
                try { respond(ex, 500, "err:" + e.getMessage()); } catch (IOException ignored) { }
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();

        String ip = lanIp();
        return ip.isEmpty() ? "" : "http://" + ip + ":" + finalPort;
    }

    private static String course(Map<String, String> q) {
        String c = q.get("course");
        if ("algo".equals(c)) return "algo";
        if ("co".equals(c)) return "co";
        return "java";
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) m.put(urlDecode(kv.substring(0, i)), urlDecode(kv.substring(i + 1)));
        }
        return m;
    }

    private static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream in = WebServer.class.getResourceAsStream(name)) {
            if (in == null) return null;
            return in.readAllBytes();
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int code, String text)
            throws IOException {
        respondBytes(ex, code, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void respondBytes(com.sun.net.httpserver.HttpExchange ex, byte[] bytes)
            throws IOException {
        respondBytes(ex, 200, bytes);
    }

    private static void respondBytes(com.sun.net.httpserver.HttpExchange ex, int code, byte[] bytes)
            throws IOException {
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** 取局域网 IPv4(优先 192.168.*), 手机要用它访问 */
    private static String lanIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            String best = "";
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    String host = a.getHostAddress();
                    if (host.startsWith("192.168.")) return host;
                    if (best.isEmpty() && (host.startsWith("10.") || host.startsWith("172."))) best = host;
                }
            }
            return best;
        } catch (Exception e) {
            return "";
        }
    }

    /** 简易自测: java -cp javashan.jar WebServer */
    public static void main(String[] args) throws Exception {
        String url = start(new Bridge());
        System.out.println("服务地址: " + url);
        Thread.currentThread().join();
    }
}

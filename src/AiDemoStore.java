import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 实时演示生成器: 点击时调大模型为当前卡生成实战代码演示(不预存, 结果按卡缓存复用)。
 * 配置 ~/.javashan/ai.txt: key=xxx  model=glm-4-flash  base=https://open.bigmodel.cn/api/paas/v4/chat/completions
 */
public class AiDemoStore {

    private final Path file;
    private final Map<Integer, String> cache = new HashMap<>();

    volatile int runningId = 0;
    volatile String state = "";   // pending / done / err:xxx

    public AiDemoStore(Path file) {
        this.file = file;
        if (Files.exists(file)) {
            try {
                Pattern kv = Pattern.compile("\"(\\w+)\"\\s*:\\s*(\"([^\"]*)\"|-?\\d+)");
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    Map<String, String> raw = new HashMap<>();
                    Matcher m = kv.matcher(line);
                    while (m.find()) {
                        String v = m.group(2);
                        raw.put(m.group(1), v.startsWith("\"") ? m.group(3) : v);
                    }
                    if (raw.containsKey("id") && raw.containsKey("demo")) {
                        cache.put(Integer.parseInt(raw.get("id")), raw.get("demo"));
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    public boolean has(int id) { return cache.containsKey(id); }
    public String get(int id) { return cache.get(id); }

    private synchronized void save() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> e : cache.entrySet()) {
            sb.append("{\"id\":").append(e.getKey()).append(",\"demo\":\"")
              .append(e.getValue()).append("\"}\n");
        }
        try { Files.writeString(file, sb.toString(), StandardCharsets.UTF_8); } catch (IOException ignored) { }
    }

    /* ---- 配置 ---- */
    static String[] config(Path dataHome) {
        String key = "", model = "qwen2.5-coder:7b", base = "http://localhost:11434/v1/chat/completions";
        try {
            Path f = dataHome.resolve("ai.txt");
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    line = line.trim();
                    if (line.startsWith("key=")) key = line.substring(4).trim();
                    else if (line.startsWith("model=")) model = line.substring(6).trim();
                    else if (line.startsWith("base=")) base = line.substring(5).trim();
                }
            }
        } catch (IOException ignored) { }
        return new String[]{key, model, base};
    }

    /* ---- 异步生成 ---- */
    public void generateAsync(Path dataHome, int id, String courseName, String topic,
                              String question, String answer, String explain) {
        runningId = id;
        state = "pending";
        Thread t = new Thread(() -> {
            try {
                String demo = callApi(dataHome, courseName, topic, question, answer, explain);
                if (demo == null || demo.isBlank()) {
                    state = "err:AI返回为空, 稍后再试";
                } else {
                    cache.put(id, demo);
                    save();
                    state = "done";
                }
            } catch (Exception e) {
                state = "err:" + e.getMessage();
            }
        }, "aidemo");
        t.setDaemon(true);
        t.start();
    }

    public String state(int id) {
        if (has(id)) return "done";
        if (id == runningId) return state;
        return "";
    }

    private static String callApi(Path dataHome, String courseName, String topic,
                                  String question, String answer, String explain) throws Exception {
        String[] cfg = config(dataHome);
        // 本地模型(Ollama)免key, 远程API才需要key
        if (cfg[0].isEmpty() && !cfg[2].contains("localhost") && !cfg[2].contains("127.0.0.1")) {
            throw new IllegalStateException("远程API需配置key(本地Ollama无需): 在 ~/.javashan/ai.txt 写入 key=你的key");
        }
        String prompt = "你是编程教师。课程: " + courseName + ", 知识点: " + topic + "\n"
                + "题目: " + question + "\n正确答案: " + answer + "\n解析: " + explain + "\n"
                + "请为这个知识点生成一个实战代码演示, 要求:\n"
                + "1. Java代码(算法课可用伪代码注释), 5-10行, 用System.out.println展示关键结果\n"
                + "2. 第一行用注释概括知识点, 其余每行一个动作\n"
                + "3. 代码中需要字符串字面量时一律用单引号, 禁止双引号\n"
                + "4. 只输出代码行本身, 不要解释和代码块围栏";
        String body = "{\"model\":\"" + cfg[1] + "\",\"messages\":[{\"role\":\"user\",\"content\":\""
                + esc(prompt) + "\"}]}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(cfg[2]))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", cfg[0].isEmpty() ? "Bearer none" : "Bearer " + cfg[0])
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("API HTTP " + resp.statusCode() + " — 请确认Ollama已启动且模型已拉取(ollama list): " + resp.body());
        }
        String content = extractContent(resp.body());
        if (content == null) throw new IllegalStateException("响应里找不到content");
        // 反转义: \n -> ¶ (演示换行), 双引号->单引号, 去掉可能的围栏
        String demo = content.replace("\\r\\n", "\\n").replace("\\n", "¶").replace("\\\"", "'")
                .replace("```", "").trim();
        return demo;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "'").replace("\n", "\\n");
    }

    /** 从 chat/completions 响应中抠出 content (避免引入JSON库) */
    static String extractContent(String json) {
        Matcher m = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (!m.find()) return null;
        return m.group(1);
    }
}

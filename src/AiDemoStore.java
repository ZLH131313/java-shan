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
 * AI 实时演示生成器(仅Java课程): 点击时调本地 Ollama(qwen2.5-coder)为当前卡生成实战代码演示,
 * 不预存, 结果按卡缓存复用。其余课程已于 v1.10 停用 AI 演示。
 * 配置 ~/.javashan/ai.txt: key=xxx  model=qwen2.5-coder:1.5b  base=http://127.0.0.1:11434/v1/chat/completions
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
        String key = "", model = "qwen2.5-coder:1.5b", base = "http://localhost:11434/v1/chat/completions";
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
                    state = "err:AI生成失败, 稍后再点一次";
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
        // 概念课(计组/OS/算法): AI不做解释者(解析已经讲清了), 只补"生活类比"建直觉
        if (courseName != null && (courseName.contains("操作") || courseName.contains("组成")
                || courseName.contains("算法"))) {
            return analogyGenerate(cfg, topic, explain);
        }
        // Java课: 生成代码演示(带代码形态校验+重掷)
        return javaGenerate(cfg, topic, question, answer, explain);
    }

    /**
     * Java代码演示: 1.5b级小模型只跟英文指令(中文指令会复述要求), 指令用英文, 代码注释要求中文。
     * 校验: 输出必须像代码且有足够长度, 拒绝"请给出详细信息"式耍赖(实测卡142踩过且被缓存),
     * 最多重掷3次, 不达标不缓存。
     */
    private static String javaGenerate(String[] cfg, String topic, String question,
                                       String answer, String explain) throws Exception {
        String prompt = "ALL COMMENTS MUST BE IN CHINESE (中文注释).\n"
                + "Topic: " + topic + ". Question: " + question
                + " Answer: " + answer + " Explanation: " + explain + "\n"
                + "Write 6-10 lines of Java code demonstrating the exact behavior this question tests. "
                + "The question above is complete, do NOT ask for more information. "
                + "Rules: every comment in Chinese(中文), standard indentation, "
                + "use System.out.println to show results, "
                + "use single quotes for strings, output only the code.";
        String last = null;
        for (int i = 0; i < 3; i++) {
            last = postChat(cfg, cfg[1], prompt, ",\"temperature\":0.5");
            last = last.replaceFirst("(?i)^\\s*java\\s*¶", "").trim();   // 去掉```java围栏残留的语言标记行
            if (javaCodeOk(last)) return last;
        }
        return null;
    }

    /** Java演示校验: 必须像代码(类/方法/打印/分号花括号)且够长, 拒绝索要信息的元文本 */
    private static boolean javaCodeOk(String demo) {
        if (demo == null || demo.length() < 40) return false;
        if (demo.contains("请给出") || demo.contains("请提供") || demo.contains("请告诉我")) return false;
        String low = demo.toLowerCase();
        boolean codeish = low.contains("class ") || low.contains("void ")
                || low.contains("system.out") || low.contains("public ")
                || low.contains("static ");
        return codeish && (demo.contains(";") || demo.contains("{"));
    }

    /**
     * 概念课类比生成: 喂卡片解析当原料(模型只做"转写"不做"回忆", 这是小模型的可靠区),
     * 2-3句中文: 点名概念 → 一个日常类比 → 一句映射回概念。
     * 校验: 必须含概念名且长度合理, 最多3次, 不达标不缓存。
     */
    private static String analogyGenerate(String[] cfg, String topic, String explain) throws Exception {
        String prompt = "You explain computer concepts with daily-life analogies.\n"
                + "Concept: " + topic + "\n"
                + "Textbook explanation (material only, do NOT copy it): "
                + (explain == null ? "" : explain) + "\n"
                + "Write in Chinese, 2-3 short sentences. First sentence MUST name the concept "
                + "「" + topic + "」and its job. Then give ONE vivid everyday analogy that captures "
                + "the KEY MECHANISM (how it actually works step by step), not merely the purpose. "
                + "Last sentence maps the analogy back to the concept. "
                + "Output ONLY those sentences.";
        String last = null;
        for (int i = 0; i < 3; i++) {
            last = postChat(cfg, "qwen2.5:1.5b", prompt, ",\"temperature\":0.7");
            last = last.replace("¶", " ").trim();
            String t = topic.replace(" ", "");
            if ((last.contains(topic) || last.contains(t)) && last.length() >= 15 && last.length() <= 200) {
                return last;
            }
        }
        return null;   // 不达标不缓存, 前端提示重点一次
    }

    /** 发一次 chat 请求并反转义(\n->¶换行, 双引号->单引号, 去围栏); AiTransStore 也复用 */
    static String postChat(String[] cfg, String model, String prompt, String extraJson) throws Exception {
        String body = "{\"model\":\"" + model + "\",\"messages\":[{\"role\":\"user\",\"content\":\""
                + esc(prompt) + "\"}],\"max_tokens\":280,\"keep_alive\":\"30m\"" + extraJson + "}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(cfg[2]))
                .timeout(Duration.ofSeconds(180))
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
        return content.replace("\\r\\n", "\\n").replace("\\n", "¶").replace("\\\"", "'")
                .replace("```", "").trim();
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

    /** 自测: java=Java代码演示 / analogy=概念课类比(需Ollama已启动) */
    public static void main(String[] args) throws Exception {
        java.nio.file.Path home = java.nio.file.Path.of(System.getProperty("user.home"), ".javashan");
        if (args.length > 0 && args[0].equals("analogy")) {
            String a = callApi(home, "🖥 操作系统", "信号量", "test", "",
                    "semaphore = value + 等待队列; P(wait)/V(signal) 是原子操作, 由内核实现。");
            System.out.println("[os类比] " + a);
            String b = callApi(home, "📐 算法设计", "回溯法", "test", "",
                    "把所有可能解组织成树(子集树/排列树/一般树), 深度优先遍历并剪枝。");
            System.out.println("[algo类比] " + b);
            return;
        }
        String demo = callApi(home, "☕ Java 基础", "转型",
                "向下转型前保证安全的做法是先用?", "instanceof 判断类型",
                "if (a instanceof Dog d) { d.bark(); } 类型对得上再转, 避免 ClassCastException。");
        System.out.println(demo == null ? "(未达标返回null)" : demo.replace("¶", "\n"));
    }
}

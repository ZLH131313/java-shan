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
        // 英语: 例句生成(带输出校验+自动重试)
        if (courseName != null && courseName.contains("英语")) {
            return enGenerate(cfg, topic, answer, question);
        }
        // 按课程分流: 1.5b模型英文指令服从性远好于中文, 统一英文指令+代码内中文注释
        String prompt;
        if (courseName != null && courseName.contains("算法")) {
            prompt = "ALL COMMENTS MUST BE IN CHINESE (中文注释).\n"
                    + "Topic: " + topic + ". Question: " + question
                    + " Answer: " + answer + " Explanation: " + explain + "\n"
                    + "Write 6-10 lines of C++ code (or pseudocode if more intuitive) "
                    + "demonstrating the core algorithm steps directly related to this question. "
                    + "Rules: every comment in Chinese(中文), print each step's result, "
                    + "output only the code.";
        } else if (courseName != null && courseName.contains("Java")) {
            prompt = "ALL COMMENTS MUST BE IN CHINESE (中文注释).\n"
                    + "Topic: " + topic + ". Question: " + question
                    + " Answer: " + answer + " Explanation: " + explain + "\n"
                    + "Write 6-10 lines of Java code demonstrating the exact behavior this question tests. "
                    + "Rules: every comment in Chinese(中文), standard indentation, "
                    + "use System.out.println to show results, "
                    + "use single quotes for strings, output only the code.";
        } else {
            // OS/计组: 解释型任务, 通用模型+英文指令+中文输出
            prompt = "You must respond ONLY in Chinese (中文). No English sentences.\n"
                    + "Knowledge point: [" + topic + "]\n"
                    + "Question: " + question + "\n"
                    + "Correct answer: " + answer + "\n"
                    + "Explain in Chinese: what this concept is, why it works, how to calculate it. "
                    + "5-8 lines. May use pseudocode or math steps. Output only the Chinese explanation.";
        }
        // OS/计组用通用模型(解释好), Java/算法用编码模型(代码好)
        String model = (courseName != null && (courseName.contains("操作") || courseName.contains("组成")))
                ? "qwen2.5:1.5b" : cfg[1];
        return postChat(cfg, model, prompt, "");
    }

    /* 个别情境表达类考点(加油/welcome)没有可造句的语法锚点, AI产不出稳定例句, 直接内置 */
    private static final java.util.Map<String, String> EN_PRESET = java.util.Map.of(
        "加油", "You can do it!¶(你能行的!)¶Good luck!¶(祝你好运!)¶Keep it up!¶(继续保持!)¶Go for it!¶(放手一搏!)",
        "welcome", "Welcome!¶(欢迎!)¶Welcome to our home.¶(欢迎来我们家。)¶Welcome back!¶(欢迎回来!)¶You are welcome to visit us anytime.¶(随时欢迎你来玩。)"
    );

    /** 英语例句: 最多3次尝试, 输出校验(4句英文+4行中文翻译)不过就换一组场景重掷 */
    private static String enGenerate(String[] cfg, String topic, String answer, String question) throws Exception {
        // AI只生成例句, 知识点解释由卡片解析提供(更准确)
        // 坑: 题面喂进去 1.5b 会锚定题面情境, 四句例句全抄同一个故事(如连着四个 I finished my homework)
        //     所以不喂题面, 只给考点+正确形式, 并要求四句不同故事/主语/动词, 再随机指定四条场景提示
        String preset = EN_PRESET.get(topic);
        if (preset != null) return preset;
        if (topic.contains("不能连用")) return enPairTranslate(cfg, topic);
        String target = (answer == null ? "" : answer).replace("✓", "").replace("✗", "").trim();
        // 阅读技巧类卡answer是整句中文没有英文锚点, 从题面抽数英文片段(如 it / this / they)当演示目标
        if (!target.matches(".*[A-Za-z].*") && question != null) {
            StringBuilder fr = new StringBuilder();
            Matcher m = Pattern.compile("[A-Za-z][A-Za-z' /]{1,}").matcher(question);
            while (m.find() && fr.length() < 40) {
                String g = m.group().trim();
                if (g.length() >= 2) fr.append(fr.length() > 0 ? " / " : "").append(g);
            }
            if (fr.length() > 0) target = fr.toString();
        }
        // 不加强制行的话 1.5b 会写出不含考点的句子; 短语类考点直接点名要包含短语(任意形态)
        String mustLine;
        if (topic.matches(".*[A-Za-z]{2,}.*") && !topic.contains("不能连用")) {
            String phrase = topic.replaceAll("[^A-Za-z' ]", " ").replaceAll("\\s+", " ").trim();
            mustLine = "Every sentence MUST contain the expression '" + phrase
                    + "' in some grammatical form.\n";
        } else {
            mustLine = "Every sentence MUST clearly demonstrate this grammar point, like the textbook example does.\n";
        }
        String last = null;
        for (int i = 0; i < 3; i++) {
            String[] sc = pickScenarios();
            String prompt = "Write 4 NEW English sentences using this grammar: [" + topic + "]\n"
                    + (target.isEmpty() ? "" : "Grammar example from textbook: " + target + "\n")
                    + mustLine
                    + "Do NOT copy the textbook sentence. Each of the 4 sentences must tell a different story, "
                    + "use different subjects (I / she / we / they / my brother...) and different verbs:\n"
                    + "1. " + sc[0] + "\n2. " + sc[1] + "\n3. " + sc[2] + "\n4. " + sc[3] + "\n"
                    + "Answer format (follow exactly, no intro, no numbering):\n"
                    + "The library closes at nine in the evening.\n"
                    + "(图书馆晚上九点关门。)\n"
                    + "Every English sentence is followed by its Chinese translation in parentheses on the next line. "
                    + "Output only the 4 sentences and 4 translations.";
            last = postChat(cfg, "qwen2.5:1.5b", prompt, ",\"temperature\":0.5");
            // 1.5b 有时把格式示例原句(或改个词的版本)抄进输出, 检测到就重掷
            if (enOk(last) && !last.toLowerCase().contains("library clos")
                    && enTopicOk(topic, answer, last) && enTenseOk(topic, last)) return last;
        }
        return last;   // 3次都没校验过, 尽力返回最后一次
    }

    /* 不能连用类连词考点(because/so, although/but)的中文源句: 四句不同故事, 每句只含一个连词语义 */
    private static final java.util.Map<String, String[]> ZH_PAIR_SENTENCES = java.util.Map.of(
        "because与so不能连用", new String[]{
            "因为他迟到了, 我们错过了开幕式。",
            "他因为感冒了, 今天没来上课。",
            "她起晚了, 所以没赶上早班车。",
            "我们提前到了车站, 所以多等了半个小时。"},
        "although与but不能连用", new String[]{
            "虽然雨很大, 我们还是按时到了山顶。",
            "他虽然只学了半年英语, 但已经能看懂简单的文章。",
            "这家餐厅很远, 但是菜很好吃。",
            "她个子不高, 但她是队里跳得最高的。"}
    );

    /**
     * 连词类考点走中译英: 1.5b 自由造句总写出不含连词的句子, 而翻译是它的强项。
     * 源句自带连词结构且故事互不相同, 译完校验"每句恰好一个连词", 英文+(中文)组合回演示格式。
     */
    private static String enPairTranslate(String[] cfg, String topic) throws Exception {
        String[] zh = ZH_PAIR_SENTENCES.get(topic);
        if (zh == null) return null;
        StringBuilder pair = new StringBuilder();
        Matcher w = Pattern.compile("[a-zA-Z]+").matcher(topic);
        while (w.find()) pair.append(pair.length() > 0 ? " / " : "").append(w.group().toLowerCase());
        java.util.List<String> pairList = java.util.Arrays.asList(pair.toString().split(" / "));
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < zh.length; i++) list.append(i + 1).append(". ").append(zh[i]).append("\n");
        String conjRule = pair.indexOf("because") >= 0
                ? " Use because as a conjunction before a full clause (subject + verb), never 'because of'."
                : "";
        String prompt = "Translate these 4 Chinese sentences into natural English.\n"
                + "Rule: each sentence uses exactly ONE of these connectors: " + pair
                + ". Never use both in one sentence." + conjRule + "\n"
                + list
                + "Output only the 4 English sentences, one per line, no numbering.";
        String last = null;
        for (int i = 0; i < 3; i++) {
            last = postChat(cfg, "qwen2.5:1.5b", prompt, ",\"temperature\":0.2");
            java.util.List<String> en = enSentences(splitSentences(last));
            if (en.size() >= 4 && pairAllHit1(en, pairList)) {
                return compose(en, zh);
            }
        }
        java.util.List<String> en = enSentences(splitSentences(last));
        return en.isEmpty() ? null : compose(en, zh);   // 尽力返回
    }

    /** 每句恰好含一个指定连词(词边界匹配): 0个=没体现规则, 2个=犯不能连用; because必须接从句(because of算不合格) */
    private static boolean pairAllHit1(java.util.List<String> sents, java.util.List<String> pair) {
        for (String s : sents) {
            if (pair.contains("because") && s.toLowerCase().contains("because of")) return false;
            int hits = 0;
            for (String p : pair)
                if (Pattern.compile("\\b" + Pattern.quote(p) + "\\b").matcher(s).find()) hits++;
            if (hits != 1) return false;
        }
        return true;
    }

    /** 英文句 + (中文句) 逐对组合成演示文本(¶分行) */
    private static String compose(java.util.List<String> en, String[] zh) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < en.size() && i < zh.length; i++) {
            if (sb.length() > 0) sb.append("¶");
            sb.append(en.get(i).replaceFirst("^\\s*\\d+[.)]\\s*", "").trim())
              .append("¶(").append(zh[i]).append(")");
        }
        return sb.toString();
    }

    /** 模型偶尔把4句挤在同一行, 按 ". " 切开各自成行 */
    private static String splitSentences(String raw) {
        return raw == null ? "" : raw.replace(". ", ".¶");
    }

    /** 考点校验: 英文短语类考点至少3句含考点词干(连词类已走中译英路线, 不进这里) */
    private static boolean enTopicOk(String topic, String answer, String demo) {
        java.util.List<String> sents = enSentences(demo);
        if (sents.size() < 4) return false;
        // 考点词干优先取topic里的英文长词, topic没有再取answer的
        String src = topic.replaceAll("[^A-Za-z\\s]", " ");
        if (!src.replaceAll("\\s", "").isEmpty() && !src.matches(".*[A-Za-z]{5,}.*"))
            src = src + " " + (answer == null ? "" : answer);
        java.util.Set<String> stems = new java.util.HashSet<>();
        Matcher w = Pattern.compile("[A-Za-z]{5,}").matcher(src);
        while (w.find()) stems.add(w.group().substring(0, 5).toLowerCase());
        if (stems.isEmpty()) return true;
        int hit = 0;
        for (String s : sents) {
            String low = s.toLowerCase();
            for (String st : stems) if (low.contains(st)) { hit++; break; }
        }
        return hit >= 3;
    }

    /** 时态/虚拟类考点的轻量标记校验: 至少3句带目标时态特征 */
    private static boolean enTenseOk(String topic, String demo) {
        String marker;
        switch (topic) {
            case "过去完成时":
                marker = "\\bhad\\s+(been|\\w+ed|\\w+en|left|won|done|gone|seen|taken|given|made|told|gotten|begun"
                       + "|brought|bought|thought|found|kept|known|met|paid|sent|spent|spoken|stood|taught"
                       + "|thrown|written|driven|eaten|fallen|heard|held|lost|sold|shown|felt|grown)\\b";
                break;
            case "将来进行时": marker = "\\bwill be \\w+ing\\b"; break;
            case "建议虚拟":  marker = "\\b(should|suggest|insist|recommend|demand)\\w*\\b"; break;
            case "wish 虚拟": marker = "\\bwish\\w*\\b"; break;
            default: return true;
        }
        java.util.List<String> sents = enSentences(demo);
        if (sents.size() < 4) return false;
        Pattern p = Pattern.compile(marker, Pattern.CASE_INSENSITIVE);
        int hit = 0;
        for (String s : sents) if (p.matcher(s).find()) hit++;
        return hit >= 3;
    }

    /** 从演示文本里抠英文句子行(字母多/中文少, 允许带行号或前导空格) */
    private static java.util.List<String> enSentences(String demo) {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String line : demo.split("¶")) {
            int c = 0, e = 0;
            for (int k = 0; k < line.length(); ) {
                int cp = line.codePointAt(k);
                if (cp >= 0x4E00 && cp <= 0x9FFF) c++;
                else if ((cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')) e++;
                k += Character.charCount(cp);
            }
            if (e >= 8 && c <= 3) list.add(line);
        }
        return list;
    }

    /** 英语例句输出校验: 至少4行英文句子 + 4行中文翻译 */
    private static boolean enOk(String demo) {
        if (demo == null || demo.isBlank()) return false;
        int cjk = 0, eng = 0;
        for (String line : demo.split("¶")) {
            int c = 0, e = 0;
            for (int k = 0; k < line.length(); ) {
                int cp = line.codePointAt(k);
                if (cp >= 0x4E00 && cp <= 0x9FFF) c++;
                else if ((cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')) e++;
                k += Character.charCount(cp);
            }
            if (c >= 2 && e < 8) cjk++;   // 纯翻译行
            else if (e >= 8) eng++;       // 英文句子行(可带行号/前导空格)
        }
        return cjk >= 4 && eng >= 4;
    }

    /** 发一次 chat 请求并反转义(\n->¶换行, 双引号->单引号, 去围栏) */
    private static String postChat(String[] cfg, String model, String prompt, String extraJson) throws Exception {
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

    /* 英语例句场景池: 每次生成随机抽4条塞进提示词, 防止四句例句/不同卡片的情境雷同 */
    private static final String[] EN_SCENARIOS = {
        "a birthday party", "a train trip", "cooking dinner", "a football match",
        "a rainy morning", "moving to a new apartment", "seeing a doctor", "a final exam",
        "shopping for a winter coat", "a phone call at midnight", "a holiday at the beach",
        "planting flowers", "a movie night", "borrowing a book from the library",
        "a morning run in the park", "a family reunion dinner", "losing a wallet",
        "an English speech contest", "a job interview", "a delayed flight"
    };

    private static String[] pickScenarios() {
        java.util.List<String> pool = new java.util.ArrayList<>(java.util.Arrays.asList(EN_SCENARIOS));
        java.util.Collections.shuffle(pool, java.util.concurrent.ThreadLocalRandom.current());
        return new String[]{pool.get(0), pool.get(1), pool.get(2), pool.get(3)};
    }

    /** 自测: 跑英语例句生成, 检查四句故事/主语/动词是否互不相同(需Ollama已启动) */
    public static void main(String[] args) throws Exception {
        String[][] cases = {
            {"过去完成时", "had left", ""},
            {"because与so不能连用", "Because it rained, I did not go out.", ""},
            {"acquire knowledge", "We should keep acquiring new knowledge.", ""},
            {"加油", "You can do it!", "朋友比赛前, 你想对他说「加油」"},
            {"指代题", "回看上一句的主语或宾语, 代入原句检验逻辑", "阅读中判断 it / this / they 指代什么, 最可靠的做法是?"}
        };
        java.nio.file.Path home = java.nio.file.Path.of(System.getProperty("user.home"), ".javashan");
        java.io.PrintStream out = new java.io.PrintStream(System.out, true, "UTF-8");
        for (String[] c : cases) {
            out.println("== [" + c[0] + "] ==");
            String demo = callApi(home, "英语四级", c[0], c[2], c[1], "");
            out.println(demo.replace("¶", "\n"));
            out.println("check=" + enOk(demo) + " topicOk=" + enTopicOk(c[0], c[1], demo));
        }
    }

    /** 从 chat/completions 响应中抠出 content (避免引入JSON库) */
    static String extractContent(String json) {
        Matcher m = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (!m.find()) return null;
        return m.group(1);
    }
}

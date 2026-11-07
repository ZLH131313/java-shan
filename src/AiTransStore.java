import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;

/**
 * AI翻译(仅英语课): 答题后把本题正确答案句翻成地道中文。
 * 单槽异步; state 协议与 aiDemoState 一致: ""空闲 / pending / err:xxx / 其余=翻译结果。
 * 日志: ~/.javashan/aitrans.log(排查不出结果用)。
 */
public class AiTransStore {

    private volatile String state = "";

    private static void log(String s) {
        try {
            Files.writeString(Path.of(System.getProperty("user.home"), ".javashan", "aitrans.log"),
                    LocalTime.now() + " " + s + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }

    public void start(Path dataHome, String text) {
        if (text == null || text.isBlank()) { state = "err:没有可翻译的内容"; return; }
        if ("pending".equals(state)) return;   // 正在翻(前端有transBusy守卫, 双保险)
        state = "pending";
        log("start: " + text);
        Thread t = new Thread(() -> {
            try {
                String[] cfg = AiDemoStore.config(dataHome);
                // 本地模型(Ollama)免key, 远程API才需要key
                if (cfg[0].isEmpty() && !cfg[2].contains("localhost") && !cfg[2].contains("127.0.0.1")) {
                    throw new IllegalStateException("远程API需配置key(本地Ollama无需)");
                }
                // 翻译是1.5b通用模型的强项; 指令用英文(小模型只跟英文指令), 输出要求中文
                String prompt = "Translate the following English into natural, idiomatic Chinese. "
                        + "Output ONLY the Chinese translation, nothing else.\n" + text;
                String r = AiDemoStore.postChat(cfg, "qwen2.5:1.5b", prompt, ",\"temperature\":0.2");
                r = r.replace("¶", " ").trim();
                state = r.isEmpty() ? "err:翻译结果为空, 再点一次" : r;
                log("done: " + state);
            } catch (Exception e) {
                state = "err:" + e.getMessage();
                log("err: " + e);
            }
        }, "aitrans");
        t.setDaemon(true);
        t.start();
    }

    /** 预热: 后台发个迷你请求把 qwen2.5:1.5b 拉进显存(keep_alive 30m), 首次点击不用等冷加载 */
    public void warm(Path dataHome) {
        if ("pending".equals(state)) return;
        Thread t = new Thread(() -> {
            try {
                String[] cfg = AiDemoStore.config(dataHome);
                if (cfg[0].isEmpty() && !cfg[2].contains("localhost") && !cfg[2].contains("127.0.0.1")) return;
                AiDemoStore.postChat(cfg, "qwen2.5:1.5b", "Reply with: ok", "");
                log("warmed");
            } catch (Exception e) {
                log("warm err: " + e);
            }
        }, "aitrans-warm");
        t.setDaemon(true);
        t.start();
    }

    public String state() { return state; }

    /** 自测: 翻一句看质量(需Ollama已启动) */
    public static void main(String[] args) throws Exception {
        AiTransStore s = new AiTransStore();
        s.start(Path.of(System.getProperty("user.home"), ".javashan"),
                args.length > 0 ? args[0] : "By the time the police arrived, the thief had left the shop.");
        while ("pending".equals(s.state())) Thread.sleep(200);
        System.out.println(s.state());
    }
}

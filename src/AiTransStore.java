import java.nio.file.Path;

/**
 * AI翻译(仅英语课): 答题后把本题正确答案句翻成地道中文。
 * 单槽异步; state 协议与 aiDemoState 一致: ""空闲 / pending / err:xxx / 其余=翻译结果。
 */
public class AiTransStore {

    private volatile String state = "";

    public void start(Path dataHome, String text) {
        if (text == null || text.isBlank()) { state = "err:没有可翻译的内容"; return; }
        if ("pending".equals(state)) return;   // 正在翻(前端有transBusy守卫, 双保险)
        state = "pending";
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
            } catch (Exception e) {
                state = "err:" + e.getMessage();
            }
        }, "aitrans");
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

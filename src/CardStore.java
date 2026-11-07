import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 cards.jsonl 读取卡片库。
 * 每行一个扁平 JSON 对象, 如:
 *   {"id":1,"topic":"数据类型","tag":"概念","q":"...","o":"A;;B;;C;;D","a":2,"e":"..."}
 * 有 "o" 的是选择题(a 是正确选项下标), 没有 "o" 的是填空题(a 是答案文本)。
 */
public class CardStore {

    // 只匹配 "键":"字符串值" 或 "键":数字 两种形态, 卡片内容里不允许出现双引号
    private static final Pattern KV = Pattern.compile("\"(\\w+)\"\\s*:\\s*(\"([^\"]*)\"|-?\\d+)");

    static List<Card> load(Path file) throws IOException {
        List<Card> cards = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty()) continue;
            Map<String, String> raw = new HashMap<>();
            Matcher m = KV.matcher(line);
            while (m.find()) {
                String value = m.group(2);
                raw.put(m.group(1), value.startsWith("\"") ? m.group(3) : value);
            }
            List<String> options = null;
            int answerIndex = -1;
            String answerText = null;
            if (raw.containsKey("o")) {
                options = new ArrayList<>(List.of(raw.get("o").split(";;")));
                answerIndex = Integer.parseInt(raw.get("a"));
            } else {
                answerText = raw.get("a");
            }
            cards.add(new Card(
                    Integer.parseInt(raw.get("id")),
                    raw.containsKey("ch") ? Integer.parseInt(raw.get("ch")) : 1,
                    raw.containsKey("ord") ? Integer.parseInt(raw.get("ord")) : 0,
                    raw.get("topic"), raw.get("tag"), raw.get("q"),
                    options, answerIndex, answerText, raw.get("e")));
        }
        // demos-*.jsonl 已删除, AI实时生成替代
        // mergeDemos(file, cards);
        return cards;
    }

    /** 若卡片库旁有 demos.jsonl, 按 id 给卡片挂上代码演示 */
    private static void mergeDemos(Path cardsFile, List<Card> cards) throws IOException {
        Path demos = cardsFile.resolveSibling("demos.jsonl");
        if (!Files.exists(demos)) return;
        Map<Integer, String> demoMap = new HashMap<>();
        for (String line : Files.readAllLines(demos, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty()) continue;
            Matcher m = KV.matcher(line);
            Map<String, String> raw = new HashMap<>();
            while (m.find()) {
                String value = m.group(2);
                raw.put(m.group(1), value.startsWith("\"") ? m.group(3) : value);
            }
            if (raw.containsKey("id") && raw.containsKey("demo")) {
                demoMap.put(Integer.parseInt(raw.get("id")), raw.get("demo"));
            }
        }
        for (Card c : cards) {
            if (demoMap.containsKey(c.id)) c.demo = demoMap.get(c.id);
        }
    }
}

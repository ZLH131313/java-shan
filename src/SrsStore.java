import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 记忆调度器(简化版 SM-2):
 * 答对  -> 连对次数 +1, 复习间隔按 1/2/4/7/15/30 天递增; 若该卡在错题本中, 同时移出
 * 答错  -> 清零, 当天还要再刷一遍, 明天继续出现; 累计错次 +1 并记入错题本
 * 斩    -> suspended = true, 永不再出现
 * 进度保存在 progress.jsonl, 每行一张卡的状态。
 */
public class SrsStore {

    /** 连对第 n 次时, 距下次复习的天数 */
    private static final int[] INTERVALS = {1, 2, 4, 7, 15, 30};

    static class CardState {
        int interval;       // 当前复习间隔(天)
        int streak;         // 连对次数
        LocalDate due;      // 下次该出现的日期
        boolean suspended;  // 是否已被「斩」
        int wrong;          // 历史累计答错次数
        boolean inWrong;    // 当前是否在错题本中(答对即移出)

        CardState(int interval, int streak, LocalDate due, boolean suspended, int wrong, boolean inWrong) {
            this.interval = interval;
            this.streak = streak;
            this.due = due;
            this.suspended = suspended;
            this.wrong = wrong;
            this.inWrong = inWrong;
        }
    }

    private final Map<Integer, CardState> states = new HashMap<>();
    private final Path file;

    private SrsStore(Path file) {
        this.file = file;
    }

    static SrsStore load(Path file) throws IOException {
        SrsStore store = new SrsStore(file);
        if (!Files.exists(file)) return store;
        Pattern kv = Pattern.compile("\"(\\w+)\"\\s*:\\s*(\"([^\"]*)\"|-?\\d+)");
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty()) continue;
            Map<String, String> raw = new HashMap<>();
            Matcher m = kv.matcher(line);
            while (m.find()) {
                String value = m.group(2);
                raw.put(m.group(1), value.startsWith("\"") ? m.group(3) : value);
            }
            store.states.put(Integer.parseInt(raw.get("id")), new CardState(
                    Integer.parseInt(raw.get("iv")),
                    Integer.parseInt(raw.get("st")),
                    LocalDate.parse(raw.get("due")),
                    "1".equals(raw.get("z")),
                    raw.containsKey("w") ? Integer.parseInt(raw.get("w")) : 0,
                    "1".equals(raw.get("wb"))));   // 旧进度文件没有该字段则视为不在错题本
        }
        return store;
    }

    void save() throws IOException {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Integer, CardState> e : states.entrySet()) {
            CardState s = e.getValue();
            lines.add("{\"id\":" + e.getKey()
                    + ",\"iv\":" + s.interval
                    + ",\"st\":" + s.streak
                    + ",\"due\":\"" + s.due + "\""
                    + ",\"z\":" + (s.suspended ? 1 : 0)
                    + ",\"w\":" + s.wrong
                    + ",\"wb\":" + (s.inWrong ? 1 : 0) + "}");
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    boolean isNew(int cardId) {
        return !states.containsKey(cardId);
    }

    CardState state(int cardId) {
        return states.get(cardId);
    }

    /** 今天是否该复习这张卡(新卡不算) */
    boolean isDue(int cardId, LocalDate today) {
        CardState s = states.get(cardId);
        return s != null && !s.suspended && !s.due.isAfter(today);
    }

    void suspend(int cardId) {
        CardState s = states.computeIfAbsent(cardId,
                k -> new CardState(0, 0, LocalDate.now(), false, 0, false));
        s.suspended = true;
        s.inWrong = false;  // 斩掉的卡不再出现在错题本
    }

    void applyResult(int cardId, boolean correct, LocalDate today) {
        CardState s = states.computeIfAbsent(cardId,
                k -> new CardState(0, 0, today, false, 0, false));
        if (s.suspended) return;
        if (correct) {
            s.streak++;
            int idx = Math.min(s.streak, INTERVALS.length) - 1;
            s.interval = INTERVALS[idx];
            s.due = today.plusDays(s.interval);
            s.inWrong = false;                          // 答对即移出错题本
        } else {
            s.streak = 0;
            s.interval = 0;
            s.due = today;                              // 今天还会被重新排队刷一遍
            s.wrong++;
            s.inWrong = true;                           // 答错自动进错题本
        }
    }

    int suspendedCount() {
        return (int) states.values().stream().filter(s -> s.suspended).count();
    }

    /** 错题本当前卡数(在册且未被斩) */
    int wrongBookCount() {
        return (int) states.values().stream().filter(s -> s.inWrong && !s.suspended).count();
    }

    long dueTomorrowCount(LocalDate today) {
        return states.values().stream()
                .filter(s -> !s.suspended && s.due.isAfter(today) && !s.due.isAfter(today.plusDays(1)))
                .count();
    }
}

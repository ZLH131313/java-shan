import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * Java 百词斩 —— 控制台版
 * 启动: 在项目根目录执行 java -cp out Main
 * 玩法: 每天一队卡片, 答对进入更长间隔的复习队列, 答错当场重排;
 *       输入 z 把当前卡「斩」掉(永不再现), 输入 q 随时保存退出。
 */
public class Main {

    static final int NEW_PER_DAY = 10;

    public static void main(String[] args) throws IOException {
        // exe 双击启动时工作目录是它所在的 bin/, 卡片库在上一级, 两个位置都找一遍
        Path cardsFile = Path.of("cards.jsonl");
        if (!java.nio.file.Files.exists(cardsFile)) cardsFile = Path.of("..", "cards.jsonl");
        Path progressFile = cardsFile.resolveSibling("progress.jsonl");

        List<Card> cards = CardStore.load(cardsFile);
        SrsStore srs = SrsStore.load(progressFile);
        LocalDate today = LocalDate.now();
        Scanner in = new Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8);

        List<Card> queue = buildQueue(cards, srs, today);
        System.out.println("==============================================");
        System.out.println("      Java 百词斩  ·  今日刷卡计划");
        System.out.println("==============================================");
        System.out.println("今天是 " + today + " | 卡片库共 " + cards.size() + " 张");
        if (queue.isEmpty()) {
            System.out.println("今天没有到期的卡, 新卡也刷完啦! 去写代码吧。");
            System.out.println("明日待复习: " + srs.dueTomorrowCount(today) + " 张");
            exitPause(in);
            return;
        }

        long dueCount = queue.stream().filter(c -> !srs.isNew(c.id)).count();
        System.out.println("今日: 复习 " + dueCount + " 张 + 新卡 " + (queue.size() - dueCount)
                + " 张 = " + queue.size() + " 张   (已斩 " + srs.suspendedCount() + " 张)");
        System.out.println("提示: z=斩掉此卡  q=保存并退出");
        System.out.println();

        int answered = 0, correct = 0, streak = 0, bestStreak = 0;
        Set<Integer> requeued = new HashSet<>();
        int i = 0;
        while (i < queue.size()) {
            Card card = queue.get(i);
            System.out.println("---- [" + (i + 1) + "/" + queue.size() + "] "
                    + card.topic + " · " + card.tag + " ----");
            System.out.println(card.question);
            for (int j = 0; j < (card.isFill() ? 0 : card.options.size()); j++) {
                System.out.println("  " + (j + 1) + ". " + card.options.get(j));
            }
            System.out.print(card.isFill() ? "你的答案(直接输入): " : "选择(输入数字): ");

            String line = readLine(in);
            if (line == null) break; // 输入流结束, 视为退出
            line = line.trim();

            if (line.equalsIgnoreCase("q")) break;
            if (line.equalsIgnoreCase("z")) {
                srs.suspend(card.id);
                System.out.println("  [斩!] 已挥剑, 这张卡永不再现。");
                pause(in);
                i++;
                continue;
            }

            boolean ok;
            if (card.isFill()) {
                ok = card.checkFill(line);
            } else {
                Integer pick = parseInt(line);
                while (pick == null || pick < 1 || pick > card.options.size()) {
                    System.out.print("  请输入 1~" + card.options.size() + " 的数字: ");
                    line = readLine(in);
                    if (line == null) { pick = null; break; }
                    pick = parseInt(line.trim());
                }
                if (pick == null) break;
                ok = pick - 1 == card.answerIndex;
            }

            answered++;
            if (ok) {
                correct++;
                streak++;
                bestStreak = Math.max(bestStreak, streak);
                System.out.println("  [√] 答对了! 连击 x" + streak);
            } else {
                streak = 0;
                System.out.println("  [×] 答错了。正确答案: "
                        + (card.isFill() ? card.answerText : card.options.get(card.answerIndex)));
                srs.applyResult(card.id, false, today);
                if (!requeued.contains(card.id)) {
                    queue.add(card); // 错卡排到队尾再刷一次
                    requeued.add(card.id);
                }
            }
            if (ok) srs.applyResult(card.id, true, today);
            System.out.println("  ─ 记忆点: " + card.explain);
            pause(in);
            i++;
        }

        srs.save();
        System.out.println();
        System.out.println("==============================================");
        System.out.println("  本次刷卡 " + answered + " 张, 答对 " + correct + " 张"
                + (answered == 0 ? "" : ", 正确率 " + correct * 100 / answered + "%")
                + ", 最高连击 x" + bestStreak);
        System.out.println("  已斩 " + srs.suspendedCount() + " 张 | 明日待复习 "
                + srs.dueTomorrowCount(today) + " 张");
        System.out.println("  进度已保存, 明天再来!");
        System.out.println("==============================================");
        exitPause(in);
    }

    /** 今天的队伍: 先刷到期的复习卡, 再用新卡补足 */
    static List<Card> buildQueue(List<Card> cards, SrsStore srs, LocalDate today) {
        List<Card> review = new ArrayList<>();
        List<Card> fresh = new ArrayList<>();
        for (Card c : cards) {
            if (srs.isNew(c.id)) fresh.add(c);
            else if (srs.isDue(c.id, today)) review.add(c);
        }
        Collections.shuffle(review);
        Collections.shuffle(fresh);
        if (fresh.size() > NEW_PER_DAY) fresh = fresh.subList(0, NEW_PER_DAY);
        List<Card> queue = new ArrayList<>(review);
        queue.addAll(fresh);
        return queue;
    }

    static String readLine(Scanner in) {
        return in.hasNextLine() ? in.nextLine() : null;
    }

    static Integer parseInt(String s) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static void pause(Scanner in) {
        System.out.print("  (回车继续)");
        readLine(in);
        System.out.println();
    }

    /** 双击 exe 运行时, 窗口显示完结果就关闭, 这里停一下让用户看清结束语 */
    static void exitPause(Scanner in) {
        System.out.print("(按回车键退出)");
        readLine(in);
    }
}

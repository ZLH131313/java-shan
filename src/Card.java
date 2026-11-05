import java.util.List;

/**
 * 一张学习卡片。
 * 选择题: options 非空, 答案是 answerIndex(从 0 开始的下标)
 * 填空题: options 为 null, 答案是 answerText
 */
public class Card {
    final int id;
    final int chapter;    // 所属章节 1~8
    final int ord;        // 章内学习顺序
    final String topic;   // 主题, 如「集合」
    final String tag;     // 题型标签: 概念 / 输出 / 辨析 / 找错 / 填空
    final String question;
    final List<String> options;
    final int answerIndex;
    final String answerText;
    final String explain;
    String demo;          // 可选: 答题后展示的短代码演示, 由 demos.jsonl 按 id 关联

    Card(int id, int chapter, int ord, String topic, String tag, String question,
         List<String> options, int answerIndex, String answerText, String explain) {
        this.id = id;
        this.chapter = chapter;
        this.ord = ord;
        this.topic = topic;
        this.tag = tag;
        this.question = question;
        this.options = options;
        this.answerIndex = answerIndex;
        this.answerText = answerText;
        this.explain = explain;
    }

    boolean isFill() {
        return options == null;
    }

    /** 填空题判定: 去掉首尾空格, 忽略大小写 */
    boolean checkFill(String input) {
        return input != null && answerText.equalsIgnoreCase(input.trim());
    }
}

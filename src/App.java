import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.animation.PauseTransition;
import netscape.javascript.JSObject;

/**
 * Java 百词斩 图形版窗口: WebView 加载内嵌页面, Java 引擎通过 bridge 对象供 JS 调用。
 * 参数 --auto-close 供自动化冒烟测试, 4 秒后自动退出。
 */
public class App extends Application {

    /** 必须用字段持有强引用: setMember 只存弱引用, 若被 GC 回收, JS 侧所有按钮都会静默失效 */
    private Bridge bridge;

    @Override
    public void start(Stage stage) {
        WebView view = new WebView();
        WebEngine engine = view.getEngine();
        engine.setJavaScriptEnabled(true);

        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                try {
                    JSObject win = (JSObject) engine.executeScript("window");
                    bridge = new Bridge();
                    win.setMember("bridge", bridge);
                    engine.executeScript("window.__onBridgeReady && window.__onBridgeReady()");
                    System.out.println("[App] bridge ready");
                } catch (Exception e) {
                    System.out.println("[App] bridge failed: " + e.getMessage());
                }
            }
        });

        engine.load(App.class.getResource("/app.html").toExternalForm());

        stage.setTitle("Java 百词斩 v1.7");
        try {
            stage.getIcons().add(new javafx.scene.image.Image(
                    App.class.getResource("/icon.png").toExternalForm()));
        } catch (Exception ignored) { }
        stage.setMinWidth(760);
        stage.setMinHeight(640);
        stage.setScene(new Scene(view, 920, 780));
        stage.show();

        if (getParameters().getRaw().contains("--keytest")) {
            /* 键盘答题模拟: 开一组卡, 用 Robot 敲下真实按键 B, 检查结果与事件内容 */
            PauseTransition k1 = new PauseTransition(Duration.seconds(1.5));
            k1.setOnFinished(ev -> engine.executeScript("startSession(1)"));
            k1.play();
            PauseTransition k2 = new PauseTransition(Duration.seconds(2.8));
            k2.setOnFinished(ev -> {
                try {
                    javafx.application.Platform.runLater(() -> { stage.toFront(); stage.requestFocus(); });
                    java.awt.Robot robot = new java.awt.Robot();
                    Thread.sleep(400);
                    robot.keyPress(java.awt.event.KeyEvent.VK_B);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_B);
                    Thread.sleep(250);
                    robot.keyPress(java.awt.event.KeyEvent.VK_ENTER);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_ENTER);
                    Thread.sleep(250);
                    robot.keyPress(java.awt.event.KeyEvent.VK_SPACE);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_SPACE);
                    System.out.println("[按键测试] 已敲下 B / 回车 / 空格");
                } catch (Exception ex) {
                    System.out.println("[按键测试] Robot失败: " + ex.getMessage());
                }
            });
            k2.play();
            PauseTransition k3 = new PauseTransition(Duration.seconds(3.6));
            k3.setOnFinished(ev -> {
                System.out.println("[按键测试] " + engine.executeScript(
                        "(function(){var ws=document.querySelectorAll('#options .opt');var o=[];" +
                        "for (var i=0;i<ws.length;i++) o.push('ABCD'[i]+'='+ws[i].className.replace('opt','').trim());" +
                        "var inp=document.getElementById('fillInput');" +
                        "return '题目='+document.getElementById('question').textContent.slice(0,18)+" +
                        "' | 选项状态=['+o.join(',')+'] | 填空框显示='+(document.getElementById('fillRow').style.display)+' 值='+(inp?inp.value:'-')+" +
                        "' | 判定='+document.getElementById('verdict').textContent;})()"));
                System.out.println("[按键测试] 事件记录: " + engine.executeScript("window.__lastKey"));
                System.out.println("[按键测试] keypress: " + engine.executeScript("window.__lastPress"));
                System.out.println("[按键测试] keycap: " + engine.executeScript("window.__lastCap"));
                Platform.exit();
            });
            k3.play();
            return;
        }

        if (getParameters().getRaw().contains("--auto-close")) {
            PauseTransition probe = new PauseTransition(Duration.seconds(2));
            probe.setOnFinished(e -> {
                try {
                    Object result = engine.executeScript(
                            "(function(){var l=document.getElementById('chapterList');" +
                            "var s=document.getElementById('headerStats');" +
                            "return '章节卡=' + (l?l.children.length:-1) + ' 顶栏=' + (s?s.textContent.length:-1) + " +
                            "' bridge存活=' + (window.bridge && window.bridge.stats ? 'yes' : 'no');})()");
                    System.out.println("[App] 页面自检: " + result);
                    /* 验证 JS 侧调用温故桥接方法(无重载)是否派发成功 */
                    try {
                        System.out.println("[App] JS温故调用: " + engine.executeScript(
                                "(function(){try{var d=JSON.parse(bridge.sessionWarm('java',1));" +
                                "return 'OK count=' + d.count}catch(e){return '失败:' + e}})()"));
                    } catch (Exception ex) {
                        System.out.println("[App] JS温故调用异常: " + ex.getMessage());
                    }
                    System.out.println("[App] 屏幕: " + javafx.stage.Screen.getPrimary()
                            + " 缩放=" + javafx.stage.Screen.getPrimary().getOutputScaleX());

                    /* 页面内 rAF 帧率: CSS/JS 动画实际运行的帧率(与合成器帧率不同) */
                    engine.executeScript(
                            "window.__rafN=0; window.__rafFps=0; var t0=performance.now();" +
                            "(function lp(){ window.__rafN++; if(performance.now()-t0<3000) requestAnimationFrame(lp);" +
                            " else window.__rafFps=Math.round(window.__rafN/3); })();");
                    PauseTransition rafRead = new PauseTransition(Duration.seconds(3.6));
                    rafRead.setOnFinished(ev -> {
                        try {
                            System.out.println("[App] 页面动画帧率(rAF): " + engine.executeScript("window.__rafFps") + " fps");
                            /* 滚动压力测试: 连续滚动主页 3 秒, 测滚动中的真实帧率 */
                            engine.executeScript(
                                    "window.__scN=0; window.__scFps=0; var el=document.getElementById('home');" +
                                    "if(el){ el.style.overflowY='auto'; var t1=performance.now(); var d=1;" +
                                    "(function sp(){ window.__scN++; el.scrollTop+=d*41;" +
                                    "if(el.scrollTop>=el.scrollHeight-el.clientHeight-1||el.scrollTop<=0) d=-d;" +
                                    "if(performance.now()-t1<3000) requestAnimationFrame(sp);" +
                                    "else window.__scFps=Math.round(window.__scN/3); })(); }");
                        } catch (Exception ignored) { }
                        PauseTransition scRead = new PauseTransition(Duration.seconds(3.4));
                        scRead.setOnFinished(ev2 -> {
                            try {
                                System.out.println("[App] 滚动中帧率: " + engine.executeScript("window.__scFps") + " fps");
                                /* 对照组: transform 位移(不触发重光栅化)是否更快 */
                                engine.executeScript(
                                        "window.__tfN=0; window.__tfFps=0; var it=document.getElementById('chapterList');" +
                                        "if(it){ var t2=performance.now(); var d2=1; var y=0;" +
                                        "(function tp(){ window.__tfN++; y+=d2*41; if(y>700||y<0) d2=-d2;" +
                                        "it.style.transform='translateY('+(-y)+'px)';" +
                                        "if(performance.now()-t2<3000) requestAnimationFrame(tp);" +
                                        "else window.__tfFps=Math.round(window.__tfN/3); })(); }");
                            } catch (Exception ignored) { }
                            PauseTransition tfRead = new PauseTransition(Duration.seconds(3.4));
                            tfRead.setOnFinished(ev3 -> {
                                try {
                                    System.out.println("[App] transform位移帧率: " + engine.executeScript("window.__tfFps") + " fps");
                                } catch (Exception ignored) { }
                            });
                            tfRead.play();
                        });
                        scRead.play();
                    });
                    rafRead.play();

                    /* 帧率实测: 每帧微调透明度强制整窗重绘, 3 秒后输出实测 fps */
                    new javafx.animation.AnimationTimer() {
                        long frames = 0; long t0 = System.nanoTime(); boolean flip;
                        @Override public void handle(long now) {
                            frames++;
                            flip = !flip;
                            view.setOpacity(flip ? 1f : 0.985f);
                            if (now - t0 >= 3_000_000_000L) {
                                System.out.printf("[App] 强制重绘帧率: %.1f fps%n", frames / 3.0);
                                view.setOpacity(1f);
                                stop();
                            }
                        }
                    }.start();
                } catch (Exception ex) {
                    System.out.println("[App] 页面自检失败: " + ex.getMessage());
                }
            });
            probe.play();
            PauseTransition close = new PauseTransition(Duration.seconds(16));
            close.setOnFinished(e -> Platform.exit());
            close.play();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

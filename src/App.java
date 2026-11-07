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

    /** 单实例锁: 端口被占说明已有实例, 直接退出(双开会搞乱守望恢复与进度写入) */
    private static java.net.ServerSocket instanceLock;

    @Override
    public void start(Stage stage) {
        try {
            instanceLock = new java.net.ServerSocket(48765);
        } catch (Exception e) {
            javafx.application.Platform.exit();
            return;
        }
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

        stage.setTitle("Java 百词斩 v1.9");
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

        /* 界面截图自检: 依次打开五个界面并截屏(界面改动只能从桌面窗口验证, 这个模式把"人工看"变成有图有据) */
        if (getParameters().getRaw().contains("--uishow")) {
            Thread shot = new Thread(() -> {
                String[] names = {"home", "study", "anim", "note", "coll"};
                String[] js = {
                        "(function(){goHome(false);refreshHome();return '章节卡=' + document.getElementById('chapterList').children.length" +
                        " + ' 合集入口张数=' + document.getElementById('animCountN').textContent;})()",
                        "(function(){window.__uiBak = bridge.loadSession('java'); startSession(1);" +
                        "return '队列=' + queue.length + ' 当前第' + (qidx+1) + '张 卡片按钮数=' + document.querySelectorAll('#qcard .cardTools button').length;})()",
                        "(function(){closeNotePanel(); openAnim(queue[qidx]);" +
                        "return '步骤=' + anim.steps.length + ' 自动播放=' + anim.playing + ' 标题=' + document.getElementById('animTitle').textContent" +
                        " + ' 计数=' + document.getElementById('animCap').textContent;})()",
                        "(function(){closeAnim(); openNotePanel(); return '面板已开=' + document.getElementById('noteOverlay').classList.contains('show')" +
                        " + ' 标题=' + document.getElementById('noteTitle').textContent;})()",
                        "(function(){ closeNotePanel(); window.__uiAnim = !bridge.animHas(currentCourse, queue[qidx].id);" +
                        "if (window.__uiAnim) bridge.animAdd(currentCourse, queue[qidx].id);" +
                        "openCollection(); return '面板已开=' + document.getElementById('collOverlay').classList.contains('show')" +
                        " + ' 行数=' + document.getElementById('collList').children.length;})()",
                };
                try {
                    /* 截 WebView 本身: 高分屏与别的窗口遮挡都不影响, 拿到的就是页面像素 */
                    for (int i = 0; i < names.length; i++) {
                        final String script = js[i];
                        System.out.println("[界面] " + names[i] + ": "
                                + fx(new java.util.concurrent.FutureTask<Object>(() -> engine.executeScript(script))));
                        Thread.sleep(850);
                        final javafx.scene.SnapshotParameters sp = new javafx.scene.SnapshotParameters();
                        sp.setFill(javafx.scene.paint.Color.WHITE);
                        javafx.scene.image.WritableImage img = fx(new java.util.concurrent.FutureTask<>(
                                () -> view.snapshot(sp, null)));
                        java.io.File out = new java.io.File(System.getProperty("java.io.tmpdir"), "shan-ui-" + names[i] + ".png");
                        javax.imageio.ImageIO.write(toBuffered(img), "png", out);
                        System.out.println("[界面] 截图 " + out.getAbsolutePath()
                                + " " + (int) img.getWidth() + "x" + (int) img.getHeight());
                    }
                    /* 收尾: 关掉面板、把会话续做文件恢复原样(自检不该动用户的断点进度) */
                    System.out.println("[界面] " + fx(new java.util.concurrent.FutureTask<Object>(() -> engine.executeScript(
                            "(function(){closeAnim();closeNotePanel();closeCollection();" +
                            "if (window.__uiAnim) { try { bridge.animDel(currentCourse, queue[qidx].id); } catch(e){} window.__uiAnim=false; }" +
                            "goHome(false);" +
                            "try{bridge.saveSession('java', window.__uiBak||'');}catch(e){}return '已复原';})()"))));
                    Thread.sleep(400);
                } catch (Exception ex) {
                    System.out.println("[界面] 截图自检失败: " + ex);
                }
                Platform.exit();
            }, "uishow");
            shot.setDaemon(true);
            shot.start();
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
                    /* 空章节(卡库0卡)标签自检: 四级第3-7章应显示「卡库待补」, 不能再被误标成「温故知新」 */
                    try {
                        System.out.println("[App] 空章节标签: " + engine.executeScript(
                                "(function(){try{switchCourse('en');" +
                                "var box=document.getElementById('chapterList'),empty=0,warm=0,cont=0;" +
                                "for(var i=0;i<box.children.length;i++){var g=box.children[i].querySelector('.go');" +
                                "if(!g)continue;var t=g.textContent;" +
                                "if(t.indexOf('卡库待补')>=0)empty++;" +
                                "else if(t.indexOf('温故知新')>=0)warm++;" +
                                "else if(t.indexOf('继续学习')>=0)cont++;}" +
                                "var r='四级章='+box.children.length+' 待补='+empty+' 温故='+warm+' 继续='+cont;" +
                                "switchCourse('java');return r}catch(e){return '失败:'+e}})()"));
                    } catch (Exception ex) {
                        System.out.println("[App] 空章节标签自检异常: " + ex.getMessage());
                    }
                    /* 笔记 + 动画合集自检: 用不存在的卡号 999999 走一遍增删读, 不碰用户真实数据 */
                    try {
                        System.out.println("[App] 笔记/动画自检: " + engine.executeScript(
                                "(function(){try{var T=999999;" +
                                "bridge.noteSet('java',T,'自检¶第二行');var n=bridge.noteGet('java',T);" +
                                "bridge.noteSet('java',T,'');var n2=bridge.noteGet('java',T);" +
                                "bridge.animAdd('java',T);var has=bridge.animHas('java',T);" +
                                "bridge.animDel('java',T);var after=bridge.animHas('java',T);" +
                                "var dom=['animBtn','noteBtn','noteView','animOverlay','collOverlay','animEntry','animStage','anFill']" +
                                ".map(function(i){return document.getElementById(i)?1:0}).join('');" +
                                "var ch=buildSteps({id:1,ch:1,topic:'自检',tag:'概念',q:'这是题干?',fill:false,ans:1," +
                                "opts:['甲','乙','丙','丁'],e:'因为乙最贴。其它都对不上。',d:'int a §1¶;§'}).length;" +
                                "var fl=buildSteps({id:2,ch:1,topic:'自检2',tag:'填空',q:'填 ___',fill:true,ans:'乙',e:'乙即可。'}).length;" +
                                "openAnim({id:1,ch:1,topic:'自检',tag:'概念',q:'这是题干?',fill:false,ans:1," +
                                "opts:['甲','乙','丙','丁'],e:'因为乙最贴。',d:''});" +
                                "var cap=document.getElementById('animCap').textContent;" +
                                "var lbl=document.getElementById('animPP').textContent;" +
                                "closeAnim();" +
                                "return '笔记读回'+n.length+'字/删空后'+n2.length+'字 animHas=' + has + '→删后' + after +" +
                                " ' DOM齐全='+(/^1{8}$/.test(dom)) + ' 选择'+ch+'步 填空'+fl+'步'" +
                                " + ' 播放器首帧='+cap+' 按钮='+lbl;}catch(e){return '失败:'+e}})()"));
                    } catch (Exception ex) {
                        System.out.println("[App] 笔记/动画自检异常: " + ex.getMessage());
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

    /**
     * 把一段有返回值的活儿交给 FX 线程执行并等结果(后台自检线程用)。
     * FutureTask 同时实现 Runnable 与 Callable, 直接传会撞上 runLater 的重载歧义, 故先转成 Runnable 变量。
     */
    private static <T> T fx(java.util.concurrent.FutureTask<T> task) {
        Runnable r = task;
        Platform.runLater(r);
        try {
            return task.get();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /** javafx.swing 不在模块清单里, 用不上 SwingFXUtils, 手工过一遍像素 */
    private static java.awt.image.BufferedImage toBuffered(javafx.scene.image.Image src) {
        int w = (int) src.getWidth(), h = (int) src.getHeight();
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javafx.scene.image.PixelReader pr = src.getPixelReader();
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            pr.getPixels(0, y, w, 1, javafx.scene.image.PixelFormat.getIntArgbInstance(), row, 0, w);
            out.setRGB(0, y, w, 1, row, 0, w);
        }
        return out;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

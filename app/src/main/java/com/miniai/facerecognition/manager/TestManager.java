package com.miniai.facerecognition.manager;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class TestManager {
    private static final String TAG = "[TreeHole]TtsManager";

    private final HashMap<String, Queue<String>> queries = new HashMap<>();

    private static final class Holder {
        private static final TestManager INSTANCE = new TestManager();
    }

    /**
     * Default constructor
     */
    private TestManager() {
    }

    /**
     * Single instance.
     *
     * @return the instance.
     */
    public static TestManager getInstance() {
        return Holder.INSTANCE;
    }

    public boolean init() {
        queries.put("Normal", generateNormalQueries());
        queries.put("Warning", generateWarningQueries());
        queries.put("Danger", generateDangerQueries());
        return true;
    }

    public Queue<String> getQueries(String key) {
        if (queries.containsKey(key)) {
            return queries.get(key);
        } else {
            return new LinkedList<>();
        }
    }

    private Queue<String> generateNormalQueries() {
        Queue<String> queries = new LinkedList<>();
        queries.add("你好");
        queries.add("我今天被老师表扬了");
        queries.add("我在英文课上回答问题很积极");
        return queries;
    }

    private Queue<String> generateWarningQueries() {
        Queue<String> queries = new LinkedList<>();
        queries.add("你好");
        queries.add("我的心情很不好");
        queries.add("今天考试没考好");
        queries.add("我语文不是太好");
        return queries;
    }

    private Queue<String> generateDangerQueries() {
        Queue<String> queries = new LinkedList<>();
        queries.add("你好");
        queries.add("我觉得活着很没意思");
        queries.add("我学习不好，老师不喜欢我，我的同学们也总是欺负我");
        queries.add("他们总是嘲笑我，有的时候还打我");
        return queries;
    }

}

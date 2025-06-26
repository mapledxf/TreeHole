package com.miniai.facerecognition.manager;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.miniai.facerecognition.chat.ChatMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ReportManager {
    private static final String TAG = "[TreeHole]ReportManager";
    private static final String URL = "http://treehole.gleap.cc/report";
    private final OkHttpClient client = new OkHttpClient();

    private static final class Holder {
        private static final ReportManager INSTANCE = new ReportManager();
    }

    /**
     * Default constructor
     */
    private ReportManager() {
    }

    /**
     * Single instance.
     *
     * @return the instance.
     */
    public static ReportManager getInstance() {
        return Holder.INSTANCE;
    }

    public boolean init() {
        return true;
    }

    public void report(String userName, List<ChatMessage> messages) {
        try {
            JSONArray messagesArray = new JSONArray();
            for (ChatMessage msg : messages) {
                JSONObject messageJson = new JSONObject();
                messageJson.put("role", msg.getType() == ChatMessage.TYPE_USER ? "user" : "assistant");
                messageJson.put("content", msg.getContent());
                messagesArray.put(messageJson);
            }

            new Thread(() -> {
                try {
                    Pair<String,String> evaluation = ChatManager.getInstance().evaluate(messagesArray.toString());
                    if ("无".equals(evaluation.first)) {
                        return;
                    }
                    JSONObject json = new JSONObject();
                    json.put("time", System.currentTimeMillis() / 1000);
                    json.put("username", userName);
                    json.put("label", evaluation.first);
                    json.put("reason", evaluation.second);
                    json.put("history", messagesArray);

                    Log.d(TAG, "report: " + json);
                    RequestBody body = RequestBody.create(
                            json.toString(), MediaType.parse("application/json; charset=utf-8"));

                    Request request = new Request.Builder()
                            .url(URL)
                            .post(body)
                            .build();

                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            Log.e(TAG, "onFailure: ", e);
                            // 处理失败
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            // 处理响应
                            String resp = response.body().string();
                            Log.d(TAG, "onResponse: " + resp);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "report: ", e);
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "report: ", e);
        }

    }
}

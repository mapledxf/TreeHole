package com.miniai.facerecognition.manager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.miniai.facerecognition.App;
import com.miniai.facerecognition.Utils;
import com.miniai.facerecognition.callback.ChatCallback;
import com.miniai.facerecognition.chat.ChatAdapter;
import com.miniai.facerecognition.chat.ChatMessage;
import com.miniai.facerecognition.chat.DeepSeekResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ChatManager {
    private static final String TAG = "[TreeHole]ChatManager";

    private String prompt;
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    // 定义正则表达式模式
    private final Pattern pattern = Pattern.compile(
            "<评估>\\s*(.*?)\\s*<理由>\\s*(.*?)\\s*<正文>\\s*(.*)",
            Pattern.DOTALL
    );
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatCallback callback;
    private RecyclerView recyclerView;

    private static final class Holder {
        private static final ChatManager INSTANCE = new ChatManager();
    }

    /**
     * Default constructor
     */
    private ChatManager() {
    }

    /**
     * Single instance.
     *
     * @return the instance.
     */
    public static ChatManager getInstance() {
        return Holder.INSTANCE;
    }

    public void setChatCallback(ChatCallback callback) {
        this.callback = callback;
    }

    public void init(Activity activity, RecyclerView chatRecyclerView) {
        this.recyclerView = chatRecyclerView;
        chatAdapter = new ChatAdapter(messages);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        chatRecyclerView.setAdapter(chatAdapter);
        prompt = Utils.readFileFromAssets(App.getInstance(), "prompt.txt");
        Log.d(TAG, "init success");
    }

    public void addUserMessage(String message) {
        Log.d(TAG, "addUserMessage: " + message);
        chatAdapter.addMessage(new ChatMessage(ChatMessage.TYPE_USER, message));
        chatAdapter.addMessage(new ChatMessage(ChatMessage.TYPE_DEEPSEEK, ""));
        recyclerView.scrollToPosition(messages.size() - 1);
        try {
            requestDeepSeek();
        } catch (JSONException e) {
            Log.e(TAG, "addUserMessage: ", e);
        }
    }

    private void requestDeepSeek() throws JSONException {
        if (callback != null) {
            callback.onChatStart();
        }

        String requestBody = getRequestBody();

        Log.d(TAG, "requestDeepSeek: " + requestBody);

        RequestBody body = RequestBody.create(requestBody, MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url("https://api.deepseek.com/chat/completions")
                .header("Authorization", "Bearer sk-b6e4dfe5aa9c475f8209c1c9c02d5cf0")
                .post(body)
                .build();

        // 异步执行
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onChatError("Network error");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response res) throws IOException {
                if (res.isSuccessful() && res.body() != null) {
                    ResponseBody responseBody = res.body();
                    BufferedReader reader = new BufferedReader(responseBody.charStream());
                    boolean shouldOutput = false;
                    StringBuilder evaluation = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String jsonData = line.substring(6);
                            if (jsonData.trim().equals("[DONE]")) {
                                Log.d(TAG, "onResponse: " + messages.get(messages.size() - 1).getContent());
                                if (callback != null) {
                                    callback.onChatEnd();
                                    mainHandler.post(() -> recyclerView.scrollToPosition(messages.size() - 1));
                                }
                                break;
                            }
                            try {
                                DeepSeekResponse response = gson.fromJson(jsonData, DeepSeekResponse.class);
                                if (response != null &&
                                        response.getChoices() != null &&
                                        !response.getChoices().isEmpty() &&
                                        response.getChoices().get(0).getDelta() != null) {
                                    String content = response.getChoices().get(0).getDelta().getContent();
                                    if (!TextUtils.isEmpty(content)) {
                                        if (shouldOutput) {
                                            appendAIMessage(content);
                                        } else {
                                            // 解析<评估> XXX <理由> XXX <正文> XXX
                                            evaluation.append(content);

                                            Matcher matcher = pattern.matcher(evaluation.toString());
                                            if (matcher.find()) {
                                                String label = matcher.group(1);
                                                String reason = matcher.group(2);
                                                String text = matcher.group(3);
                                                callback.OnChatEvaluation(label, reason);
                                                Log.d(TAG, "Label: " + label);
                                                Log.d(TAG, "Reason: " + reason);
                                                Log.d(TAG, "Content: " + text);
                                                if (!TextUtils.isEmpty(text)) {
                                                    appendAIMessage(text);
                                                }
                                                shouldOutput = true;
                                            }
                                        }
                                    }
                                }
                            } catch (JsonSyntaxException e) {
                                callback.onChatError("json pared error " + e);
                            }
                        }
                    }
                    reader.close();
                } else {
                    callback.onChatError("Request error");
                }
            }
        });
    }

    @NonNull
    private String getRequestBody() throws JSONException {
        JSONObject jsonBodyObject = new JSONObject();
        jsonBodyObject.put("model", "deepseek-chat");
        jsonBodyObject.put("stream", true);

        JSONArray messagesArray = new JSONArray();
        JSONObject systemPrompt = new JSONObject();
        systemPrompt.put("role", "system");
        systemPrompt.put("content", prompt);
        messagesArray.put(systemPrompt);
        for (ChatMessage msg : messages) {
            JSONObject messageJson = new JSONObject();
            messageJson.put("role", msg.getType() == ChatMessage.TYPE_USER ? "user" : "assistant");
            messageJson.put("content", msg.getContent());
            messagesArray.put(messageJson);
        }
        jsonBodyObject.put("messages", messagesArray);
        return jsonBodyObject.toString();
    }

    private void appendAIMessage(String message) {
        mainHandler.post(() -> {
            String msg = chatAdapter.appendAIMessage(message);
            recyclerView.scrollToPosition(messages.size() - 1);
            TtsManager.getInstance().play(msg);
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    public void reset() {
        messages.clear();
        chatAdapter.notifyDataSetChanged();
    }

}

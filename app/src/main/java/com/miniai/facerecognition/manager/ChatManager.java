package com.miniai.facerecognition.manager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

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
import com.miniai.facerecognition.chat.DeepSeekStreamResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private String chatPrompt;
    private String evaluationPrompt;
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    // 定义正则表达式模式
//    private final Pattern pattern = Pattern.compile(
//            "<评估>\\s*(.*?)\\s*<理由>\\s*(.*?)",
//            Pattern.DOTALL
//    );
    Pattern pattern = Pattern.compile("<评估>\\s*(.*?)\\s*<理由>\\s*(.*)");

    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatCallback callback;
    private RecyclerView recyclerView;
    private WeakReference<Activity> ref;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

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
        this.ref = new WeakReference<>(activity);
        this.recyclerView = chatRecyclerView;
        chatAdapter = new ChatAdapter(messages);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        chatRecyclerView.setAdapter(chatAdapter);
        chatPrompt = Utils.readFileFromAssets(App.getInstance(), "chat_prompt.txt");
        evaluationPrompt = Utils.readFileFromAssets(App.getInstance(), "evaluation_prompt.txt");
        Log.d(TAG, "init success");
    }

    public void addUserMessage() {
        if (!isRunning.get()){
            return;
        }
        chatAdapter.addMessage(new ChatMessage(ChatMessage.TYPE_USER, ""));
        recyclerView.scrollToPosition(messages.size() - 1);
    }

    public void addAIMessage() {
        if(!isRunning.get()){
            return;
        }
        chatAdapter.addMessage(new ChatMessage(ChatMessage.TYPE_DEEPSEEK, ""));
        recyclerView.scrollToPosition(messages.size() - 1);
    }

    public void setUserMessage(String message) {
        if (!isRunning.get()) {
            return;
        }
        chatAdapter.setMessage(message);
        recyclerView.scrollToPosition(messages.size() - 1);
    }


    public void appendAIMessage(String message) {
        if (isRunning.get() && ref.get() != null) {
            ref.get().runOnUiThread(() -> {
                String msg = chatAdapter.appendMessage(message);
                recyclerView.scrollToPosition(messages.size() - 1);
                TtsManager.getInstance().play(msg);
            });
        }
    }

    public Pair<String, String> evaluate(String string) {
        try {
            JSONObject jsonBodyObject = getEvaluationBody(string);
            Log.d(TAG, "evaluate: " + jsonBodyObject);

            RequestBody body = RequestBody.create(
                    jsonBodyObject.toString(),
                    MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url("https://api.deepseek.com/chat/completions")
                    .header("Authorization", "Bearer sk-b6e4dfe5aa9c475f8209c1c9c02d5cf0")
                    .post(body)
                    .build();
            try (Response res = client.newCall(request).execute()) {
                if (res.isSuccessful() && res.body() != null) {
                    String responseBody = res.body().string(); // 一次性读取整个响应体
                    Log.d(TAG, "evaluate result: " + responseBody);
                    DeepSeekResponse response = gson.fromJson(responseBody, DeepSeekResponse.class);
                    if (response != null &&
                            response.getChoices() != null &&
                            !response.getChoices().isEmpty() &&
                            response.getChoices().get(0).getMessage() != null) {
                        String fullContent = response.getChoices().get(0).getMessage().getContent();
                        Matcher matcher = pattern.matcher(fullContent);
                        if (matcher.find()) {
                            String label = matcher.group(1);
                            if (label != null) {
                                label = label.trim();
                            }
                            String reason = matcher.group(2);
                            if (reason != null) {
                                reason = reason.trim();
                            }

                            Log.d(TAG, "Label: " + label);
                            Log.d(TAG, "Reason: " + reason);

                            return new Pair<>(label, reason);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "evaluate: ", e);
            }


        } catch (Exception e) {
            Log.e(TAG, "evaluate: ", e);
        }

        return null;
    }

    @NonNull
    private JSONObject getEvaluationBody(String string) throws JSONException {
        JSONObject jsonBodyObject = new JSONObject();
        jsonBodyObject.put("model", "deepseek-chat");
        jsonBodyObject.put("stream", false);


        JSONObject systemPrompt = new JSONObject();
        systemPrompt.put("role", "system");
        systemPrompt.put("content", evaluationPrompt);

        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", string);

        JSONArray messagesArray = new JSONArray();
        messagesArray.put(systemPrompt);
        messagesArray.put(userMessage);

        jsonBodyObject.put("messages", messagesArray);
        return jsonBodyObject;
    }

    public void requestDeepSeek() {
        if (callback != null) {
            callback.onChatStart();
        }

        try {
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
                        String line;
                        while ((line = reader.readLine()) != null && isRunning.get()) {
                            if (line.startsWith("data: ")) {
                                String jsonData = line.substring(6);
                                if (jsonData.trim().equals("[DONE]")) {
                                    Log.d(TAG, "onResponse: " + messages.get(messages.size() - 1).getContent());
                                    if (callback != null) {
                                        callback.OnChatEnd(messages);
                                    }
                                    if (ref.get() != null) {
                                        ref.get().runOnUiThread(() -> recyclerView.scrollToPosition(messages.size() - 1));
                                    }
                                    break;
                                }
                                try {
                                    DeepSeekStreamResponse response = gson.fromJson(jsonData, DeepSeekStreamResponse.class);
                                    if (response != null &&
                                            response.getChoices() != null &&
                                            !response.getChoices().isEmpty() &&
                                            response.getChoices().get(0).getDelta() != null) {
                                        String content = response.getChoices().get(0).getDelta().getContent();
                                        if (!TextUtils.isEmpty(content)) {
                                            appendAIMessage(content);
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
        } catch (Exception e) {
            Log.e(TAG, "requestDeepSeek: ", e);
        }
    }

    @NonNull
    private String getRequestBody() throws JSONException {
        JSONObject jsonBodyObject = new JSONObject();
        jsonBodyObject.put("model", "deepseek-chat");
        jsonBodyObject.put("stream", true);

        JSONArray messagesArray = new JSONArray();
        JSONObject systemPrompt = new JSONObject();
        systemPrompt.put("role", "system");
        systemPrompt.put("content", chatPrompt);
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


    public void start() {
        Log.d(TAG, "start: ");
        this.isRunning.set(true);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void stop() {
        Log.d(TAG, "stop: ");
        isRunning.set(false);
        messages.clear();
        chatAdapter.notifyDataSetChanged();
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

}

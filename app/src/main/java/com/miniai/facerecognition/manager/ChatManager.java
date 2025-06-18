package com.miniai.facerecognition.manager;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
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
    OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();
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

    public void init(Activity activity, RecyclerView chatRecyclerView, ChatCallback callback) {
        this.recyclerView = chatRecyclerView;
        this.callback = callback;
        chatAdapter = new ChatAdapter(messages);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        chatRecyclerView.setAdapter(chatAdapter);

        Log.d(TAG, "init success");
    }

    public void addUserMessage(String message) {
        Log.d(TAG, "addUserMessage: " + message);
        chatAdapter.addMessage(new ChatMessage(ChatMessage.TYPE_USER, message));
        chatAdapter.addMessage(new ChatMessage(ChatMessage.TYPE_DEEPSEEK, ""));
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
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String jsonData = line.substring(6);
                            if (jsonData.trim().equals("[DONE]")) {
                                if (callback != null) {
                                    callback.onChatEnd();
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
                                    if (content != null && !content.isEmpty()) {
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
    }

    @NonNull
    private String getRequestBody() throws JSONException {
        JSONObject jsonBodyObject = new JSONObject();
        jsonBodyObject.put("model", "deepseek-chat");
        jsonBodyObject.put("stream", true);

        JSONArray messagesArray = new JSONArray();
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
            chatAdapter.appendAIMessage(message);
            recyclerView.scrollToPosition(messages.size() - 1);
        });
    }

}

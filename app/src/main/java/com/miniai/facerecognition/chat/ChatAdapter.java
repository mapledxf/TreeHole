package com.miniai.facerecognition.chat;

import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.miniai.facerecognition.App;
import com.miniai.facerecognition.R;

import java.util.List;

import io.noties.markwon.Markwon;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<ChatMessage> messages;
    private final Markwon markdown;
    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
        markdown = Markwon.builder(App.getInstance()).build();
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == ChatMessage.TYPE_USER) {
            View view = inflater.inflate(R.layout.item_user_message, parent, false);
            return new UserMessageViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_deepseek_message, parent, false);
            return new DeepSeekMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);

        if (holder.getItemViewType() == ChatMessage.TYPE_USER) {
            ((UserMessageViewHolder) holder).bind(message);
        } else {
            Spanned markdown = this.markdown.toMarkdown(message.getContent().toString());
            ((DeepSeekMessageViewHolder) holder).bind(markdown);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    // 添加流式打字机效果的消息
    public void appendAIMessage(String content) {
        final int position = messages.size() - 1;
        messages.get(position).getContent().append(content);
        notifyItemChanged(position);
    }

    // ViewHolder 类
    public static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;

        public UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.user_message_text);
        }

        public void bind(ChatMessage message) {
            messageText.setText(message.getContent().toString());
        }
    }

    public static class DeepSeekMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;

        public DeepSeekMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.deepseek_message_text);
        }

        public void bind(Spanned message) {
            messageText.setText(message);
        }
    }
}
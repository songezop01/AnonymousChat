package com.example.anonymouschat;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "MessageAdapter";
    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    private List<Message> messages;
    private String currentUserId;

    public MessageAdapter(List<Message> messages, String currentUserId) {
        this.messages = messages != null ? messages : new ArrayList<>();
        this.currentUserId = currentUserId;
    }

    public void updateMessages(List<Message> newMessages) {
        this.messages.clear();
        this.messages.addAll(newMessages != null ? newMessages : new ArrayList<>());
        notifyDataSetChanged();
        Log.d(TAG, "Updated messages, count: " + messages.size());
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        return message.getFromUid().equals(currentUserId) ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SENT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_sent, parent, false);
            return new SentMessageViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received, parent, false);
            return new ReceivedMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messages.get(position);
        if (holder instanceof SentMessageViewHolder) {
            ((SentMessageViewHolder) holder).bind(message);
        } else {
            ((ReceivedMessageViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView timestampText;

        SentMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timestampText = itemView.findViewById(R.id.timestampText);
        }

        void bind(Message message) {
            String text = message.getText() != null ? message.getText() : "";
            messageText.setText(text);
            timestampText.setText(formatTimestamp(message.getTimestamp()));
            Log.d(TAG, "Bound sent message: " + text);
        }
    }

    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView nicknameText;
        TextView timestampText;

        ReceivedMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            nicknameText = itemView.findViewById(R.id.nicknameText);
            timestampText = itemView.findViewById(R.id.timestampText);
        }

        void bind(Message message) {
            String text = message.getText() != null ? message.getText() : "";
            String nickname = message.getNickname() != null ? message.getNickname() : "Unknown";
            messageText.setText(text);
            nicknameText.setText(nickname);
            timestampText.setText(formatTimestamp(message.getTimestamp()));
            Log.d(TAG, "Bound received message: " + text + ", nickname: " + nickname);
        }
    }

    private static String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return "Unknown Time";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
package com.example.anonymouschat;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.anonymouschat.databinding.ActivityChatBinding;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";
    private ActivityChatBinding binding;
    private ChatViewModel viewModel;
    private MessageAdapter messageAdapter;
    private String chatId;
    private String chatType;
    private String uid;
    private List<Message> messages = new ArrayList<>();
    private SocketManager socketManager;
    private boolean isGroupChat;
    private boolean hasLeftGroup = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Log.d(TAG, "ChatActivity onCreate");

        chatId = getIntent().getStringExtra("chatId");
        chatType = getIntent().getStringExtra("chatType");
        uid = getIntent().getStringExtra("uid");
        if (chatId == null || chatType == null || uid == null) {
            Log.e(TAG, "chatId, chatType, or uid is null, finishing activity. chatId: " + chatId + ", chatType: " + chatType + ", uid: " + uid);
            finish();
            return;
        }
        Log.d(TAG, "chatId: " + chatId + ", chatType: " + chatType + ", uid: " + uid);

        isGroupChat = chatType.equals("group");

        socketManager = SocketManager.getInstance(this);

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.init(this, chatId, chatType, uid);

        binding.messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new MessageAdapter(messages, uid);
        binding.messagesRecyclerView.setAdapter(messageAdapter);

        viewModel.getMessages().observe(this, updatedMessages -> {
            if (updatedMessages == null) {
                Log.e(TAG, "Received null messages from ViewModel");
                return;
            }
            messages.clear();
            messages.addAll(updatedMessages);
            messageAdapter.updateMessages(messages);
            binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
            Log.d(TAG, "Updated messages, count: " + messages.size());
        });

        binding.sendMessageButton.setOnClickListener(v -> {
            String messageText = binding.messageInput.getText().toString().trim();
            if (!messageText.isEmpty()) {
                sendMessage(messageText);
                binding.messageInput.setText("");
            }
        });

        if (isGroupChat) {
            Button leaveGroupButton = new Button(this);
            leaveGroupButton.setText(R.string.leave_group);
            leaveGroupButton.setOnClickListener(v -> {
                if (!hasLeftGroup) {
                    viewModel.leaveGroup();
                    hasLeftGroup = true;
                    Log.d(TAG, "Leave group button clicked, hasLeftGroup: " + hasLeftGroup);
                }
            });
            binding.getRoot().addView(leaveGroupButton);
        }
    }

    private void sendMessage(String messageText) {
        try {
            long timestamp = System.currentTimeMillis();
            messages.add(new Message(uid, messageText, "You", timestamp));
            messageAdapter.updateMessages(messages);
            binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
            Log.d(TAG, "Local echo: " + messageText);

            viewModel.sendMessage(messageText);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send message: " + e.getMessage(), e);
            runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(ChatActivity.this);
                builder.setTitle(getString(R.string.send_message_failed_title));
                builder.setMessage(getString(R.string.send_message_failed_message) + "\n錯誤：" + e.getMessage());
                builder.setPositiveButton(getString(R.string.retry), (dialog, which) -> sendMessage(messageText));
                builder.setNegativeButton(R.string.cancel, null);
                builder.show();
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isGroupChat && !hasLeftGroup) {
            viewModel.leaveGroup();
            hasLeftGroup = true;
            Log.d(TAG, "onDestroy: Leaving group, hasLeftGroup: " + hasLeftGroup);
        }
        viewModel.cleanup();
        Log.d(TAG, "ChatActivity onDestroy");
    }
}
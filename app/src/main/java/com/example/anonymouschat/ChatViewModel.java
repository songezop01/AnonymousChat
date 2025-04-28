package com.example.anonymouschat;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatViewModel extends ViewModel {
    private static final String TAG = "ChatViewModel";
    private MutableLiveData<List<Message>> messagesLiveData = new MutableLiveData<>(new ArrayList<>());
    private SocketManager socketManager;
    private String chatId;
    private String chatType;
    private String uid;
    private boolean hasLeftGroup = false;

    public void init(Context context, String chatId, String chatType, String uid) {
        this.socketManager = SocketManager.getInstance(context);
        this.chatId = chatId;
        this.chatType = chatType;
        this.uid = uid;
        setupSocketListeners();
        loadChatHistory();
        Log.d(TAG, "ChatViewModel initialized for chatId: " + chatId + ", chatType: " + chatType);
    }

    public LiveData<List<Message>> getMessages() {
        return messagesLiveData;
    }

    public void sendMessage(String messageText) {
        if (messageText.isEmpty()) return;

        long timestamp = System.currentTimeMillis();
        try {
            JSONObject messageData = new JSONObject();
            messageData.put("chatId", chatId);
            messageData.put("fromUid", uid);
            messageData.put("message", messageText);
            messageData.put("timestamp", timestamp);
            if (chatType.equals("group")) {
                socketManager.sendGroupMessage(messageData);
                Log.d(TAG, "Sent group message: " + messageText);
            } else {
                socketManager.sendChatMessage(messageData);
                Log.d(TAG, "Sent chat message: " + messageText);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send message: " + e.getMessage(), e);
        }
    }

    public void leaveGroup() {
        if (!chatType.equals("group") || hasLeftGroup) {
            Log.d(TAG, "Not a group chat or already left group, skipping leaveGroup");
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("groupId", chatId);
            data.put("uid", uid);
            socketManager.getSocket().emit("leaveGroup", data);
            hasLeftGroup = true;
            Log.d(TAG, "Sent leave group request: " + chatId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send leave group request: " + e.getMessage(), e);
        }
    }

    private void setupSocketListeners() {
        socketManager.getSocket().on("chatMessage", args -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "chatMessage event received null or empty args");
                    return;
                }
                JSONObject messageData = (JSONObject) args[0];
                String receivedChatId = messageData.optString("chatId", null);
                if (receivedChatId == null || !receivedChatId.equals(chatId)) return;

                String fromUid = messageData.optString("fromUid", null);
                String messageText = messageData.optString("message", "");
                String nickname = messageData.optString("nickname", "Unknown");
                long timestamp = messageData.optLong("timestamp", System.currentTimeMillis());
                if (fromUid == null) {
                    Log.e(TAG, "chatMessage event missing fromUid");
                    return;
                }

                List<Message> currentMessages = new ArrayList<>(messagesLiveData.getValue() != null ? messagesLiveData.getValue() : new ArrayList<>());
                currentMessages.add(new Message(fromUid, messageText, nickname, timestamp));
                Collections.sort(currentMessages, (m1, m2) -> Long.compare(m1.getTimestamp(), m2.getTimestamp()));
                messagesLiveData.postValue(currentMessages);
                Log.d(TAG, "Received chat message: " + messageText);
            } catch (Exception e) {
                Log.e(TAG, "Failed to process chat message: " + e.getMessage(), e);
            }
        });

        socketManager.getSocket().on("groupMessage", args -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "groupMessage event received null or empty args");
                    return;
                }
                JSONObject messageData = (JSONObject) args[0];
                String receivedChatId = messageData.optString("chatId", null);
                if (receivedChatId == null || !receivedChatId.equals(chatId)) return;

                String type = messageData.optString("type", "user");
                List<Message> currentMessages = new ArrayList<>(messagesLiveData.getValue() != null ? messagesLiveData.getValue() : new ArrayList<>());
                if (type.equals("system")) {
                    String messageText = messageData.optString("message", "");
                    currentMessages.add(new Message("system", messageText, "System", System.currentTimeMillis()));
                    Log.d(TAG, "Received system message: " + messageText);
                } else {
                    String fromUid = messageData.optString("fromUid", null);
                    String messageText = messageData.optString("message", "");
                    String nickname = messageData.optString("nickname", "Unknown");
                    long timestamp = messageData.optLong("timestamp", System.currentTimeMillis());
                    if (fromUid == null) {
                        Log.e(TAG, "groupMessage event missing fromUid");
                        return;
                    }
                    currentMessages.add(new Message(fromUid, messageText, nickname, timestamp));
                    Log.d(TAG, "Received group message: " + messageText);
                }
                Collections.sort(currentMessages, (m1, m2) -> Long.compare(m1.getTimestamp(), m2.getTimestamp()));
                messagesLiveData.postValue(currentMessages);
            } catch (Exception e) {
                Log.e(TAG, "Failed to process group message: " + e.getMessage(), e);
            }
        });

        socketManager.getSocket().on("getChatHistoryResponse", args -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "getChatHistoryResponse event received null or empty args");
                    return;
                }
                JSONObject response = (JSONObject) args[0];
                boolean success = response.optBoolean("success", false);
                if (success) {
                    JSONArray history = response.optJSONArray("messages");
                    if (history == null) {
                        Log.e(TAG, "getChatHistoryResponse event missing messages array");
                        return;
                    }
                    List<Message> newMessages = new ArrayList<>();
                    for (int i = 0; i < history.length(); i++) {
                        JSONObject msg = history.getJSONObject(i);
                        String fromUid = msg.optString("fromUid", null);
                        String messageText = msg.optString("message", "");
                        String nickname = msg.optString("nickname", "Unknown");
                        long timestamp = msg.optLong("timestamp", System.currentTimeMillis());
                        if (fromUid == null) {
                            Log.w(TAG, "Message missing fromUid at index: " + i);
                            continue;
                        }
                        newMessages.add(new Message(fromUid, messageText, nickname, timestamp));
                    }
                    Collections.sort(newMessages, (m1, m2) -> Long.compare(m1.getTimestamp(), m2.getTimestamp()));
                    messagesLiveData.postValue(newMessages);
                    Log.d(TAG, "Loaded chat history, message count: " + newMessages.size());
                } else {
                    String message = response.optString("message", "Unknown error");
                    Log.e(TAG, "Failed to load chat history: " + message);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to process chat history: " + e.getMessage(), e);
            }
        });
    }

    private void loadChatHistory() {
        try {
            JSONObject request = new JSONObject();
            request.put("chatId", chatId);
            socketManager.getSocket().emit("getChatHistory", request);
            Log.d(TAG, "Requested chat history for chatId: " + chatId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to request chat history: " + e.getMessage(), e);
        }
    }

    public void cleanup() {
        if (socketManager != null && socketManager.getSocket() != null) {
            socketManager.getSocket().off("chatMessage");
            socketManager.getSocket().off("groupMessage");
            socketManager.getSocket().off("getChatHistoryResponse");
        }
        Log.d(TAG, "ChatViewModel cleanup");
    }
}
package com.example.anonymouschat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import io.socket.client.IO;
import io.socket.client.Socket;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class SocketManager {
    private static final String TAG = "SocketManager";
    private static SocketManager instance;
    private Socket socket;
    private Context context;
    private List<JSONObject> messageQueue = new ArrayList<>();
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final long INITIAL_RECONNECT_DELAY = 1000; // 初始重連延遲 1 秒
    private int reconnectAttempts = 0;

    private SocketManager(Context context) {
        this.context = context.getApplicationContext();
        try {
            // 配置 Socket.IO 選項，優先使用 WebSocket
            IO.Options options = new IO.Options();
            options.transports = new String[]{"websocket"}; // 優先使用 WebSocket
            options.reconnection = true;
            options.reconnectionAttempts = MAX_RECONNECT_ATTEMPTS;
            options.reconnectionDelay = INITIAL_RECONNECT_DELAY;
            options.forceNew = false;

            socket = IO.socket("https://anonymous-chat-server-d43x.onrender.com", options);
            setupSocketListeners();
            socket.connect();
            Log.d(TAG, "Socket initialized and connected");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            Log.e(TAG, "Socket initialization failed: " + e.getMessage());
        }
    }

    public static synchronized SocketManager getInstance(Context context) {
        if (instance == null) {
            instance = new SocketManager(context);
        }
        return instance;
    }

    private void setupSocketListeners() {
        socket.on(Socket.EVENT_CONNECT, args -> {
            Log.d(TAG, "Socket connected");
            reconnectAttempts = 0; // 重置重連次數
            synchronized (messageQueue) {
                for (JSONObject data : new ArrayList<>(messageQueue)) {
                    socket.emit(data.optString("event"), data);
                }
                messageQueue.clear();
            }
        });

        socket.on(Socket.EVENT_DISCONNECT, args -> {
            Log.w(TAG, "Socket disconnected: " + args[0]);
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            Log.e(TAG, "Socket connection error: " + args[0]);
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                long delay = INITIAL_RECONNECT_DELAY * (1L << reconnectAttempts); // 指數退避
                Log.d(TAG, "Attempting to reconnect in " + delay + "ms (Attempt " + (reconnectAttempts + 1) + ")");
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    socket.connect();
                    reconnectAttempts++;
                }, delay);
            } else {
                Log.e(TAG, "Max reconnect attempts reached, giving up.");
            }
        });
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public void connect() {
        if (socket != null && !socket.connected()) {
            socket.connect();
            Log.d(TAG, "Socket reconnecting");
        }
    }

    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            Log.d(TAG, "Socket disconnected");
        }
    }

    public void sendChatMessage(JSONObject messageData) {
        try {
            messageData.put("event", "chatMessage");
            if (isConnected()) {
                socket.emit("chatMessage", messageData);
            } else {
                Log.w(TAG, "Socket not connected, queuing message");
                synchronized (messageQueue) {
                    messageQueue.add(messageData);
                }
                connect();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send chat message: " + e.getMessage(), e);
        }
    }

    public void sendGroupMessage(JSONObject messageData) {
        try {
            messageData.put("event", "groupMessage");
            if (isConnected()) {
                socket.emit("groupMessage", messageData);
            } else {
                Log.w(TAG, "Socket not connected, queuing message");
                synchronized (messageQueue) {
                    messageQueue.add(messageData);
                }
                connect();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send group message: " + e.getMessage(), e);
        }
    }
}
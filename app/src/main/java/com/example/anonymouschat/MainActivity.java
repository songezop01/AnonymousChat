package com.example.anonymouschat;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.anonymouschat.databinding.ActivityMainBinding;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private Socket socket;
    private MessageAdapter adapter;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUserId = "user_" + System.currentTimeMillis();

        try {
            socket = IO.socket("http://192.168.1.101:3000"); // 替換為你的電腦 IP
            socket.on(Socket.EVENT_CONNECT, args -> runOnUiThread(() ->
                    Toast.makeText(this, "已連接到服務器", Toast.LENGTH_SHORT).show()));
            socket.on("chatMessage", args -> runOnUiThread(() -> {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String userId = data.getString("userId");
                    String content = data.getString("content");
                    long timestamp = data.getLong("timestamp");
                    Message message = new Message(userId, content, timestamp);
                    adapter.addMessage(message);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));
            socket.connect();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "連接失敗", Toast.LENGTH_SHORT).show();
        }

        adapter = new MessageAdapter(currentUserId);
        binding.messageRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.messageRecyclerView.setAdapter(adapter);

        binding.sendButton.setOnClickListener(v -> {
            String content = binding.messageEditText.getText().toString().trim();
            if (!content.isEmpty()) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("userId", currentUserId);
                    data.put("content", content);
                    data.put("timestamp", System.currentTimeMillis());
                    socket.emit("chatMessage", data);
                    binding.messageEditText.setText("");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (socket != null) {
            socket.disconnect();
        }
    }
}
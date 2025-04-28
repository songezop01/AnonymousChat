package com.example.anonymouschat;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private SocketManager socketManager;
    private EditText usernameInput;
    private EditText passwordInput;
    private EditText nicknameInput;
    private Button registerButton;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 設置語言（示例：默認為繁體中文，實際應從 SharedPreferences 獲取）
        String language = "zh";
        Locale locale = new Locale(language);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());

        setContentView(R.layout.activity_main);

        socketManager = SocketManager.getInstance(this);
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        nicknameInput = findViewById(R.id.nicknameInput);
        registerButton = findViewById(R.id.registerButton);
        loginButton = findViewById(R.id.loginButton);

        registerButton.setOnClickListener(v -> register());
        loginButton.setOnClickListener(v -> login());

        socketManager.getSocket().on("registerResponse", args -> runOnUiThread(() -> {
            try {
                JSONObject response = (JSONObject) args[0];
                if (response.getBoolean("success")) {
                    Toast.makeText(MainActivity.this, getString(R.string.register_success), Toast.LENGTH_SHORT).show();
                } else {
                    String message = response.getString("message");
                    Toast.makeText(MainActivity.this, getString(R.string.register_failed, message), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "處理註冊回應失敗: " + e.getMessage(), e);
                Toast.makeText(MainActivity.this, getString(R.string.register_failed_generic), Toast.LENGTH_SHORT).show();
            }
        }));

        socketManager.getSocket().on("loginResponse", args -> runOnUiThread(() -> {
            try {
                JSONObject response = (JSONObject) args[0];
                if (response.getBoolean("success")) {
                    String uid = response.getString("uid");
                    String username = response.getString("username");
                    String nickname = response.optString("nickname", username);
                    Log.d(TAG, "登入成功，UID: " + uid + ", Username: " + username + ", Nickname: " + nickname);

                    Intent intent = new Intent(MainActivity.this, ChatListActivity.class);
                    intent.putExtra("uid", uid);
                    intent.putExtra("username", username);
                    intent.putExtra("nickname", nickname);
                    startActivity(intent);
                    finish();
                } else {
                    String message = response.getString("message");
                    Toast.makeText(MainActivity.this, getString(R.string.login_failed, message), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "處理登入回應失敗: " + e.getMessage(), e);
                Toast.makeText(MainActivity.this, getString(R.string.login_failed_generic), Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void register() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String nickname = nicknameInput.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, getString(R.string.input_username_password), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("username", username);
            data.put("password", password);
            data.put("nickname", nickname.isEmpty() ? username : nickname);
            data.put("deviceInfo", new JSONObject()
                    .put("androidId", "unknown")
                    .put("model", "unknown")
                    .put("osVersion", "unknown"));
            socketManager.getSocket().emit("register", data);
        } catch (Exception e) {
            Log.e(TAG, "發送註冊請求失敗: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.register_failed_generic), Toast.LENGTH_SHORT).show();
        }
    }

    private void login() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, getString(R.string.input_username_password), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("username", username);
            data.put("password", password);
            data.put("deviceInfo", new JSONObject()
                    .put("androidId", "unknown")
                    .put("model", "unknown")
                    .put("osVersion", "unknown"));
            socketManager.getSocket().emit("login", data);
        } catch (Exception e) {
            Log.e(TAG, "發送登入請求失敗: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.login_failed_generic), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        socketManager.getSocket().off("registerResponse");
        socketManager.getSocket().off("loginResponse");
    }
}
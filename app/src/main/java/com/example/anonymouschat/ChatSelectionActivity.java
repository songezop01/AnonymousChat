package com.example.anonymouschat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import org.json.JSONObject;

public class ChatSelectionActivity extends AppCompatActivity {
    private static final String TAG = "ChatSelectionActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private static final int STORAGE_PERMISSION_REQUEST = 101;
    private SocketManager socketManager;
    private String uid;
    private EditText toUidEditText;
    private Button startChatButton;
    private Button scanQrButton;
    private Button importQrButton;
    private TextView uidTextView;
    private ImageView qrCodeImageView;
    private ActivityResultLauncher<ScanOptions> scanResultLauncher;
    private ActivityResultLauncher<Intent> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_selection);
        Log.d(TAG, "ChatSelectionActivity onCreate");

        uid = getIntent().getStringExtra("uid");
        if (uid == null) {
            Log.e(TAG, "UID 為 null，無法繼續");
            Toast.makeText(this, "無法獲取用戶 UID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Log.d(TAG, "UID: " + uid);

        uidTextView = findViewById(R.id.uidTextView);
        qrCodeImageView = findViewById(R.id.qrCodeImageView);
        toUidEditText = findViewById(R.id.toUidEditText);
        startChatButton = findViewById(R.id.startChatButton);
        scanQrButton = findViewById(R.id.scanQrButton);
        importQrButton = findViewById(R.id.importQrButton);

        uidTextView.setText("Your UID: " + uid);

        try {
            Bitmap qrCodeBitmap = generateQRCode(uid, 200, 200);
            qrCodeImageView.setImageBitmap(qrCodeBitmap);
        } catch (WriterException e) {
            e.printStackTrace();
            Log.e(TAG, "生成 QR 碼失敗: " + e.getMessage());
            Toast.makeText(this, "生成 QR 碼失敗: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        socketManager = SocketManager.getInstance(this);

        if (!socketManager.isConnected()) {
            Toast.makeText(this, "連線已斷開，嘗試重新連線...", Toast.LENGTH_LONG).show();
            socketManager.connect();
        }

        socketManager.getSocket().on("friendRequest", args -> runOnUiThread(() -> {
            try {
                JSONObject response = (JSONObject) args[0];
                String fromUid = response.getString("fromUid");
                String fromNickname = response.getString("fromNickname");
                Log.d(TAG, "收到好友請求，From UID: " + fromUid);

                new AlertDialog.Builder(ChatSelectionActivity.this)
                        .setTitle("好友請求")
                        .setMessage("來自 " + fromNickname + " (UID: " + fromUid + ") 的好友請求，是否接受？")
                        .setPositiveButton("接受", (dialog, which) -> {
                            try {
                                JSONObject acceptData = new JSONObject()
                                        .put("fromUid", fromUid)
                                        .put("toUid", uid);
                                socketManager.getSocket().emit("acceptFriendRequest", acceptData);
                                Log.d(TAG, "已接受好友請求，From UID: " + fromUid);
                            } catch (Exception e) {
                                e.printStackTrace();
                                Log.e(TAG, "接受好友請求失敗: " + e.getMessage());
                            }
                        })
                        .setNegativeButton("拒絕", (dialog, which) -> {
                            try {
                                socketManager.getSocket().emit("rejectFriendRequest", new JSONObject()
                                        .put("fromUid", fromUid)
                                        .put("toUid", uid));
                                Log.d(TAG, "已拒絕好友請求，From UID: " + fromUid);
                            } catch (Exception e) {
                                e.printStackTrace();
                                Log.e(TAG, "拒絕好友請求失敗: " + e.getMessage());
                            }
                        })
                        .setCancelable(false)
                        .show();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "處理好友請求失敗: " + e.getMessage());
            }
        }));

        socketManager.getSocket().on("friendRequestResponse", args -> runOnUiThread(() -> {
            try {
                JSONObject response = (JSONObject) args[0];
                if (response.getBoolean("success")) {
                    Log.d(TAG, "好友請求發送成功");
                    Toast.makeText(ChatSelectionActivity.this, "好友請求已發送", Toast.LENGTH_SHORT).show();
                } else {
                    String message = response.getString("message");
                    Log.d(TAG, "好友請求失敗: " + message);
                    Toast.makeText(ChatSelectionActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "處理好友請求回應失敗: " + e.getMessage());
            }
        }));

        socketManager.getSocket().on("friendRequestAccepted", args -> runOnUiThread(() -> {
            try {
                JSONObject response = (JSONObject) args[0];
                String fromUid = response.getString("fromUid");
                String fromNickname = response.getString("fromNickname");
                Log.d(TAG, "好友請求被接受，From UID: " + fromUid);
                Toast.makeText(ChatSelectionActivity.this, fromNickname + " 已接受你的好友請求", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "處理好友接受失敗: " + e.getMessage());
            }
        }));

        socketManager.getSocket().on("friendRequestRejected", args -> runOnUiThread(() -> {
            try {
                JSONObject response = (JSONObject) args[0];
                String fromUid = response.getString("fromUid");
                Log.d(TAG, "好友請求被拒絕，From UID: " + fromUid);
                Toast.makeText(ChatSelectionActivity.this, "好友請求被拒絕", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "處理好友拒絕失敗: " + e.getMessage());
            }
        }));

        startChatButton.setOnClickListener(v -> {
            String toUid = toUidEditText.getText().toString().trim();
            if (toUid.isEmpty()) {
                Toast.makeText(ChatSelectionActivity.this, "請輸入對方 UID", Toast.LENGTH_SHORT).show();
                return;
            }
            sendFriendRequest(toUid);
        });

        scanResultLauncher = registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() != null) {
                String scannedUid = result.getContents();
                Log.d(TAG, "掃描到的 UID: " + scannedUid);
                new AlertDialog.Builder(ChatSelectionActivity.this)
                        .setTitle("掃描結果")
                        .setMessage("掃描到的 UID: " + scannedUid + "\n是否發送好友請求？")
                        .setPositiveButton("是", (dialog, which) -> {
                            toUidEditText.setText(scannedUid);
                            sendFriendRequest(scannedUid);
                        })
                        .setNegativeButton("否", null)
                        .show();
            } else {
                Toast.makeText(this, "掃描失敗，請重試", Toast.LENGTH_SHORT).show();
            }
        });

        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri imageUri = result.getData().getData();
                try {
                    Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
                    String scannedUid = decodeQRCode(bitmap);
                    if (scannedUid != null) {
                        Log.d(TAG, "從相簿導入的 UID: " + scannedUid);
                        new AlertDialog.Builder(ChatSelectionActivity.this)
                                .setTitle("掃描結果")
                                .setMessage("掃描到的 UID: " + scannedUid + "\n是否發送好友請求？")
                                .setPositiveButton("是", (dialog, which) -> {
                                    toUidEditText.setText(scannedUid);
                                    sendFriendRequest(scannedUid);
                                })
                                .setNegativeButton("否", null)
                                .show();
                    } else {
                        Toast.makeText(this, "無法識別 QR 碼，請選擇有效的 QR 碼圖片", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "處理相簿圖片失敗: " + e.getMessage());
                    Toast.makeText(this, "處理圖片失敗: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        scanQrButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            } else {
                startQrScan();
            }
        });

        importQrButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
            } else {
                pickImageFromGallery();
            }
        });
    }

    private void sendFriendRequest(String toUid) {
        if (!socketManager.isConnected()) {
            Toast.makeText(ChatSelectionActivity.this, "連線未建立，請稍後再試", Toast.LENGTH_SHORT).show();
            socketManager.connect();
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("fromUid", uid);
            data.put("toUid", toUid);
            Log.d(TAG, "發送好友請求: " + data.toString());
            socketManager.getSocket().emit("friendRequest", data);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "發送好友請求失敗: " + e.getMessage());
        }
    }

    private void startQrScan() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("掃描對方的 UID QR 碼");
        options.setBeepEnabled(true);
        options.setBarcodeImageEnabled(true);
        scanResultLauncher.launch(options);
    }

    private void pickImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private String decodeQRCode(Bitmap bitmap) {
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = new MultiFormatReader().decode(binaryBitmap);
            return result.getText();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "解碼 QR 碼失敗: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startQrScan();
            } else {
                Toast.makeText(this, "需要相機權限才能掃描 QR 碼", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImageFromGallery();
            } else {
                Toast.makeText(this, "需要儲存權限才能從相簿選擇圖片", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Bitmap generateQRCode(String text, int width, int height) throws WriterException {
        BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return bitmap;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        socketManager.getSocket().off("friendRequest");
        socketManager.getSocket().off("friendRequestResponse");
        socketManager.getSocket().off("friendRequestAccepted");
        socketManager.getSocket().off("friendRequestRejected");
        Log.d(TAG, "ChatSelectionActivity onDestroy");
    }
}
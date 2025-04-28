package com.example.anonymouschat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ChatListActivity extends AppCompatActivity {
    private static final String TAG = "ChatListActivity";
    private static final String PREFS_NAME = "ChatAppPrefs";
    private static final String PREF_LANGUAGE = "language";
    private SocketManager socketManager;
    private String uid;
    private String username;
    private String nickname;
    private RecyclerView friendListRecyclerView;
    private RecyclerView chatListRecyclerView;
    private RecyclerView pendingFriendListRecyclerView;
    private FriendListAdapter friendListAdapter;
    private ChatListAdapter chatListAdapter;
    private PendingFriendListAdapter pendingFriendListAdapter;
    private List<FriendItem> friendList = new ArrayList<>();
    private List<ChatItem> chatList = new ArrayList<>();
    private List<PendingFriendItem> pendingFriendList = new ArrayList<>();
    private Button addFriendButton;
    private Button joinGroupButton;
    private Button createGroupChatButton;
    private EditText groupNameInput;
    private TextView userInfoTextView;
    private Button editNicknameButton;
    private ImageButton qrCodeButton;
    private Button languageSwitchButton;
    private ActivityResultLauncher<ScanOptions> scanResultLauncher;
    private String currentGroupId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedLanguage = prefs.getString(PREF_LANGUAGE, "zh");
        updateLocale(savedLanguage, false);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);
        Log.d(TAG, "ChatListActivity onCreate");

        try {
            socketManager = SocketManager.getInstance(this);
            uid = getIntent().getStringExtra("uid");
            username = getIntent().getStringExtra("username");
            nickname = getIntent().getStringExtra("nickname");

            if (uid == null || username == null) {
                Log.e(TAG, "UID or Username is null, finishing activity");
                Toast.makeText(this, getString(R.string.invalid_user_info), Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            Log.d(TAG, "UID: " + uid + ", Username: " + username + ", Nickname: " + nickname);

            userInfoTextView = findViewById(R.id.user_info_text_view);
            editNicknameButton = findViewById(R.id.edit_nickname_button);
            qrCodeButton = findViewById(R.id.qr_code_button);
            languageSwitchButton = findViewById(R.id.language_switch_button);
            userInfoTextView.setText(getString(R.string.user_info, username, nickname));

            friendListRecyclerView = findViewById(R.id.friend_list_recycler_view);
            friendListRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            friendListAdapter = new FriendListAdapter(friendList, friendUid -> startChatWithFriend(friendUid));
            friendListRecyclerView.setAdapter(friendListAdapter);

            chatListRecyclerView = findViewById(R.id.chat_list_recycler_view);
            chatListRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            chatListAdapter = new ChatListAdapter(chatList, uid, chat -> {
                Intent intent = new Intent(ChatListActivity.this, ChatActivity.class);
                intent.putExtra("chatId", chat.chatId);
                intent.putExtra("chatType", chat.type);
                intent.putExtra("uid", uid);
                startActivity(intent);
                Log.d(TAG, "Started ChatActivity with chatId: " + chat.chatId + ", chatType: " + chat.type + ", uid: " + uid);
            });
            chatListRecyclerView.setAdapter(chatListAdapter);

            pendingFriendListRecyclerView = findViewById(R.id.pending_friend_list_recycler_view);
            pendingFriendListRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            pendingFriendListAdapter = new PendingFriendListAdapter(pendingFriendList, (fromUid, accept) -> {
                if (accept) {
                    acceptFriendRequest(fromUid);
                } else {
                    rejectFriendRequest(fromUid);
                }
            });
            pendingFriendListRecyclerView.setAdapter(pendingFriendListAdapter);

            addFriendButton = findViewById(R.id.add_friend_button);
            joinGroupButton = findViewById(R.id.join_group_button);
            createGroupChatButton = findViewById(R.id.create_group_chat_button);
            groupNameInput = findViewById(R.id.group_name_input);

            scanResultLauncher = registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String scannedUid = result.getContents();
                    Log.d(TAG, "Scanned UID: " + scannedUid);
                    if (scannedUid != null && scannedUid.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                        sendFriendRequest(scannedUid);
                    } else {
                        Toast.makeText(this, getString(R.string.invalid_uid_format), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, getString(R.string.scan_failed), Toast.LENGTH_SHORT).show();
                }
            });

            addFriendButton.setOnClickListener(v -> showAddFriendDialog());
            joinGroupButton.setOnClickListener(v -> showJoinGroupDialog());
            createGroupChatButton.setOnClickListener(v -> {
                String groupName = groupNameInput.getText().toString().trim();
                if (!groupName.isEmpty()) {
                    showCreateGroupDialog(groupName);
                } else {
                    Toast.makeText(this, getString(R.string.enter_group_name), Toast.LENGTH_SHORT).show();
                }
            });

            editNicknameButton.setOnClickListener(v -> showEditNicknameDialog());
            qrCodeButton.setOnClickListener(v -> showQrCodeDialog());
            languageSwitchButton.setOnClickListener(v -> {
                Log.d(TAG, "Language switch button clicked");
                showLanguageSelectionDialog();
            });

            if (!socketManager.isConnected()) {
                Log.w(TAG, "Socket not connected, attempting to reconnect");
                socketManager.connect();
            }

            setupSocketListeners();
            loadFriendList();
            loadChatList();
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.init_failed), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void setupSocketListeners() {
        socketManager.getSocket().on("friendRequest", args -> runOnUiThread(() -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "friendRequest event received null or empty args");
                    return;
                }
                JSONObject response = (JSONObject) args[0];
                String fromUid = response.optString("fromUid", null);
                String fromNickname = response.optString("fromNickname", "Unknown User");
                if (fromUid == null) {
                    Log.e(TAG, "friendRequest event missing fromUid");
                    return;
                }
                Log.d(TAG, "Received friend request, From UID: " + fromUid + ", From Nickname: " + fromNickname);
                pendingFriendList.add(new PendingFriendItem(fromUid, fromNickname));
                pendingFriendListAdapter.notifyDataSetChanged();
                Toast.makeText(ChatListActivity.this, getString(R.string.received_friend_request, fromNickname), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "Failed to process friend request: " + e.getMessage(), e);
            }
        }));

        socketManager.getSocket().on("friendRequestResponse", args -> runOnUiThread(() -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "friendRequestResponse event received null or empty args");
                    return;
                }
                JSONObject response = (JSONObject) args[0];
                boolean success = response.optBoolean("success", false);
                String message = response.optString("message", getString(R.string.friend_request_failed_generic));
                if (success) {
                    Log.d(TAG, "Friend request sent successfully");
                    Toast.makeText(ChatListActivity.this, getString(R.string.friend_request_sent), Toast.LENGTH_SHORT).show();
                } else {
                    Log.d(TAG, "Friend request failed: " + message);
                    Toast.makeText(ChatListActivity.this, getString(R.string.friend_request_failed, message), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to process friend request response: " + e.getMessage(), e);
            }
        }));

        socketManager.getSocket().on("searchUsersResponse", args -> runOnUiThread(() -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "searchUsersResponse event received null or empty args");
                    return;
                }
                JSONObject response = (JSONObject) args[0];
                boolean success = response.optBoolean("success", false);
                if (success) {
                    JSONArray users = response.optJSONArray("users");
                    if (users == null) {
                        Log.e(TAG, "searchUsersResponse event missing users array");
                        return;
                    }
                    List<UserItem> userList = new ArrayList<>();
                    for (int i = 0; i < users.length(); i++) {
                        JSONObject user = users.getJSONObject(i);
                        String userUid = user.optString("uid", null);
                        String userNickname = user.optString("nickname", "Unknown");
                        if (userUid != null) {
                            userList.add(new UserItem(userUid, userNickname));
                        }
                    }
                    showUserSearchResults(userList);
                } else {
                    String message = response.optString("message", getString(R.string.search_failed_generic));
                    Toast.makeText(ChatListActivity.this, getString(R.string.search_failed, message), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to process search users response: " + e.getMessage(), e);
            }
        }));

        socketManager.getSocket().on("friendRequestAccepted", args -> runOnUiThread(() -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "friendRequestAccepted event received null or empty args");
                    return;
                }
                JSONObject response = (JSONObject) args[0];
                String fromUid = response.optString("fromUid", null);
                String fromNickname = response.optString("fromNickname", "Unknown");
                String chatId = response.optString("chatId", null);
                if (fromUid == null || chatId == null) {
                    Log.e(TAG, "friendRequestAccepted event missing fromUid or chatId");
                    return;
                }
                Log.d(TAG, "Friend request accepted, From UID: " + fromUid);
                if (friendList.stream().noneMatch(friend -> friend.uid.equals(fromUid))) {
                    friendList.add(new FriendItem(fromUid, fromNickname));
                    friendListAdapter.notifyDataSetChanged();
                }
                if (chatList.stream().noneMatch(chat -> chat.chatId.equals(chatId))) {
                    chatList.add(new ChatItem(chatId, "private", fromNickname, null));
                    chatListAdapter.notifyDataSetChanged();
                }
                pendingFriendList.removeIf(item -> item.uid.equals(fromUid));
                pendingFriendListAdapter.notifyDataSetChanged();
                Toast.makeText(ChatListActivity.this, getString(R.string.friend_request_accepted, fromNickname), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Failed to process friend acceptance: " + e.getMessage(), e);
                Toast.makeText(ChatListActivity.this, getString(R.string.friend_request_failed_generic), Toast.LENGTH_SHORT).show();
            }
        }));

        socketManager.getSocket().on("friendRequestRejected", args -> runOnUiThread(() -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "friendRequestRejected event received null or empty args");
                    return;
                }
                JSONObject response = (JSONObject) args[0];
                String fromUid = response.optString("fromUid", null);
                if (fromUid == null) {
                    Log.e(TAG, "friendRequestRejected event missing fromUid");
                    return;
                }
                Log.d(TAG, "Friend request rejected, From UID: " + fromUid);
                pendingFriendList.removeIf(item -> item.uid.equals(fromUid));
                pendingFriendListAdapter.notifyDataSetChanged();
                Toast.makeText(ChatListActivity.this, getString(R.string.friend_request_rejected), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Failed to process friend rejection: " + e.getMessage(), e);
            }
        }));

        socketManager.getSocket().on("getFriendListResponse", args -> runOnUiThread(() -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "getFriendListResponse event received null or empty args");
                    return;
                }
                JSONObject response = (JSONObject) args[0];
                boolean success = response.optBoolean("success", false);
                if (success) {
                    JSONArray friendsJson = response.optJSONArray("friends");
                    if (friendsJson == null) {
                        Log.e(TAG, "getFriendListResponse event missing friends array");
                        return;
                    }
                    Set<String> friendUids = new HashSet<>();
                    List<FriendItem> newFriendList = new ArrayList<>();
                    for (int i = 0; i < friendsJson.length(); i++) {
                        JSONObject friend = friendsJson.getJSONObject(i);
                        String friendUid = friend.optString("uid", null);
                        String friendNickname = friend.optString("nickname", "Unknown");
                        if (friendUid == null) {
                            Log.w(TAG, "Friend missing uid at index: " + i);
                            continue;
                        }
                        if (friendUids.contains(friendUid)) {
                            Log.w(TAG, "Duplicate friendUid found: " + friendUid);
                            continue;
                        }
                        friendUids.add(friendUid);
                        newFriendList.add(new FriendItem(friendUid, friendNickname));
                    }
                    friendList.clear();
                    friendList.addAll(newFriendList);
                    friendListAdapter.notifyDataSetChanged();
                    Log.d(TAG, "Loaded friend list, count: " + friendList.size());
                } else {
                    String message = response.optString("message", getString(R.string.load_friend_list_failed));
                    Log.e(TAG, "Failed to load friend list: " + message);
                    Toast.makeText(this, getString(R.string.load_friend_list_failed), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to process getFriendList response: " + e.getMessage(), e);
                Toast.makeText(this, getString(R.string.load_friend_list_failed), Toast.LENGTH_SHORT).show();
            }
        }));

        socketManager.getSocket().on("getChatListResponse", args -> runOnUiThread(() -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "getChatListResponse event received null or empty args");
                    return;
                }
                JSONObject response = (JSONObject) args[0];
                boolean success = response.optBoolean("success", false);
                if (success) {
                    JSONArray chatListJson = response.optJSONArray("chatList");
                    if (chatListJson == null) {
                        Log.e(TAG, "getChatListResponse event missing chatList array");
                        return;
                    }
                    Set<String> chatIds = new HashSet<>();
                    List<ChatItem> newChatList = new ArrayList<>();
                    for (int i = 0; i < chatListJson.length(); i++) {
                        try {
                            JSONObject chat = chatListJson.getJSONObject(i);
                            String chatId = chat.optString("chatId", null);
                            if (chatId == null) {
                                Log.w(TAG, "Chat missing chatId at index: " + i);
                                continue;
                            }
                            if (chatIds.contains(chatId)) {
                                Log.w(TAG, "Duplicate chatId found: " + chatId);
                                continue;
                            }
                            chatIds.add(chatId);
                            String type = chat.optString("type", "private");
                            String name = chat.optString("name", "Unknown");
                            JSONObject lastMessage = chat.has("lastMessage") && !chat.isNull("lastMessage") ? chat.getJSONObject("lastMessage") : null;
                            String lastMessageText = lastMessage != null ? lastMessage.optString("message", "") : null;
                            newChatList.add(new ChatItem(chatId, type, name, lastMessageText));
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to parse chat item at index " + i + ": " + e.getMessage(), e);
                        }
                    }
                    chatList.clear();
                    chatList.addAll(newChatList);
                    chatListAdapter.notifyDataSetChanged();
                    Log.d(TAG, "Loaded chat list, count: " + chatList.size());
                } else {
                    String message = response.optString("message", getString(R.string.load_chat_list_failed));
                    Log.e(TAG, "Failed to load chat list: " + message);
                    Toast.makeText(this, getString(R.string.load_chat_list_failed), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to process getChatList response: " + e.getMessage(), e);
                Toast.makeText(this, getString(R.string.load_chat_list_failed), Toast.LENGTH_SHORT).show();
            }
        }));

        socketManager.getSocket().on("createGroupChatResponse", args -> runOnUiThread(() -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "createGroupChatResponse event received null or empty args");
                    return;
                }
                JSONObject response = (JSONObject) args[0];
                boolean success = response.optBoolean("success", false);
                if (success) {
                    Log.d(TAG, "Group chat created successfully");
                    currentGroupId = response.optString("groupId", null);
                    if (currentGroupId == null) {
                        Log.e(TAG, "createGroupChatResponse event missing groupId");
                        return;
                    }
                    Toast.makeText(this, getString(R.string.group_chat_created), Toast.LENGTH_SHORT).show();
                    loadChatList();
                } else {
                    String errorMsg = response.optString("message", getString(R.string.create_group_failed));
                    Log.e(TAG, "Failed to create group chat: " + errorMsg);
                    Toast.makeText(this, getString(R.string.create_group_failed, errorMsg), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to process createGroupChat response: " + e.getMessage(), e);
                Toast.makeText(this, getString(R.string.create_group_failed_generic), Toast.LENGTH_SHORT).show();
            }
        }));

        socketManager.getSocket().on("groupChatCreated", args -> runOnUiThread(() -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "groupChatCreated event received null or empty args");
                    return;
                }
                JSONObject data = (JSONObject) args[0];
                String chatId = data.optString("chatId", null);
                String name = data.optString("name", "Unknown Group");
                if (chatId == null) {
                    Log.e(TAG, "groupChatCreated event missing chatId");
                    return;
                }
                ChatItem chatItem = new ChatItem(chatId, "group", name, null);
                chatList.add(chatItem);
                chatListAdapter.notifyDataSetChanged();
                Log.d(TAG, "Group chat created, added to chat list: " + name);
            } catch (Exception e) {
                Log.e(TAG, "Failed to process groupChatCreated event: " + e.getMessage(), e);
            }
        }));

        socketManager.getSocket().on("searchGroupsResponse", args -> runOnUiThread(() -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "searchGroupsResponse event received null or empty args");
                    return;
                }
                JSONObject response = (JSONObject) args[0];
                boolean success = response.optBoolean("success", false);
                if (success) {
                    JSONArray groups = response.optJSONArray("groups");
                    if (groups == null) {
                        Log.e(TAG, "searchGroupsResponse event missing groups array");
                        return;
                    }
                    List<GroupItem> groupList = new ArrayList<>();
                    for (int i = 0; i < groups.length(); i++) {
                        JSONObject group = groups.getJSONObject(i);
                        String chatId = group.optString("chatId", null);
                        String groupId = group.optString("groupId", null);
                        String name = group.optString("name", "Unknown Group");
                        if (chatId == null || groupId == null) {
                            Log.w(TAG, "Group missing chatId or groupId at index: " + i);
                            continue;
                        }
                        groupList.add(new GroupItem(chatId, groupId, name));
                    }
                    showGroupSearchResults(groupList);
                } else {
                    String message = response.optString("message", getString(R.string.search_groups_failed_generic));
                    Toast.makeText(ChatListActivity.this, getString(R.string.search_groups_failed, message), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to process search groups response: " + e.getMessage(), e);
            }
        }));

        socketManager.getSocket().on("joinGroupRequest", args -> runOnUiThread(() -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "joinGroupRequest event received null or empty args");
                    return;
                }
                JSONObject data = (JSONObject) args[0];
                String groupId = data.optString("groupId", null);
                String fromUid = data.optString("fromUid", null);
                String fromNickname = data.optString("fromNickname", "Unknown");
                if (groupId == null || fromUid == null) {
                    Log.e(TAG, "joinGroupRequest event missing groupId or fromUid");
                    return;
                }
                Log.d(TAG, "Received join group request, From UID: " + fromUid + ", Group ID: " + groupId);

                new AlertDialog.Builder(ChatListActivity.this)
                        .setTitle(getString(R.string.join_group_request))
                        .setMessage(getString(R.string.join_group_request_message, fromNickname, fromUid))
                        .setPositiveButton(R.string.approve, (dialog, which) -> {
                            try {
                                JSONObject approveData = new JSONObject()
                                        .put("groupId", groupId)
                                        .put("fromUid", fromUid)
                                        .put("toUid", uid);
                                socketManager.getSocket().emit("approveJoinGroup", approveData);
                                Log.d(TAG, "Approved join group request, From UID: " + fromUid);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to approve join group: " + e.getMessage(), e);
                            }
                        })
                        .setNegativeButton(R.string.reject, (dialog, which) -> {
                            try {
                                JSONObject rejectData = new JSONObject()
                                        .put("groupId", groupId)
                                        .put("fromUid", fromUid)
                                        .put("toUid", uid);
                                socketManager.getSocket().emit("rejectJoinGroup", rejectData);
                                Log.d(TAG, "Rejected join group request, From UID: " + fromUid);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to reject join group: " + e.getMessage(), e);
                            }
                        })
                        .setCancelable(false)
                        .show();
            } catch (Exception e) {
                Log.e(TAG, "Failed to process join group request: " + e.getMessage(), e);
            }
        }));

        socketManager.getSocket().on("joinGroupResponse", args -> runOnUiThread(() -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "joinGroupResponse event received null or empty args");
                    return;
                }
                JSONObject response = (JSONObject) args[0];
                boolean success = response.optBoolean("success", false);
                if (success) {
                    Log.d(TAG, "Join group request sent successfully");
                    Toast.makeText(ChatListActivity.this, getString(R.string.join_group_request_sent), Toast.LENGTH_SHORT).show();
                } else {
                    String message = response.optString("message", getString(R.string.join_group_failed_generic));
                    Log.d(TAG, "Join group request failed: " + message);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to process join group response: " + e.getMessage(), e);
                Toast.makeText(ChatListActivity.this, getString(R.string.join_group_failed_generic), Toast.LENGTH_SHORT).show();
            }
        }));

        socketManager.getSocket().on("joinGroupApproved", args -> runOnUiThread(() -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "joinGroupApproved event received null or empty args");
                    return;
                }
                JSONObject data = (JSONObject) args[0];
                String chatId = data.optString("chatId", null);
                String name = data.optString("name", "Unknown Group");
                if (chatId == null) {
                    Log.e(TAG, "joinGroupApproved event missing chatId");
                    return;
                }
                Log.d(TAG, "Join group approved, Chat ID: " + chatId);
                chatList.add(new ChatItem(chatId, "group", name, null));
                chatListAdapter.notifyDataSetChanged();
                Toast.makeText(ChatListActivity.this, getString(R.string.joined_group, name), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Failed to process join group approval: " + e.getMessage(), e);
                Toast.makeText(ChatListActivity.this, getString(R.string.join_group_failed_generic), Toast.LENGTH_SHORT).show();
            }
        }));

        socketManager.getSocket().on("inviteToGroup", args -> runOnUiThread(() -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "inviteToGroup event received null or empty args");
                    return;
                }
                JSONObject data = (JSONObject) args[0];
                String groupId = data.optString("groupId", null);
                String groupName = data.optString("groupName", "Unknown Group");
                String fromUid = data.optString("fromUid", null);
                String fromNickname = data.optString("fromNickname", "Unknown");
                if (groupId == null || fromUid == null) {
                    Log.e(TAG, "inviteToGroup event missing groupId or fromUid");
                    return;
                }
                Log.d(TAG, "Received group invite, From UID: " + fromUid + ", Group ID: " + groupId);

                new AlertDialog.Builder(ChatListActivity.this)
                        .setTitle(getString(R.string.group_invite))
                        .setMessage(getString(R.string.group_invite_message, fromNickname, groupName))
                        .setPositiveButton(R.string.accept, (dialog, which) -> {
                            try {
                                JSONObject acceptData = new JSONObject()
                                        .put("groupId", groupId)
                                        .put("fromUid", fromUid)
                                        .put("toUid", uid);
                                socketManager.getSocket().emit("acceptGroupInvite", acceptData);
                                Log.d(TAG, "Accepted group invite, Group ID: " + groupId);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to accept group invite: " + e.getMessage(), e);
                            }
                        })
                        .setNegativeButton(R.string.reject, (dialog, which) -> {
                            try {
                                JSONObject rejectData = new JSONObject()
                                        .put("groupId", groupId)
                                        .put("fromUid", fromUid)
                                        .put("toUid", uid);
                                socketManager.getSocket().emit("rejectGroupInvite", rejectData);
                                Log.d(TAG, "Rejected group invite, Group ID: " + groupId);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to reject group invite: " + e.getMessage(), e);
                            }
                        })
                        .setCancelable(false)
                        .show();
            } catch (Exception e) {
                Log.e(TAG, "Failed to process group invite: " + e.getMessage(), e);
            }
        }));

        socketManager.getSocket().on("updateNicknameResponse", args -> runOnUiThread(() -> {
            try {
                if (args == null || args.length == 0) {
                    Log.e(TAG, "updateNicknameResponse event received null or empty args");
                    return;
                }
                JSONObject response = (JSONObject) args[0];
                boolean success = response.optBoolean("success", false);
                if (success) {
                    nickname = response.optString("nickname", nickname);
                    userInfoTextView.setText(getString(R.string.user_info, username, nickname));
                    Toast.makeText(ChatListActivity.this, getString(R.string.nickname_updated), Toast.LENGTH_SHORT).show();
                } else {
                    String message = response.optString("message", getString(R.string.nickname_update_failed_generic));
                    Toast.makeText(ChatListActivity.this, getString(R.string.nickname_update_failed, message), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to process nickname update response: " + e.getMessage(), e);
            }
        }));
    }

    private void showAddFriendDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.add_friend_title);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_friend, null);
        EditText uidInput = dialogView.findViewById(R.id.uid_input);
        EditText nicknameInput = dialogView.findViewById(R.id.nickname_input);
        Button scanQrButton = dialogView.findViewById(R.id.scan_qr_button);
        Button searchButton = dialogView.findViewById(R.id.search_button);

        builder.setView(dialogView);
        builder.setPositiveButton(R.string.send_request, (dialog, which) -> {
            String uidText = uidInput.getText().toString().trim();
            String nicknameText = nicknameInput.getText().toString().trim();
            if (!uidText.isEmpty()) {
                sendFriendRequest(uidText);
            } else if (!nicknameText.isEmpty()) {
                sendFriendRequestByNickname(nicknameText);
            } else {
                Toast.makeText(ChatListActivity.this, getString(R.string.enter_uid_or_nickname), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);

        AlertDialog dialog = builder.create();
        scanQrButton.setOnClickListener(v -> {
            dialog.dismiss();
            startQrScan();
        });
        searchButton.setOnClickListener(v -> {
            String query = nicknameInput.getText().toString().trim();
            if (!query.isEmpty()) {
                searchUsers(query);
            } else {
                Toast.makeText(ChatListActivity.this, getString(R.string.enter_nickname_to_search), Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show();
    }

    private void showJoinGroupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.join_group_title);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_join_group, null);
        EditText groupIdInput = dialogView.findViewById(R.id.group_id_input);
        EditText passwordInput = dialogView.findViewById(R.id.password_input);
        Button searchGroupsButton = dialogView.findViewById(R.id.search_groups_button);

        builder.setView(dialogView);
        builder.setPositiveButton(R.string.send_request, (dialog, which) -> {
            String groupId = groupIdInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            if (!groupId.isEmpty() && !password.isEmpty()) {
                sendJoinGroupRequest(groupId, password);
            } else {
                Toast.makeText(ChatListActivity.this, getString(R.string.enter_group_id_password), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = builder.create();
        searchGroupsButton.setOnClickListener(v -> {
            String query = groupIdInput.getText().toString().trim();
            if (!query.isEmpty()) {
                searchGroups(query);
            } else {
                Toast.makeText(ChatListActivity.this, getString(R.string.enter_group_query), Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show();
    }

    private void showCreateGroupDialog(String groupName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.create_group);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_group, null);
        EditText passwordInput = dialogView.findViewById(R.id.password_input);
        Button inviteFriendsButton = dialogView.findViewById(R.id.invite_friends_button);

        builder.setView(dialogView);
        builder.setPositiveButton(R.string.create, (dialog, which) -> {
            String password = passwordInput.getText().toString().trim();
            if (!password.isEmpty()) {
                createGroupChat(groupName, password);
            } else {
                Toast.makeText(ChatListActivity.this, getString(R.string.enter_password), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);

        AlertDialog dialog = builder.create();
        inviteFriendsButton.setOnClickListener(v -> {
            showInviteFriendsDialog();
        });
        dialog.show();
    }

    private void showInviteFriendsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.invite_friends);

        List<String> selectedFriends = new ArrayList<>();
        CharSequence[] friendNames = friendList.stream()
                .map(f -> f.nickname)
                .toArray(CharSequence[]::new);
        boolean[] checkedItems = new boolean[friendList.size()];

        builder.setMultiChoiceItems(friendNames, checkedItems, (dialog, which, isChecked) -> {
            String friendUid = friendList.get(which).uid;
            if (isChecked) {
                selectedFriends.add(friendUid);
            } else {
                selectedFriends.remove(friendUid);
            }
        });
        builder.setPositiveButton(R.string.invite, (dialog, which) -> {
            if (!selectedFriends.isEmpty()) {
                inviteFriendsToGroup(selectedFriends);
            } else {
                Toast.makeText(ChatListActivity.this, getString(R.string.select_friend), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showEditNicknameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.edit_nickname);

        EditText nicknameInput = new EditText(this);
        nicknameInput.setText(nickname);
        builder.setView(nicknameInput);

        builder.setPositiveButton(R.string.save, (dialog, which) -> {
            String newNickname = nicknameInput.getText().toString().trim();
            if (!newNickname.isEmpty()) {
                updateNickname(newNickname);
            } else {
                Toast.makeText(ChatListActivity.this, getString(R.string.nickname_empty), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showQrCodeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.qr_code_title);

        ImageView qrCodeImage = new ImageView(this);
        try {
            Bitmap qrCodeBitmap = generateQrCode(uid, 400, 400);
            qrCodeImage.setImageBitmap(qrCodeBitmap);
            qrCodeImage.setAdjustViewBounds(true);
            qrCodeImage.setMaxWidth(400);
            qrCodeImage.setMaxHeight(400);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate QR code: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.qr_code_failed), Toast.LENGTH_SHORT).show();
            return;
        }

        builder.setView(qrCodeImage);
        builder.setPositiveButton(R.string.ok, null);
        builder.show();
    }

    private Bitmap generateQrCode(String text, int width, int height) throws Exception {
        MultiFormatWriter writer = new MultiFormatWriter();
        BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return bitmap;
    }

    private void showUserSearchResults(List<UserItem> userList) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.search_results);

        String[] userNames = userList.stream()
                .map(user -> user.nickname + " (UID: " + user.uid + ")")
                .toArray(String[]::new);

        builder.setItems(userNames, (dialog, which) -> {
            UserItem selectedUser = userList.get(which);
            sendFriendRequest(selectedUser.uid);
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showGroupSearchResults(List<GroupItem> groupList) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.search_groups_results);

        String[] groupNames = groupList.stream()
                .map(group -> group.name + " (ID: " + group.groupId + ")")
                .toArray(String[]::new);

        builder.setItems(groupNames, (dialog, which) -> {
            GroupItem selectedGroup = groupList.get(which);
            showJoinGroupWithIdDialog(selectedGroup.groupId);
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showJoinGroupWithIdDialog(String groupId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.join_group_title);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_join_group, null);
        EditText groupIdInput = dialogView.findViewById(R.id.group_id_input);
        EditText passwordInput = dialogView.findViewById(R.id.password_input);
        Button searchGroupsButton = dialogView.findViewById(R.id.search_groups_button);
        groupIdInput.setText(groupId);
        groupIdInput.setEnabled(false);

        builder.setView(dialogView);
        builder.setPositiveButton(R.string.send_request, (dialog, which) -> {
            String password = passwordInput.getText().toString().trim();
            if (!password.isEmpty()) {
                sendJoinGroupRequest(groupId, password);
            } else {
                Toast.makeText(ChatListActivity.this, getString(R.string.enter_password), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = builder.create();
        searchGroupsButton.setVisibility(View.GONE);
        dialog.show();
    }

    private void startQrScan() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt(getString(R.string.scan_prompt));
        options.setBeepEnabled(true);
        options.setBarcodeImageEnabled(true);
        scanResultLauncher.launch(options);
    }

    private void sendFriendRequest(String toUid) {
        if (!socketManager.isConnected()) {
            Toast.makeText(ChatListActivity.this, getString(R.string.connection_lost), Toast.LENGTH_SHORT).show();
            socketManager.connect();
            new Handler(Looper.getMainLooper()).postDelayed(() -> sendFriendRequestWithRetry(toUid, 3), 1000);
            return;
        }

        sendFriendRequestWithRetry(toUid, 3);
    }

    private void sendFriendRequestWithRetry(String toUid, int retryCount) {
        if (retryCount <= 0) {
            runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(ChatListActivity.this);
                builder.setTitle("發送好友請求失敗");
                builder.setMessage("無法發送好友請求，是否重試？");
                builder.setPositiveButton("重試", (dialog, which) -> sendFriendRequestWithRetry(toUid, 3));
                builder.setNegativeButton("取消", null);
                builder.show();
            });
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("fromUid", uid);
            data.put("toUid", toUid);
            Log.d(TAG, "Sending friend request (Attempts left: " + retryCount + "): " + data.toString());

            final boolean[] responseReceived = {false};
            socketManager.getSocket().once("friendRequestResponse", args -> runOnUiThread(() -> {
                responseReceived[0] = true;
                try {
                    JSONObject response = (JSONObject) args[0];
                    if (response.getBoolean("success")) {
                        Log.d(TAG, "Friend request sent successfully");
                        Toast.makeText(ChatListActivity.this, getString(R.string.friend_request_sent), Toast.LENGTH_SHORT).show();
                    } else {
                        String message = response.getString("message");
                        Log.d(TAG, "Friend request failed: " + message);
                        Toast.makeText(ChatListActivity.this, getString(R.string.friend_request_failed, message), Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to process friend request response: " + e.getMessage(), e);
                    Toast.makeText(ChatListActivity.this, getString(R.string.friend_request_failed_generic), Toast.LENGTH_SHORT).show();
                }
            }));

            socketManager.getSocket().emit("friendRequest", data);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!responseReceived[0]) {
                    Log.w(TAG, "Friend request timeout, retrying... (Attempts left: " + (retryCount - 1) + ")");
                    sendFriendRequestWithRetry(toUid, retryCount - 1);
                }
            }, 5000);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send friend request: " + e.getMessage(), e);
            runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(ChatListActivity.this);
                builder.setTitle("發送好友請求失敗");
                builder.setMessage("無法發送好友請求，是否重試？\n錯誤：" + e.getMessage());
                builder.setPositiveButton("重試", (dialog, which) -> sendFriendRequestWithRetry(toUid, 3));
                builder.setNegativeButton("取消", null);
                builder.show();
            });
        }
    }

    private void sendFriendRequestByNickname(String nickname) {
        if (!socketManager.isConnected()) {
            Toast.makeText(ChatListActivity.this, getString(R.string.connection_lost), Toast.LENGTH_SHORT).show();
            socketManager.connect();
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("fromUid", uid);
            data.put("nickname", nickname);
            Log.d(TAG, "Sending friend request by nickname: " + data.toString());
            socketManager.getSocket().emit("friendRequestByNickname", data);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send friend request by nickname: " + e.getMessage(), e);
            Toast.makeText(ChatListActivity.this, getString(R.string.friend_request_failed_generic), Toast.LENGTH_SHORT).show();
        }
    }

    private void searchUsers(String query) {
        if (!socketManager.isConnected()) {
            Toast.makeText(ChatListActivity.this, getString(R.string.connection_lost), Toast.LENGTH_SHORT).show();
            socketManager.connect();
            return;
        }

        searchUsersWithRetry(query, 3);
    }

    private void searchUsersWithRetry(String query, int retryCount) {
        if (retryCount <= 0) {
            runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(ChatListActivity.this);
                builder.setTitle("搜尋用戶失敗");
                builder.setMessage("無法搜尋用戶，是否重試？");
                builder.setPositiveButton("重試", (dialog, which) -> searchUsersWithRetry(query, 3));
                builder.setNegativeButton("取消", null);
                builder.show();
            });
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("query", query);
            Log.d(TAG, "Searching users (Attempts left: " + retryCount + "): " + data.toString());

            final boolean[] responseReceived = {false};
            socketManager.getSocket().once("searchUsersResponse", args -> runOnUiThread(() -> {
                responseReceived[0] = true;
                try {
                    JSONObject response = (JSONObject) args[0];
                    if (response.getBoolean("success")) {
                        JSONArray users = response.getJSONArray("users");
                        List<UserItem> userList = new ArrayList<>();
                        for (int i = 0; i < users.length(); i++) {
                            JSONObject user = users.getJSONObject(i);
                            userList.add(new UserItem(user.getString("uid"), user.getString("nickname")));
                        }
                        showUserSearchResults(userList);
                    } else {
                        String message = response.getString("message");
                        Toast.makeText(ChatListActivity.this, getString(R.string.search_failed, message), Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to process search users response: " + e.getMessage(), e);
                }
            }));

            socketManager.getSocket().emit("searchUsers", data);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!responseReceived[0]) {
                    Log.w(TAG, "Search users timeout, retrying... (Attempts left: " + (retryCount - 1) + ")");
                    searchUsersWithRetry(query, retryCount - 1);
                }
            }, 5000);
        } catch (Exception e) {
            Log.e(TAG, "Failed to search users: " + e.getMessage(), e);
            runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(ChatListActivity.this);
                builder.setTitle("搜尋用戶失敗");
                builder.setMessage("無法搜尋用戶，是否重試？\n錯誤：" + e.getMessage());
                builder.setPositiveButton("重試", (dialog, which) -> searchUsersWithRetry(query, 3));
                builder.setNegativeButton("取消", null);
                builder.show();
            });
        }
    }

    private void acceptFriendRequest(String fromUid) {
        try {
            JSONObject data = new JSONObject()
                    .put("fromUid", fromUid)
                    .put("toUid", uid);
            socketManager.getSocket().emit("acceptFriendRequest", data);
            Log.d(TAG, "Accepted friend request, From UID: " + fromUid);
        } catch (Exception e) {
            Log.e(TAG, "Failed to accept friend request: " + e.getMessage(), e);
            Toast.makeText(ChatListActivity.this, getString(R.string.friend_request_failed_generic), Toast.LENGTH_SHORT).show();
        }
    }

    private void rejectFriendRequest(String fromUid) {
        try {
            JSONObject data = new JSONObject()
                    .put("fromUid", fromUid)
                    .put("toUid", uid);
            socketManager.getSocket().emit("rejectFriendRequest", data);
            Log.d(TAG, "Rejected friend request, From UID: " + fromUid);
        } catch (Exception e) {
            Log.e(TAG, "Failed to reject friend request: " + e.getMessage(), e);
            Toast.makeText(ChatListActivity.this, getString(R.string.friend_request_failed_generic), Toast.LENGTH_SHORT).show();
        }
    }

    private void sendJoinGroupRequest(String groupId, String password) {
        if (!socketManager.isConnected()) {
            Toast.makeText(ChatListActivity.this, getString(R.string.connection_lost), Toast.LENGTH_SHORT).show();
            socketManager.connect();
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("groupId", groupId);
            data.put("password", password);
            data.put("fromUid", uid);
            Log.d(TAG, "Sending join group request: " + data.toString());
            socketManager.getSocket().emit("joinGroupRequest", data);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!chatList.stream().anyMatch(chat -> chat.chatId.contains(groupId))) {
                    Log.w(TAG, "Join group request timeout, retrying...");
                    socketManager.getSocket().emit("joinGroupRequest", data);
                }
            }, 5000);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send join group request: " + e.getMessage(), e);
            Toast.makeText(ChatListActivity.this, getString(R.string.join_group_failed_generic), Toast.LENGTH_SHORT).show();
        }
    }

    private void searchGroups(String query) {
        if (!socketManager.isConnected()) {
            Toast.makeText(ChatListActivity.this, getString(R.string.connection_lost), Toast.LENGTH_SHORT).show();
            socketManager.connect();
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("query", query);
            Log.d(TAG, "Searching groups: " + data.toString());
            socketManager.getSocket().emit("searchGroups", data);
        } catch (Exception e) {
            Log.e(TAG, "Failed to search groups: " + e.getMessage(), e);
            Toast.makeText(ChatListActivity.this, getString(R.string.search_groups_failed_generic), Toast.LENGTH_SHORT).show();
        }
    }

    private void createGroupChat(String groupName, String password) {
        if (!socketManager.isConnected()) {
            Toast.makeText(this, getString(R.string.connection_lost), Toast.LENGTH_SHORT).show();
            socketManager.connect();
            return;
        }

        try {
            JSONObject groupChatData = new JSONObject();
            groupChatData.put("groupName", groupName);
            groupChatData.put("password", password);
            groupChatData.put("memberUids", new JSONArray().put(uid));
            socketManager.getSocket().emit("createGroupChat", groupChatData);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send createGroupChat request: " + e.getMessage(), e);
            Toast.makeText(ChatListActivity.this, getString(R.string.create_group_failed_generic), Toast.LENGTH_SHORT).show();
        }
    }

    private void inviteFriendsToGroup(List<String> friendUids) {
        if (!socketManager.isConnected()) {
            Toast.makeText(this, getString(R.string.connection_lost), Toast.LENGTH_SHORT).show();
            socketManager.connect();
            return;
        }

        try {
            JSONObject inviteData = new JSONObject();
            inviteData.put("fromUid", uid);
            inviteData.put("friendUids", new JSONArray(friendUids));
            inviteData.put("groupId", currentGroupId);
            socketManager.getSocket().emit("inviteToGroup", inviteData);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send invite friends request: " + e.getMessage(), e);
            Toast.makeText(ChatListActivity.this, getString(R.string.invite_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateNickname(String newNickname) {
        if (!socketManager.isConnected()) {
            Toast.makeText(this, getString(R.string.connection_lost), Toast.LENGTH_SHORT).show();
            socketManager.connect();
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("uid", uid);
            data.put("nickname", newNickname);
            socketManager.getSocket().emit("updateNickname", data);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send update nickname request: " + e.getMessage(), e);
            Toast.makeText(ChatListActivity.this, getString(R.string.nickname_update_failed_generic), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadFriendList() {
        if (!socketManager.isConnected()) {
            Log.w(TAG, "Connection not established, attempting to reconnect");
            Toast.makeText(this, getString(R.string.connection_lost), Toast.LENGTH_LONG).show();
            socketManager.connect();
            return;
        }

        try {
            JSONObject requestData = new JSONObject();
            requestData.put("uid", uid);
            socketManager.getSocket().emit("getFriendList", requestData);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send getFriendList request: " + e.getMessage(), e);
            Toast.makeText(ChatListActivity.this, getString(R.string.load_friend_list_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadChatList() {
        if (!socketManager.isConnected()) {
            Log.w(TAG, "Connection not established, attempting to reconnect");
            Toast.makeText(this, getString(R.string.connection_lost), Toast.LENGTH_LONG).show();
            socketManager.connect();
            return;
        }

        try {
            JSONObject requestData = new JSONObject();
            requestData.put("uid", uid);
            socketManager.getSocket().emit("getChatList", requestData);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send getChatList request: " + e.getMessage(), e);
            Toast.makeText(ChatListActivity.this, getString(R.string.load_chat_list_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void startChatWithFriend(String friendUid) {
        if (!socketManager.isConnected()) {
            Log.w(TAG, "Connection not established, attempting to reconnect");
            Toast.makeText(this, getString(R.string.connection_lost), Toast.LENGTH_LONG).show();
            socketManager.connect();
            return;
        }

        try {
            JSONObject chatData = new JSONObject();
            chatData.put("fromUid", uid);
            chatData.put("toUid", friendUid);
            socketManager.getSocket().emit("startFriendChat", chatData);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send startFriendChat request: " + e.getMessage(), e);
            Toast.makeText(ChatListActivity.this, getString(R.string.start_chat_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void showLanguageSelectionDialog() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.language_switch);

            final String[] languages = {getString(R.string.language_zh), getString(R.string.language_en)};
            builder.setItems(languages, (dialog, which) -> {
                String selectedLanguage = which == 0 ? "zh" : "en";
                Log.d(TAG, "Selected language: " + selectedLanguage);

                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(PREF_LANGUAGE, selectedLanguage);
                editor.apply();

                updateLocale(selectedLanguage, true);
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to show language selection dialog: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.language_selection_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateLocale(String languageCode, boolean recreateActivity) {
        Locale newLocale;
        if (languageCode.equals("zh")) {
            newLocale = new Locale("zh", "TW");
        } else {
            newLocale = Locale.ENGLISH;
        }

        Locale.setDefault(newLocale);
        Configuration config = new Configuration();
        config.setLocale(newLocale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());

        if (recreateActivity) {
            Log.d(TAG, "Recreating activity to apply language change");
            Intent intent = getIntent();
            finish();
            startActivity(intent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (socketManager != null && socketManager.getSocket() != null) {
            socketManager.getSocket().off("friendRequest");
            socketManager.getSocket().off("friendRequestResponse");
            socketManager.getSocket().off("searchUsersResponse");
            socketManager.getSocket().off("friendRequestAccepted");
            socketManager.getSocket().off("friendRequestRejected");
            socketManager.getSocket().off("getFriendListResponse");
            socketManager.getSocket().off("getChatListResponse");
            socketManager.getSocket().off("createGroupChatResponse");
            socketManager.getSocket().off("groupChatCreated");
            socketManager.getSocket().off("searchGroupsResponse");
            socketManager.getSocket().off("joinGroupRequest");
            socketManager.getSocket().off("joinGroupResponse");
            socketManager.getSocket().off("joinGroupApproved");
            socketManager.getSocket().off("joinGroupRejected");
            socketManager.getSocket().off("inviteToGroup");
            socketManager.getSocket().off("updateNicknameResponse");
        }
        Log.d(TAG, "ChatListActivity onDestroy");
    }
}

class FriendItem {
    String uid;
    String nickname;

    FriendItem(String uid, String nickname) {
        this.uid = uid;
        this.nickname = nickname != null ? nickname : "Unknown";
    }
}

class FriendListAdapter extends RecyclerView.Adapter<FriendListAdapter.FriendViewHolder> {
    private List<FriendItem> friendList;
    private OnFriendClickListener listener;

    interface OnFriendClickListener {
        void onFriendClick(String friendUid);
    }

    FriendListAdapter(List<FriendItem> friendList, OnFriendClickListener listener) {
        this.friendList = friendList != null ? friendList : new ArrayList<>();
        this.listener = listener;
    }

    @Override
    public FriendViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(FriendViewHolder holder, int position) {
        FriendItem friend = friendList.get(position);
        holder.friendNickname.setText(friend.nickname);
        holder.itemView.setOnClickListener(v -> listener.onFriendClick(friend.uid));
    }

    @Override
    public int getItemCount() {
        return friendList.size();
    }

    static class FriendViewHolder extends RecyclerView.ViewHolder {
        TextView friendNickname;

        FriendViewHolder(View itemView) {
            super(itemView);
            friendNickname = itemView.findViewById(R.id.friendNicknameText);
        }
    }
}

class ChatItem {
    String chatId;
    String type;
    String name;
    String lastMessage;

    ChatItem(String chatId, String type, String name, String lastMessage) {
        this.chatId = chatId;
        this.type = type != null ? type : "private";
        this.name = name != null ? name : "Unknown";
        this.lastMessage = lastMessage;
    }
}

class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatViewHolder> {
    private List<ChatItem> chatList;
    private String uid;
    private OnChatClickListener listener;

    interface OnChatClickListener {
        void onChatClick(ChatItem chat);
    }

    ChatListAdapter(List<ChatItem> chatList, String uid, OnChatClickListener listener) {
        this.chatList = chatList != null ? chatList : new ArrayList<>();
        this.uid = uid;
        this.listener = listener;
    }

    @Override
    public ChatViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_list, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ChatViewHolder holder, int position) {
        ChatItem chat = chatList.get(position);
        holder.chatName.setText(chat.name);
        holder.lastMessage.setText(chat.lastMessage != null ? chat.lastMessage : "");
        holder.itemView.setOnClickListener(v -> listener.onChatClick(chat));
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView chatName;
        TextView lastMessage;

        ChatViewHolder(View itemView) {
            super(itemView);
            chatName = itemView.findViewById(R.id.chatNameText);
            lastMessage = itemView.findViewById(R.id.lastMessageText);
        }
    }
}

class PendingFriendItem {
    String uid;
    String nickname;

    PendingFriendItem(String uid, String nickname) {
        this.uid = uid;
        this.nickname = nickname != null ? nickname : "Unknown";
    }
}

class UserItem {
    String uid;
    String nickname;

    UserItem(String uid, String nickname) {
        this.uid = uid;
        this.nickname = nickname != null ? nickname : "Unknown";
    }
}

class GroupItem {
    String chatId;
    String groupId;
    String name;

    GroupItem(String chatId, String groupId, String name) {
        this.chatId = chatId;
        this.groupId = groupId;
        this.name = name != null ? name : "Unknown Group";
    }
}

class PendingFriendListAdapter extends RecyclerView.Adapter<PendingFriendListAdapter.PendingFriendViewHolder> {
    private List<PendingFriendItem> pendingFriendList;
    private OnPendingFriendActionListener listener;

    interface OnPendingFriendActionListener {
        void onAction(String fromUid, boolean accept);
    }

    PendingFriendListAdapter(List<PendingFriendItem> pendingFriendList, OnPendingFriendActionListener listener) {
        this.pendingFriendList = pendingFriendList != null ? pendingFriendList : new ArrayList<>();
        this.listener = listener;
    }

    @Override
    public PendingFriendViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pending_friend, parent, false);
        return new PendingFriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(PendingFriendViewHolder holder, int position) {
        PendingFriendItem pendingFriend = pendingFriendList.get(position);
        holder.nicknameText.setText(pendingFriend.nickname);
        holder.acceptButton.setOnClickListener(v -> listener.onAction(pendingFriend.uid, true));
        holder.rejectButton.setOnClickListener(v -> listener.onAction(pendingFriend.uid, false));
    }

    @Override
    public int getItemCount() {
        return pendingFriendList.size();
    }

    static class PendingFriendViewHolder extends RecyclerView.ViewHolder {
        TextView nicknameText;
        Button acceptButton;
        Button rejectButton;

        PendingFriendViewHolder(View itemView) {
            super(itemView);
            nicknameText = itemView.findViewById(R.id.pendingNicknameText);
            acceptButton = itemView.findViewById(R.id.acceptButton);
            rejectButton = itemView.findViewById(R.id.rejectButton);
        }
    }
}
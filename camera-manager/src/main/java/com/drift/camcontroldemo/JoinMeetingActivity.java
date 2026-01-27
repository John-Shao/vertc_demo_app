package com.drift.camcontroldemo;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ss.video.rtc.demo.basic_module.utils.AppExecutors;
import com.ss.video.rtc.demo.basic_module.utils.IMEUtils;
import com.ss.video.rtc.demo.basic_module.utils.SafeToast;
import com.drift.util.TextWatcherHelper;

import com.drift.foreamlib.local.ctrl.LocalController;
import com.drift.foreamlib.local.ctrl.LocalListener;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class JoinMeetingActivity extends AppCompatActivity {

    private static final String TAG = "JoinMeetingActivity";
    private static final String ROOM_ID_REGEX = "^[A-Za-z0-9@_-]+$";
    private static final int ROOM_ID_MAX_LENGTH = 18;
    private static final String USER_NAME_REGEX = "^[\\u4e00-\\u9fa5a-zA-Z0-9@_-]+$";
    private static final int USER_NAME_MAX_LENGTH = 18;

    private String mCamIP;
    private String mSerialNumber;
    private String mStreamRes;
    private String mStreamBitrate;
    private EditText mInputRoomId;
    private TextWatcherHelper mRoomIdWatcher;
    private EditText mInputUserName;
    private TextWatcherHelper mUserNameWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_meeting);

        // 从 Intent 中获取 camIP 和 serialNumber
        Intent intent = getIntent();
        if (intent != null) {
            mCamIP = intent.getStringExtra("camIP");
            mSerialNumber = intent.getStringExtra("serialNumber");
            mStreamRes = "" + intent.getIntExtra("streamRes", 0);
            mStreamBitrate = "" + (intent.getIntExtra("streamBitrate", 0) / 8);
        }

        initViews();
    }

    private void initViews() {
        View rootView = findViewById(R.id.join_meeting_root);
        rootView.setOnClickListener(IMEUtils::closeIME);

        ImageView leftIv = findViewById(R.id.title_bar_left_iv);
        leftIv.setImageResource(R.drawable.ic_back_white);
        leftIv.setOnClickListener(v -> finish());

        mInputRoomId = findViewById(R.id.join_meeting_room_id);
        TextView inputRoomIdError = findViewById(R.id.join_meeting_room_id_waring);
        mRoomIdWatcher = new TextWatcherHelper(mInputRoomId, inputRoomIdError, ROOM_ID_REGEX,
            R.string.create_input_room_id_content_warn, ROOM_ID_MAX_LENGTH, R.string.create_input_room_id_length_warn);

        mInputUserName = findViewById(R.id.join_meeting_user_name);
        TextView inputUserNameError = findViewById(R.id.join_meeting_user_name_waring);
        mUserNameWatcher = new TextWatcherHelper(mInputUserName, inputUserNameError, USER_NAME_REGEX,
            R.string.create_input_user_name_content_warn, USER_NAME_MAX_LENGTH, R.string.create_input_user_name_length_warn);

        TextView joinMeetingBtn = findViewById(R.id.join_meeting_button);
        joinMeetingBtn.setOnClickListener(v -> {
            String roomId = mInputRoomId.getText().toString().trim();
            if (TextUtils.isEmpty(roomId)) {
                SafeToast.show(R.string.create_input_room_id_hint);
                return;
            }
            if (mRoomIdWatcher.isContentWarn()) {
                mRoomIdWatcher.showContentError();
                return;
            }
            String userName = mInputUserName.getText().toString().trim();
            if (TextUtils.isEmpty(userName)) {
                SafeToast.show(R.string.create_input_user_name_hint);
                return;
            }
            if (mUserNameWatcher.isContentWarn()) {
                mUserNameWatcher.showContentError();
                return;
            }
            // 处理进入会议的逻辑
            handleJoinMeeting(roomId, userName);
            IMEUtils.closeIME(v);
        });
    }

    /**
     * 处理进入会议的逻辑
     * @param roomId 房间ID
     * @param userName 用户名
     */
    private void handleJoinMeeting(String roomId, String userName) {
        // 检查 camIP 是否为空
        if (TextUtils.isEmpty(mCamIP)) {
            SafeToast.show(R.string.join_meeting_cam_ip_empty);
            return;
        }

        // 调用后端接口获取推流和拉流地址
        requestCameraJoinRoom(roomId, userName);
    }

    /**
     * 请求相机加入房间接口
     */
    private void requestCameraJoinRoom(String roomId, String userName) {
        AppExecutors.diskIO().execute(() -> {
            try {
                // 构建请求参数
                JSONObject params = new JSONObject();
                params.put("type", "CameraJoinRoom");

                JSONObject data = new JSONObject();
                data.put("user_id", userName);
                data.put("room_id", roomId);
                data.put("device_sn", mSerialNumber); // 使用设备序列号
                params.put("data", data);

                params.put("timestamp", System.currentTimeMillis());

                // 创建 OkHttpClient
                OkHttpClient client = new OkHttpClient();

                // 创建请求体
                RequestBody requestBody = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    params.toString()
                );

                // 构建请求
                Request request = new Request.Builder()
                    .url(BuildConfig.MEET_SERVER_URL)
                    .post(requestBody)
                    .build();

                // 发送请求
                Response response = client.newCall(request).execute();

                if (response.isSuccessful() && response.body() != null) {
                    String responseStr = response.body().string();
                    Log.d(TAG, "Response: " + responseStr);

                    JSONObject jsonResponse = new JSONObject(responseStr);
                    int code = jsonResponse.optInt("code");

                    if (code == 200) {
                        JSONObject responseData = jsonResponse.optJSONObject("data");
                        if (responseData != null) {
                            String rtmpUrl = responseData.optString("rtmp_url");
                            String rtspUrl = responseData.optString("rtsp_url");

                            // 在主线程中调用推流和拉流
                            AppExecutors.mainThread().execute(() -> {
                                // 调用推流方法
                                startPushStream(rtmpUrl);

                                // 调用拉流方法
                                startPullStream(rtspUrl);
                            });
                        } else {
                            showError("Response data is null");
                        }
                    } else {
                        String message = jsonResponse.optString("message", "Unknown error");
                        showError("Error code: " + code + ", message: " + message);
                    }
                } else {
                    showError("HTTP error: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "Request failed", e);
                showError(e.getMessage());
            }
        });
    }

    /**
     * 开始推流
     */
    private void startPushStream(String rtmpUrl) {
        LocalController localController = new LocalController();
        localController.startPushStreamWithURL(mCamIP, rtmpUrl, mStreamRes, mStreamBitrate,
            new LocalListener.OnCommonResListener() {
                @Override
                public void onCommonRes(boolean success) {
                    Log.d(TAG, "Push stream result: " + success);
                    if (!success) {
                        SafeToast.show("Failed to start push stream");
                    }
                }
            }
        );
    }

    /**
     * 开始拉流
     */
    private void startPullStream(String rtspUrl) {
        LocalController localController = new LocalController();
        localController.startPullStreamWithURL(mCamIP, rtspUrl,
            new LocalListener.OnCommonResListener() {
                @Override
                public void onCommonRes(boolean success) {
                    Log.d(TAG, "Pull stream result: " + success);
                    if (success) {
                        SafeToast.show("Join meeting successfully");
                        finish();
                    } else {
                        SafeToast.show("Failed to start pull stream");
                    }
                }
            }
        );
    }

    /**
     * 显示错误信息
     */
    private void showError(String message) {
        AppExecutors.mainThread().execute(() -> {
            SafeToast.show(R.string.join_meeting_request_failed);
            Log.e(TAG, "Error: " + message);
        });
    }
}

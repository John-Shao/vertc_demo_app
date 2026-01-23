package com.drift.camcontroldemo;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ss.video.rtc.demo.basic_module.utils.IMEUtils;
import com.ss.video.rtc.demo.basic_module.utils.SafeToast;
import com.drift.util.TextWatcherHelper;

public class JoinMeetingActivity extends AppCompatActivity {

    private static final String TAG = "JoinMeetingActivity";
    private static final String ROOM_ID_REGEX = "^[A-Za-z0-9@_-]+$";
    private static final int ROOM_ID_MAX_LENGTH = 18;
    private static final String USER_NAME_REGEX = "^[\\u4e00-\\u9fa5a-zA-Z0-9@_-]+$";
    private static final int USER_NAME_MAX_LENGTH = 18;

    private EditText mInputRoomId;
    private TextWatcherHelper mRoomIdWatcher;
    private EditText mInputUserName;
    private TextWatcherHelper mUserNameWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_meeting);
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
        mRoomIdWatcher = new TextWatcherHelper(mInputRoomId, inputRoomIdError, ROOM_ID_REGEX, R.string.create_input_room_id_content_warn, ROOM_ID_MAX_LENGTH, R.string.create_input_room_id_length_warn);

        mInputUserName = findViewById(R.id.join_meeting_user_name);
        TextView inputUserNameError = findViewById(R.id.join_meeting_user_name_waring);
        mUserNameWatcher = new TextWatcherHelper(mInputUserName, inputUserNameError, USER_NAME_REGEX, R.string.create_input_user_name_content_warn, USER_NAME_MAX_LENGTH, R.string.create_input_user_name_length_warn);

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
        // TODO: 实现进入会议的逻辑
    }
}

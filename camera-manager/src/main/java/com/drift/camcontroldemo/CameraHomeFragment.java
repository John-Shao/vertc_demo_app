package com.drift.camcontroldemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.drift.adapter.LinkCamListAdapter;
import com.drift.adapter.LinkCamListItemDecoration;
import com.drift.define.Intents;
import com.drift.foreamlib.api.CamInfo;
import com.drift.foreamlib.api.ForeamCamCtrl;
import com.drift.foreamlib.boss.model.CamStatus;
import com.drift.foreamlib.local.ctrl.LocalController;
import com.drift.foreamlib.local.ctrl.LocalListener;
import com.drift.foreamlib.util.CommonDefine;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Camera management home fragment
 * Displays list of available cameras and their status
 */
public class CameraHomeFragment extends Fragment {

    private static String TAG = "CameraHomeFragment";
    private ImageView ivLogo;
    private ImageView ivArrowUp;
    private RecyclerView rvList;
    private ImageView ivArrowDown;
    // private TextView tvVersion;
    private RelativeLayout rlAddCamera;

    private ArrayList<CamStatus> camInfoList;
    private LinkCamListAdapter m_videoListRecycleAdapter;

    private ForeamCamCtrl mForeamCamCtrl;

    private ArrayList<String> camsOnline;

    private Timer loopTimer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera_home, container, false);
        initViews(view);
        initData();
        return view;
    }

    private void initViews(View view) {
        ivLogo = view.findViewById(R.id.iv_logo);
        ivArrowUp = view.findViewById(R.id.iv_arrow_up);
        rvList = view.findViewById(R.id.rv_list);
        ivArrowDown = view.findViewById(R.id.iv_arrow_down);
        // tvVersion = view.findViewById(R.id.tv_version);
        rlAddCamera = view.findViewById(R.id.rl_add_camera);

        rlAddCamera.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), LinkAddCameraActivity.class);
            intent.putExtra("camsOnline", camsOnline);
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().overridePendingTransition(R.anim.trans_in_right, R.anim.stay);
            }
        });
    }

    private void initData() {
        camInfoList = new ArrayList<>();
        camsOnline = new ArrayList<>();
        /*
        try {
            if (getActivity() != null) {
                String versionName = getActivity().getPackageManager()
                        .getPackageInfo(getActivity().getPackageName(), 0).versionName;
                tvVersion.setText("V" + versionName);
            }
        } catch (Exception e) {
            tvVersion.setText("V1.0");
        }
        */
        mForeamCamCtrl = ForeamCamCtrl.getInstance();
        mForeamCamCtrl.setOnReceiveUDPMsgListener(mOnReceiveBoardcastMsgListener);
        mForeamCamCtrl.startReceive();

        initRecycleViewAdapter();
        checkPublishPermission();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mForeamCamCtrl != null) {
            mForeamCamCtrl.startReceive();
            mForeamCamCtrl.setOnReceiveUDPMsgListener(mOnReceiveBoardcastMsgListener);
        }
        initTimer();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mForeamCamCtrl != null) {
            mForeamCamCtrl.setOnReceiveUDPMsgListener(null);
        }
        freeTimer();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mForeamCamCtrl != null) {
            mForeamCamCtrl.stopReceive();
        }
        freeTimer();
    }

    private void initRecycleViewAdapter() {
        // Initialize 4 camera slots, all offline initially
        for (int index = 0; index < 4; ++index) {
            CamStatus cameraStatus = new CamStatus();
            cameraStatus.setOffline(true);
            camInfoList.add(cameraStatus);
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        rvList.setLayoutManager(layoutManager);
        rvList.addItemDecoration(new LinkCamListItemDecoration(dip2px(15), dip2px(15)));
        m_videoListRecycleAdapter = new LinkCamListAdapter(getActivity(), camInfoList);

        m_videoListRecycleAdapter.setOnRecordClickListener((view, position) -> {
            // 打开加入会议页面
            CamStatus camStatus = camInfoList.get(position);
            Intent intent = new Intent(getActivity(), JoinMeetingActivity.class);

            intent.putExtra("camIP",camStatus.getCamIP());
            intent.putExtra("streamRes",camStatus.getmStreamSetting().getStream_res());
            intent.putExtra("streamBitrate",camStatus.getmStreamSetting().getStream_bitrate());

            startActivity(intent);
        });

        m_videoListRecycleAdapter.setOnSettingClickListener((view, pos) -> {
            Log.d(TAG, "Settings clicked for position: " + pos);
            CamStatus cameraStatusNew = camInfoList.get(pos);
            // Only support new API
            if (cameraStatusNew.getmVideoSetting() != null) {
                startPreviewActivity(cameraStatusNew.getCamIP(), cameraStatusNew);
            }
        });

        rvList.setAdapter(m_videoListRecycleAdapter);
    }

    ForeamCamCtrl.OnReceiveUDPMsgListener mOnReceiveBoardcastMsgListener = new ForeamCamCtrl.OnReceiveUDPMsgListener() {
        public void camIsOnline(String serialNum, String msgValue, String camIP, String ownerId) {
            Log.d(TAG, "Received heartbeat - Serial: " + serialNum + ", IP: " + camIP);

            // Add camera to online list if not already present
            if (!camsOnline.contains(camIP)) {
                camsOnline.add(camIP);
                // Expand camera info array if needed
                if (camsOnline.size() > camInfoList.size()) {
                    CamStatus cameraStatus = new CamStatus();
                    cameraStatus.setOffline(true);
                    camInfoList.add(cameraStatus);
                    ivArrowDown.setVisibility(View.VISIBLE);
                    ivArrowUp.setVisibility(View.VISIBLE);
                }
            }

            // Update camera status array
            int index = camsOnline.indexOf(camIP);
            CamStatus cameraStatusNew = camInfoList.get(index);
            if (cameraStatusNew.isOffline()) {
                cameraStatusNew.setOffline(false);
                cameraStatusNew.setCamIP(camIP);
                cameraStatusNew.setModelName(parseModelName(serialNum));
                getCamStatus(camIP);
                m_videoListRecycleAdapter.notifyItemChanged(index);
            }
        }

        public void camIsOffline(String camIP) {
            Log.d(TAG, "Camera " + camIP + " is offline");
        }

        public void numberOfCamsOnline(ArrayMap<String, CamInfo> arrayList) {
            
        }
    };

    public void initTimer() {
        if (loopTimer != null)
            return;
        loopTimer = new Timer(true);

        TimerTask task = new TimerTask() {
            public void run() {
                Log.d(TAG, "Timer loop - polling camera status");
                for (int i = 0; i < camsOnline.size(); i++) {
                    CamStatus cameraStatusNew = camInfoList.get(i);
                    if (!cameraStatusNew.isOffline()) {
                        String ipAddr = cameraStatusNew.getCamIP();
                        getCamStatus(ipAddr);
                    }
                }
            }
        };
        loopTimer.schedule(task, 3000, 3000);
    }

    public void freeTimer() {
        if (loopTimer != null) {
            loopTimer.cancel();
            loopTimer = null;
        }
    }

    private void getCamStatus(String ipAddr) {
        new LocalController().getCamStaus(ipAddr, new LocalListener.OnGetCamStatusListener() {
            @Override
            public void onGetCamStatus(boolean success, CamStatus status, String serverIp) {
                Log.d(TAG, "Get camera status - success: " + success + ", IP: " + serverIp);

                int index = camsOnline.indexOf(serverIp);
                if (index < 0)
                    return;

                CamStatus camStatus = camInfoList.get(index);
                Boolean ifNeedUpdate = false;

                if (!success) {
                    camStatus.setOffline(true);
                    m_videoListRecycleAdapter.notifyItemChanged(index);
                    return;
                }

                // Check if this is old API (not supported)
                if (status == null || status.getmStreamSetting() == null) {
                    if (!camStatus.isInit()) {
                        camStatus.setInit(true);
                        camStatus.setmCameraSettingNew(status.getmCameraSetting());
                        camStatus.setmVideoSetting(status.getmVideoSetting());
                        camStatus.setmStreamSetting(status.getmStreamSetting());
                        camStatus.setmCameraStatus(status.getmCameraStatus());
                        m_videoListRecycleAdapter.notifyItemChanged(index);
                    }
                    return;
                }

                if (success) {
                    if ((!camStatus.isInit()) ||
                        (camStatus.getmCameraStatus().getCapture_mode() != status.getmCameraStatus().getCapture_mode()) ||
                        (camStatus.getmCameraStatus().getBattery() != status.getmCameraStatus().getBattery()) ||
                        (camStatus.getmCameraStatus().getSd_free() != status.getmCameraStatus().getSd_free()) ||
                        (camStatus.getmCameraStatus().getRec_time() != status.getmCameraStatus().getRec_time()) ||
                        (camStatus.getmVideoSetting().getVideo_res() != status.getmVideoSetting().getVideo_res()) ||
                        (camStatus.getmVideoSetting().getVideo_framerate() != status.getmVideoSetting().getVideo_framerate()) ||
                        (camStatus.getmVideoSetting().getVideo_bitrate() != status.getmVideoSetting().getVideo_bitrate())) {
                        ifNeedUpdate = true;
                    }
                    camStatus.setInit(true);
                    camStatus.setmCameraSettingNew(status.getmCameraSetting());
                    camStatus.setmVideoSetting(status.getmVideoSetting());
                    camStatus.setmStreamSetting(status.getmStreamSetting());
                    camStatus.setmCameraStatus(status.getmCameraStatus());
                } else {
                    ifNeedUpdate = true;
                    camStatus.setOffline(true);
                }

                if (ifNeedUpdate)
                    m_videoListRecycleAdapter.notifyItemChanged(index);
            }
        });
    }

    private void startPreviewActivity(String ipAddr, CamStatus cameraStatusNew) {
        Intent intent = new Intent(getActivity(), LinkCamDetailActivity.class);
        intent.putExtra(Intents.LINK_CAM_IP, ipAddr);
        intent.putExtra(Intents.LINK_CAM_INFO, cameraStatusNew);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.trans_in_right, R.anim.stay);
        }
    }

    private int dip2px(float dipValue) {
        if (getActivity() != null) {
            float scale = getActivity().getResources().getDisplayMetrics().scaledDensity;
            return (int) (dipValue * scale + 0.5f);
        }
        return (int) dipValue;
    }

    private boolean checkPublishPermission() {
        if (Build.VERSION.SDK_INT >= 23 && getActivity() != null) {
            List<String> permissions = new ArrayList<>();
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if (Build.VERSION.SDK_INT >= 33) {
                    permissions.add("android.permission.READ_MEDIA_IMAGES");
                    permissions.add("android.permission.READ_MEDIA_VIDEO");
                }
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (permissions.size() != 0) {
                requestPermissions(permissions.toArray(new String[0]), 100);
                return false;
            }
        }
        return true;
    }

    private String parseModelName(String value) {
        String mCamType = CommonDefine.DriftGhost4KPlus;
        if (value.contains("GX"))
            mCamType = CommonDefine.DriftGhostX;
        else if (value.contains("XL Pro"))
            mCamType = CommonDefine.DriftGhostXLPro;
        else if (value.contains("XL"))
            mCamType = CommonDefine.DriftGhostDC;
        else if (value.contains("X1"))
            mCamType = CommonDefine.X1;
        else if (value.contains("X3"))
            mCamType = CommonDefine.X3;
        return mCamType;
    }
}

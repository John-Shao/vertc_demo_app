// Copyright (c) 2023 Beijing Volcano Engine Technology Ltd.
// SPDX-License-Identifier: MIT

package com.volcengine.vertcdemo.login;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import androidx.annotation.NonNull;

import com.ss.video.rtc.demo.basic_module.acivities.BaseActivity;
import com.ss.video.rtc.demo.basic_module.adapter.TextWatcherAdapter;
import com.ss.video.rtc.demo.basic_module.utils.IMEUtils;
import com.volcengine.vertcdemo.common.SolutionToast;
import com.volcengine.vertcdemo.core.SolutionDataManager;
import com.volcengine.vertcdemo.core.eventbus.RefreshUserNameEvent;
import com.volcengine.vertcdemo.core.eventbus.SolutionDemoEventManager;
import com.volcengine.vertcdemo.core.net.ErrorTool;
import com.volcengine.vertcdemo.core.net.IRequestCallback;
import com.volcengine.vertcdemo.core.net.ServerResponse;
import com.volcengine.vertcdemo.entity.LoginInfo;
import com.volcengine.vertcdemo.login.databinding.ActivityLoginBinding;

import java.util.regex.Pattern;

public class LoginActivity extends BaseActivity implements View.OnClickListener {
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";
    private static final int COUNTDOWN_SECONDS = 60;

    private ActivityLoginBinding mViewBinding;
    private boolean mIsPolicyChecked = false;
    private CountDownTimer mCountDownTimer;
    private boolean mIsCountingDown = false;

    private final TextWatcherAdapter mTextWatcher = new TextWatcherAdapter() {
        @Override
        public void afterTextChanged(Editable s) {
            super.afterTextChanged(s);
            setupConfirmStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mViewBinding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(mViewBinding.getRoot());

        mViewBinding.verifyConfirm.setOnClickListener(this);
        mViewBinding.verifyRootLayout.setOnClickListener(this);
        mViewBinding.verifyPolicyText.setOnClickListener(this);
        mViewBinding.verifySendCodeBtn.setOnClickListener(this);

        mViewBinding.verifyPolicyText.setText(getSpannedText());
        mViewBinding.verifyPolicyText.setMovementMethod(LinkMovementMethod.getInstance());

        mViewBinding.verifyInputPhoneEt.addTextChangedListener(mTextWatcher);
        mViewBinding.verifyInputCodeEt.addTextChangedListener(mTextWatcher);

        setupConfirmStatus();
    }

    private SpannableStringBuilder getSpannedText() {
        String termsOfService = getString(R.string.login_terms_of_service);
        String privacyPolicy = getString(R.string.login_privacy_policy);
        String completeTip = getString(R.string.read_and_agree, termsOfService, privacyPolicy);

        SpannableStringBuilder ssb = new SpannableStringBuilder(completeTip);
        ForegroundColorSpan greySpan = new ForegroundColorSpan(Color.parseColor("#86909C"));
        ssb.setSpan(greySpan, 0, completeTip.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);

        int termsIndex = completeTip.indexOf(termsOfService);
        if (termsIndex >= 0) {
            ssb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    openBrowser(BuildConfig.TERMS_OF_SERVICE_URL);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setColor(Color.parseColor("#4080FF"));
                    ds.setUnderlineText(false);
                }
            }, termsIndex, termsIndex + termsOfService.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }

        int policyIndex = completeTip.indexOf(privacyPolicy);
        if (policyIndex >= 0) {
            ssb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    openBrowser(BuildConfig.PRIVACY_POLICY_URL);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setColor(Color.parseColor("#4080FF"));
                    ds.setUnderlineText(false);
                }
            }, policyIndex, policyIndex + privacyPolicy.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }

        return ssb;
    }

    @Override
    public void onClick(View v) {
        if (v == mViewBinding.verifyConfirm) {
            onClickConfirm();
        } else if (v == mViewBinding.verifyRootLayout) {
            IMEUtils.closeIME(mViewBinding.verifyRootLayout);
        } else if (v == mViewBinding.verifyPolicyText) {
            updatePolicyChecked();
            setupConfirmStatus();
        } else if (v == mViewBinding.verifySendCodeBtn) {
            onClickSendCode();
        }
    }

    private void setupConfirmStatus() {
        String phone = mViewBinding.verifyInputPhoneEt.getText().toString().trim();
        String code = mViewBinding.verifyInputCodeEt.getText().toString().trim();

        if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(code)) {
            mViewBinding.verifyConfirm.setAlpha(0.3F);
            mViewBinding.verifyConfirm.setEnabled(false);
        } else {
            boolean matchPhoneRegex = Pattern.matches(PHONE_REGEX, phone);
            if (mIsPolicyChecked && matchPhoneRegex && code.length() >= 4) {
                mViewBinding.verifyConfirm.setEnabled(true);
                mViewBinding.verifyConfirm.setAlpha(1F);
            } else {
                mViewBinding.verifyConfirm.setAlpha(0.3F);
                mViewBinding.verifyConfirm.setEnabled(false);
            }
        }
    }

    private void onClickSendCode() {
        String phone = mViewBinding.verifyInputPhoneEt.getText().toString().trim();

        if (TextUtils.isEmpty(phone)) {
            mViewBinding.verifyInputPhoneWarningTv.setVisibility(View.VISIBLE);
            mViewBinding.verifyInputPhoneWarningTv.setText(R.string.phone_number_invalid);
            return;
        }

        if (!Pattern.matches(PHONE_REGEX, phone)) {
            mViewBinding.verifyInputPhoneWarningTv.setVisibility(View.VISIBLE);
            mViewBinding.verifyInputPhoneWarningTv.setText(R.string.phone_number_invalid);
            return;
        }

        if (mIsCountingDown) {
            return;
        }

        mViewBinding.verifyInputPhoneWarningTv.setVisibility(View.INVISIBLE);

        // 调用发送验证码接口
        LoginApi.sendSmsCode(phone, new IRequestCallback<ServerResponse<Void>>() {
            @Override
            public void onSuccess(ServerResponse<Void> data) {
                SolutionToast.show(R.string.sms_code_sent);
                startCountDown();
            }

            @Override
            public void onError(int errorCode, String message) {
                SolutionToast.show(ErrorTool.getErrorMessageByErrorCode(errorCode, message));
            }
        });
    }

    private void startCountDown() {
        mIsCountingDown = true;
        mViewBinding.verifySendCodeBtn.setEnabled(false);

        mCountDownTimer = new CountDownTimer(COUNTDOWN_SECONDS * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                mViewBinding.verifySendCodeBtn.setText(getString(R.string.resend_after_seconds, seconds));
            }

            @Override
            public void onFinish() {
                mIsCountingDown = false;
                mViewBinding.verifySendCodeBtn.setEnabled(true);
                mViewBinding.verifySendCodeBtn.setText(R.string.get_verification_code);
            }
        };
        mCountDownTimer.start();
    }

    private void onClickConfirm() {
        String phone = mViewBinding.verifyInputPhoneEt.getText().toString().trim();
        String code = mViewBinding.verifyInputCodeEt.getText().toString().trim();

        if (TextUtils.isEmpty(code)) {
            mViewBinding.verifyInputCodeWarningTv.setVisibility(View.VISIBLE);
            mViewBinding.verifyInputCodeWarningTv.setText(R.string.sms_code_empty);
            return;
        }

        mViewBinding.verifyInputCodeWarningTv.setVisibility(View.INVISIBLE);
        mViewBinding.verifyConfirm.setEnabled(false);
        IMEUtils.closeIME(mViewBinding.verifyConfirm);

        LoginApi.smsCodeLogin(phone, code, new IRequestCallback<ServerResponse<LoginInfo>>() {
            @Override
            public void onSuccess(ServerResponse<LoginInfo> data) {
                LoginInfo login = data.getData();
                if (login == null) {
                    SolutionToast.show(R.string.network_message_1011);
                    return;
                }

                mViewBinding.verifyConfirm.setEnabled(true);
                SolutionDataManager.ins().setUserName(login.user_name);
                SolutionDataManager.ins().setUserId(login.user_id);
                SolutionDataManager.ins().setToken(login.login_token);
                SolutionDemoEventManager.post(new RefreshUserNameEvent(login.user_name, true));
                LoginActivity.this.finish();
            }

            @Override
            public void onError(int errorCode, String message) {
                SolutionToast.show(ErrorTool.getErrorMessageByErrorCode(errorCode, message));
                mViewBinding.verifyConfirm.setEnabled(false);
            }
        });
    }

    private void updatePolicyChecked() {
        mIsPolicyChecked = !mIsPolicyChecked;
        mViewBinding.verifyPolicyText.setSelected(mIsPolicyChecked);
    }

    private void openBrowser(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        startActivity(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
            mCountDownTimer = null;
        }
    }

    @Override
    public void onBackPressed() {
        //ignore
    }
}

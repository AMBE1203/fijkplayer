package com.befovy.fijkplayer;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.view.TextureRegistry;

/**
 * FijkPlugin
 */
public class FijkPlugin implements MethodCallHandler, FlutterPlugin, ActivityAware, AudioManager.OnAudioFocusChangeListener {

    private static final int ALWAYS_SHOW_UI = 3;

    private WeakReference<Activity> mActivity;
    private WeakReference<Context> mContext;
    private FlutterPluginBinding mBinding;

    private int volumeUIMode = ALWAYS_SHOW_UI;
    private float volStep = 1.0f / 16.0f;
    private boolean eventListening = false;
    private EventChannel mEventChannel;
    private Object mAudioFocusRequest;
    private boolean mAudioFocusRequested = false;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        final MethodChannel channel = new MethodChannel(binding.getBinaryMessenger(), "befovy.com/fijk");
        mBinding = binding;
        mContext = new WeakReference<>(binding.getApplicationContext());
        channel.setMethodCallHandler(this);

        AudioManager audioManager = audioManager();
        if (audioManager != null) {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            volStep = Math.max(1.0f / (float) max, volStep);
        }

        init(binding.getBinaryMessenger());
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        mContext = null;
        mBinding = null;
        if (mEventChannel != null) {
            mEventChannel.setStreamHandler(null);
        }
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        mActivity = new WeakReference<>(binding.getActivity());
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        mActivity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        mActivity = new WeakReference<>(binding.getActivity());
    }

    @Override
    public void onDetachedFromActivity() {
        mActivity = null;
    }

    @Nullable
    public TextureRegistry.SurfaceTextureEntry createSurfaceEntry() {
        if (mBinding != null) {
            return mBinding.getTextureRegistry().createSurfaceTexture();
        }
        return null;
    }

    @Nullable
    public BinaryMessenger messenger() {
        if (mBinding != null) {
            return mBinding.getBinaryMessenger();
        }
        return null;
    }

    @Nullable
    public Context context() {
        if (mContext != null) {
            return mContext.get();
        }
        return null;
    }

    @Nullable
    private Activity activity() {
        if (mActivity != null) {
            return mActivity.get();
        }
        return null;
    }

    @Nullable
    public String lookupKeyForAsset(@NonNull String asset, @Nullable String packageName) {
        if (mBinding != null) {
            if (TextUtils.isEmpty(packageName)) {
                return mBinding.getFlutterAssets().getAssetFilePathByName(asset);
            } else {
                return mBinding.getFlutterAssets().getAssetFilePathByName(asset, packageName);
            }
        }
        return null;
    }

    private void init(BinaryMessenger messenger) {
        if (mEventChannel != null) {
            mEventChannel.setStreamHandler(null);
        }
        mEventChannel = new EventChannel(messenger, "befovy.com/fijk/event");
        mEventChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object o, EventChannel.EventSink eventSink) {
                // Temporarily do nothing until QueuingEventSink is restored
            }

            @Override
            public void onCancel(Object o) {
                // Temporarily do nothing
            }
        });
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        Activity activity;
        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + android.os.Build.VERSION.RELEASE);
                break;
            case "init":
                Log.i("FLUTTER", "call init:" + call.arguments.toString());
                result.success(null);
                break;
            case "setOrientationPortrait":
                boolean changedPort = false;
                activity = activity();
                if (activity != null) {
                    int current_orientation = activity.getResources().getConfiguration().orientation;
                    if (current_orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
                        } else {
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                        }
                        changedPort = true;
                    }
                }
                result.success(changedPort);
                break;
            case "setOrientationLandscape":
                boolean changedLand = false;
                activity = activity();
                if (activity != null) {
                    int current_orientation = activity.getResources().getConfiguration().orientation;
                    if (current_orientation == Configuration.ORIENTATION_PORTRAIT) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
                        } else {
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                        }
                        changedLand = true;
                    }
                }
                result.success(changedLand);
                break;
            case "setOrientationAuto":
                activity = activity();
                if (activity != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
                    } else {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
                    }
                }
                result.success(null);
                break;
            case "setScreenOn":
                boolean screenOn = false;
                if (call.hasArgument("on")) {
                    Boolean on = call.argument("on");
                    screenOn = on != null ? on : false;
                }
                setScreenOn(screenOn);
                result.success(null);
                break;
            case "isScreenKeptOn":
                result.success(isScreenKeptOn());
                break;
            case "brightness":
                float brightnessGot = getScreenBrightness();
                result.success(brightnessGot);
                break;
            case "setBrightness":
                if (call.hasArgument("brightness")) {
                    final Double var = call.argument("brightness");
                    if (var != null) {
                        float brightness = var.floatValue();
                        setScreenBrightness(brightness);
                    }
                }
                result.success(null);
                break;
            case "requestAudioFocus":
                audioFocus(true);
                result.success(null);
                break;
            case "releaseAudioFocus":
                audioFocus(false);
                result.success(null);
                break;
            case "volumeDown":
                float stepDown = volStep;
                if (call.hasArgument("step")) {
                    final Double step = call.argument("step");
                    stepDown = step != null ? step.floatValue() : stepDown;
                }
                result.success(volumeDown(stepDown));
                break;
            case "volumeUp":
                float stepUp = volStep;
                if (call.hasArgument("step")) {
                    final Double step = call.argument("step");
                    stepUp = step != null ? step.floatValue() : stepUp;
                }
                result.success(volumeUp(stepUp));
                break;
            case "volumeMute":
                result.success(volumeMute());
                break;
            case "systemVolume":
                result.success(systemVolume());
                break;
            case "volumeSet":
                float vol = systemVolume();
                final Double v = call.argument("vol");
                if (v != null) {
                    vol = setSystemVolume(v.floatValue());
                }
                result.success(vol);
                break;
            case "volUiMode":
                final Integer mode = call.argument("mode");
                if (mode != null) {
                    volumeUIMode = mode;
                }
                result.success(null);
                break;
            case "onLoad":
                eventListening = true;
                result.success(null);
                break;
            case "onUnload":
                eventListening = false;
                result.success(null);
                break;
            default:
                Log.w("FLUTTER", "onMethod Call, name: " + call.method);
                result.notImplemented();
                break;
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                mAudioFocusRequested = false;
                mAudioFocusRequest = null;
                break;
        }
        Log.i("FIJKPLAYER", "onAudioFocusChange: " + focusChange);
    }

    public void setScreenOn(boolean on) {
        Activity activity = activity();
        if (activity == null || activity.getWindow() == null)
            return;
        if (on) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private boolean isScreenKeptOn() {
        Activity activity = activity();
        if (activity == null || activity.getWindow() == null)
            return false;
        int flag = activity.getWindow().getAttributes().flags;
        return (flag & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
    }

    private float getScreenBrightness() {
        Activity activity = activity();
        if (activity == null || activity.getWindow() == null)
            return 0;
        float brightness = activity.getWindow().getAttributes().screenBrightness;
        if (brightness < 0) {
            Context context = context();
            Log.w("FIJKPLAYER", "window attribute brightness less than 0");
            try {
                if (context != null) {
                    brightness = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / (float) 255;
                }
            } catch (Settings.SettingNotFoundException e) {
                Log.e("FIJKPLAYER", "System brightness settings not found");
                brightness = 1.0f;
            }
        }
        return brightness;
    }

    private void setScreenBrightness(float brightness) {
        Activity activity = activity();
        if (activity == null || activity.getWindow() == null)
            return;
        WindowManager.LayoutParams layoutParams = activity.getWindow().getAttributes();
        layoutParams.screenBrightness = brightness;
        activity.getWindow().setAttributes(layoutParams);
    }

    @TargetApi(26)
    private void requestAudioFocus() {
        AudioManager audioManager = audioManager();
        if (audioManager == null)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes audioAttributes =
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build();

            AudioFocusRequest audioFocusRequest =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(audioAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(this)
                            .build();
            mAudioFocusRequest = audioFocusRequest;
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(this,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        mAudioFocusRequested = true;
    }

    @TargetApi(26)
    private void abandonAudioFocus() {
        AudioManager audioManager = audioManager();
        if (audioManager == null)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mAudioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest((AudioFocusRequest) mAudioFocusRequest);
                mAudioFocusRequest = null;
            }
        } else {
            audioManager.abandonAudioFocus(this);
        }
        mAudioFocusRequested = false;
    }

    public void audioFocus(boolean request) {
        Log.i("FIJKPLAYER", "audioFocus " + (request ? "request" : "release") + " state:" + mAudioFocusRequested);
        if (request && !mAudioFocusRequested) {
            requestAudioFocus();
        } else if (mAudioFocusRequested) {
            abandonAudioFocus();
        }
    }

    @Nullable
    private AudioManager audioManager() {
        Context context = context();
        if (context != null) {
            return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        } else {
            Log.e("FIJKPLAYER", "context null, can't get AudioManager");
            return null;
        }
    }

    private float systemVolume() {
        AudioManager audioManager = audioManager();
        if (audioManager != null) {
            float max = (float) audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            float vol = (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            return vol / max;
        } else {
            return 0.0f;
        }
    }

    private float volumeUp(float step) {
        float vol = systemVolume();
        vol = vol + step;
        vol = setSystemVolume(vol);
        return vol;
    }

    private float volumeDown(float step) {
        float vol = systemVolume();
        vol = vol - step;
        vol = setSystemVolume(vol);
        return vol;
    }

    private float volumeMute() {
        setSystemVolume(0.0f);
        return 0.0f;
    }

    private float setSystemVolume(float vol) {
        int flag = volumeUIMode == ALWAYS_SHOW_UI ? AudioManager.FLAG_SHOW_UI : 0;
        AudioManager audioManager = audioManager();
        if (audioManager != null) {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int volIndex = (int) (vol * max);
            volIndex = Math.min(volIndex, max);
            volIndex = Math.max(volIndex, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volIndex, flag);
            return (float) volIndex / (float) max;
        } else {
            return vol;
        }
    }
}

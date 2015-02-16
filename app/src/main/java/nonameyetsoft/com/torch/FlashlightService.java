package nonameyetsoft.com.torch;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.IOException;

@SuppressWarnings("deprecation")
public class FlashlightService extends Service {

    private static FlashlightService instance = null;

    private Camera mCamera = null;
    private Notifications notifications = null;
    private SurfaceHolder mHolder = null;
    private SurfaceView mPreview = null;
    private SystemManager mSystemManager = null;
    private WindowManager mWindowManager = null;
    private BroadcastReceiver mReceiver;
    private boolean isReceiverRegistered = false;

    private boolean flashOn = false;

    public static boolean isRunning() {
        return instance != null;
    }

    public static FlashlightService getInstance() {
        return instance;
    }

    /* Start Override methods of the Service class. */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(Flashlight.LOG_TAG, "Service started.");
        instance = this;
        openCamera();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Bundle extras = intent.getExtras();
        if (extras != null) {
            String command = (String) extras.get("command");
            if (command.equalsIgnoreCase("turnOn")) {
                lightenTorch();
            } else if (command.equalsIgnoreCase("turnOff")) {
                stopTorch();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        if (flashOn) {
            stopTorch();
        }
        destroyCamera();
        removeSurfaceView();
        if (notifications != null) {
            notifications.endNotification();
        }
        Log.i(Flashlight.LOG_TAG, "Service down.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    /* End Override methods of the Service class. */

    private void openCamera() {
        try {
            mCamera = Camera.open();
        } catch (RuntimeException e) {
            Log.w(
                    Flashlight.LOG_TAG,
                    "Failed to open camera, probably in use by another App."
            );
            // If we fail to open camera, make sure to kill the newly started
            // service, as it is of no use.
            stopSelf();
            Flashlight.setBusy(true);
            if (!Flashlight.isBusyByWidget()) {
                Helpers.showFlashlightBusyDialog(MainActivity.getContext());
            }

        }
    }

    private void destroyCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    public void stopTorch() {
        setCameraModeTorch(false);
        mCamera.stopPreview();
        Flashlight.setIsOn(false);
        flashOn = false;
        if (mSystemManager != null) {
            mSystemManager.releaseWakeLock();
        }
        if (isReceiverRegistered) {
            unregisterBroadcastReceiver();
            notifications.endNotification();
        }
    }

    private void setCameraPreview() {
        try {
            mCamera.setPreviewDisplay(mHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setCameraModeTorch(boolean ON) {
        Camera.Parameters mParams = mCamera.getParameters();
        if (ON) {
            mParams.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            mCamera.setParameters(mParams);
        } else {
            mParams.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            mCamera.setParameters(mParams);
        }
    }

    public void lightenTorch() {
        mPreview = new SurfaceView(instance);
        mHolder = mPreview.getHolder();
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                setCameraPreview();
                setCameraModeTorch(true);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mCamera.startPreview();
                    }
                }).start();
                flashOn = true;
                Flashlight.setIsOn(true);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });

        mWindowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                1, 1, WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY, 0, PixelFormat.UNKNOWN);
        mWindowManager.addView(mPreview, params);

        mSystemManager = new SystemManager(this);
        notifications = new Notifications(this);

        mSystemManager.setWakeLock();
        registerBroadcastReceiver();
        notifications.startNotification();
    }

    private void removeSurfaceView() {
        if (mWindowManager != null) {
            mWindowManager.removeView(mPreview);
        }
    }
    ///////////////////////////////////////////////
    private void initializeBroadcastReceiver() {
        mReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                stopSelf();

            }
        };
    }

    private void registerBroadcastReceiver() {
        initializeBroadcastReceiver();
        IntentFilter filter = new IntentFilter("android.intent.CLOSE_ACTIVITY");
        registerReceiver(mReceiver, filter);
        setBroadcastReceiverIsRegistered(true);
    }

    private void setBroadcastReceiverIsRegistered(boolean registered) {
        isReceiverRegistered = registered;
    }

    private void unregisterBroadcastReceiver() {
        try {
            unregisterReceiver(mReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(Flashlight.LOG_TAG, "Receiver not registered.");
        }
        setBroadcastReceiverIsRegistered(false);
    }
    ///////////////////////////////////////////////////////
}
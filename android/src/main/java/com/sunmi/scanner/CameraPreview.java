package com.sunmi.scanner;

/**
 * Created by Jakub on 2019/3/11.
 */

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.CameraMetadata;
import android.graphics.ImageFormat;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Collections;

@TargetApi(23)
public class CameraPreview extends TextureView implements TextureView.SurfaceTextureListener {
    private static final String TAG = "CameraPreview";

    private SurfaceTexture _surfaceTexture;
    private int _surfaceTextureWidth;
    private int _surfaceTextureHeight;

    private android.hardware.camera2.CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private ImageReader mImageReader;
    protected CameraCaptureSession mCameraCaptureSession;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private String mCameraId;

    // by default autofocus is on
    private boolean autoFocus = true;
    private int focusDistance;
    private boolean cameraRunning = false;

    ////////////////////////// CALLBACKS

    private ImageReader.OnImageAvailableListener mPreviewCallback;
    private CameraCaptureSession.StateCallback mCameraCaptureSessionCallback = new CameraCaptureSession.StateCallback(){
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            //The camera is already closed
            if (null == mCameraDevice) {
                return;
            }
            // When the session is ready, we start displaying the preview.
            mCameraCaptureSession = cameraCaptureSession;
            updatePreview();
        }
        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.i(TAG, "Configuration change");
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            createCameraPreview(_surfaceTexture);
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            mCameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    };




    public CameraPreview(Context context, ImageReader.OnImageAvailableListener previewCallback) {
        super(context);
        this.setSurfaceTextureListener(this);

        mPreviewCallback = previewCallback;

        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        }
    }

    public void openCamera() {
        if (cameraRunning)
            return;

        mCameraManager = (android.hardware.camera2.CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);

        try {
            mCameraId = mCameraManager.getCameraIdList()[0];
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            imageDimension = chooseOptimalSize(map.getOutputSizes(ImageFormat.JPEG), _surfaceTextureWidth, _surfaceTextureHeight);

            Log.d(TAG, "Used size: " + imageDimension.getWidth() + "x" + imageDimension.getHeight());

            requestLayout();
            startBackgroundThread();
            mCameraManager.openCamera(mCameraId, stateCallback, null);

            cameraRunning = true;
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview(SurfaceTexture texture) {
        try {
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder.addTarget(surface);

            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size previewSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.YUV_420_888), _surfaceTextureWidth, _surfaceTextureHeight);

            Log.d(TAG, "Preview size: " + previewSize.getWidth() + "x" + previewSize.getHeight());
            mImageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);

            mImageReader.setOnImageAvailableListener(mPreviewCallback, mBackgroundHandler);

            captureRequestBuilder.addTarget(mImageReader.getSurface());

            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), mCameraCaptureSessionCallback, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    protected void updatePreview() {
        if(null == mCameraDevice) {
            Log.e(TAG, "Can't update preview!");
            return;
        }

        Log.d(TAG, "Updating preview");
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

        if (!autoFocus) {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);

            float minDist = getMinimumFocusDistance();
            float num = ((focusDistance) * minDist / 100);
            Log.d(TAG, "Setting focus: " + focusDistance + "(minimumFocusDistance: " + minDist + ") result: " + num);

            captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, num);
        } else {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        }

        try {
            mCameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (Throwable e) {
            Log.e(TAG, "Error on upadte preview: " + e);
        }
    }

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        if (mBackgroundThread == null)
            return;

        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.i(TAG, "Exception when stopping thread: " + e);
        }

        cameraRunning = false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        Log.d(TAG, "TextureView Size: " + width + "x" + height);
        setMeasuredDimension(width, height);
    }

    public void closeCamera() {
        if (mCameraDevice != null)
            mCameraDevice.close();

        stopBackgroundThread();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        _surfaceTexture = surface;
        _surfaceTextureWidth = width;
        _surfaceTextureHeight = height;

        openCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        _surfaceTexture = surface;
        _surfaceTextureWidth = width;
        _surfaceTextureHeight = height;
        try {
            Log.d(TAG, "CHANGING ASPECT RATIO: " + width + "x" + height);

            openCamera();
        } catch (Exception e) {
            Log.e(TAG, e.toString(), e);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        _surfaceTexture = null;
        _surfaceTextureWidth = 0;
        _surfaceTextureHeight = 0;
        stopBackgroundThread();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    public void setFlash(boolean flag) {
        if (captureRequestBuilder == null) {
            Log.w(TAG, "setFlash couldn't finish!");
            return;
        }

        if (flag)
            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
        else
            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);

        try {
            mCameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (Throwable e) {
            Log.e(TAG, "CAMERA ERROR: " + e);
        }
    }

    ////////////////////////// FOCUS FUNCTIONS

    public void setAutoFocus(boolean autoFocus) {
        Log.d(TAG, "setting AutoFocus to " + autoFocus);

        if (autoFocus == this.autoFocus)
            return;

        this.autoFocus = autoFocus;
        updatePreview();
    }

    public void setFocusDistance(int focus) {
        this.focusDistance = focus;

        updatePreview();
    }

    private float getMinimumFocusDistance() {
        if (mCameraId == null)
            return 0;

        Float minimumLens = null;
        try {
            CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics c = manager.getCameraCharacteristics(mCameraId);
            minimumLens = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        } catch (Exception e) {
            Log.e(TAG, "isHardwareLevelSupported Error", e);
        }
        if (minimumLens != null)
            return minimumLens;
        return 0;
    }

    ////////////////////////// HELPER FUNCTIONS
    // Taken from https://github.com/googlesamples/android-Camera2Basic
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight) {
        int maxWidth = textureViewWidth, maxHeight = textureViewHeight;
        Size aspectRatio = new Size(textureViewWidth, textureViewHeight);

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new com.sunmi.scanner.CameraPreview.CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new com.sunmi.scanner.CameraPreview.CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
}

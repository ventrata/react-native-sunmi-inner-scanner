package com.sunmi.scanner;

/**
 * Created by januslo on 2017/5/16.
 */

import android.hardware.Camera;

import java.util.List;
import static java.lang.Thread.sleep;

public class CameraManager {
    private static final int FLASH_TORCH = 1;
    private static final int FLASH_OFF = 0;

    private int mCameraId = -1;
    private Camera mCamera;

    public CameraManager() {
        //mCamera = getCameraInstance();
    }

    public Camera getCamera() {
        if (mCamera == null) {
            mCamera = getCameraInstance();
        }

        return mCamera;
    }

    public Camera getCamera(String cameraType) {
        if (mCamera == null) {
            mCamera = getCameraInstance(cameraType);
        }

        return mCamera;
    }

    public void releaseCamera() {
        if (mCamera != null) {
            mCamera.release(); // release the camera for other applications
            mCamera = null;
        }
    }

    public Camera getCameraInstance() {
        return getCameraInstance(-1);
    }

    public Camera getCameraInstance(String cameraType) {
        mCameraId = -1;

        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();

        for (int cameraId = 0; cameraId < Camera.getNumberOfCameras(); cameraId++) {
            Camera.getCameraInfo(cameraId, cameraInfo);
            if (cameraType.equals("back") && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mCameraId = cameraId;
                break;
            }
            if (cameraType.equals("front") && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCameraId = cameraId;
                break;
            }
        }

        return getCameraInstance(mCameraId);
    }

    // A safe way to get an instance of the Camera object.
    public Camera getCameraInstance(int cameraId) {
        Camera c = null;
        try {
            if (cameraId == -1) {
                c = Camera.open(); // attempt to get a Camera instance
            } else {
                c = Camera.open(cameraId); // attempt to get a Camera instance
            }
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    public boolean isFlashSupported(Camera camera) {
        // Credits: Top answer at http://stackoverflow.com/a/19599365/868173
        if (camera != null) {
            Camera.Parameters parameters = camera.getParameters();

            if (parameters.getFlashMode() == null) {
                return false;
            }

            List<String> supportedFlashModes = parameters.getSupportedFlashModes();
            if (supportedFlashModes == null || supportedFlashModes.isEmpty() || supportedFlashModes.size() == 1 && supportedFlashModes.get(0).equals(Camera.Parameters.FLASH_MODE_OFF)) {
                return false;
            }
        } else {
            return false;
        }

        return true;
    }

    public void setFlash(int state) {
        if (mCamera == null)
            return;

        try {
            Camera.Parameters mParam = mCamera.getParameters();
            if (state == FLASH_TORCH)
                mParam.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            else
                mParam.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);

            setParameters(mParam);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Copied from sunmi scanner demo
    private int setParameters(Camera.Parameters params) {

        byte n = 0;

        if (mCamera == null) return -1;
        if (params == null) return -1;

        while (n < 5) {
            try {
                mCamera.setParameters(params);
            } catch (Exception e) {
                n++;

                try {
                    sleep(50);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }

                continue;
            }

            break;
        }

        if (n >= 5) return -2;

        return 0;
    }
}


package com.solderchef.tof;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.util.Log;
import android.util.Range;
import android.util.SizeF;
import android.view.Surface;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class Camera extends CameraDevice.StateCallback {
            // Samsung vendor tag keys (example, may need adjustment for actual device)
            private static final String SAMSUNG_DEPTH_FILTER_TYPE = "samsung.android.depth.filterType";

            /**
             * Log Samsung vendor tag values for depth tuning from a CaptureResult.
             */
            private void logSamsungDepthVendorTags(CaptureRequest request, CameraCaptureSession session) {
                // This is a stub for demonstration. Actual vendor tag access may require reflection or custom APIs.
                // In a real implementation, you would use CaptureResult.Key and session callbacks.
                // Here, we just log the intent.
                Log.i(TAG, "Attempting to access Samsung vendor tags for depth tuning (filterType, etc.)");
            }
        /**
         * Enumerate and log all camera IDs that support DEPTH_OUTPUT.
         */
        public void logAllDepthOutputCameras() {
            try {
                for (String cameraId : cameraManager.getCameraIdList()) {
                    CameraCharacteristics chars = cameraManager.getCameraCharacteristics(cameraId);
                    int[] caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                    boolean hasDepth = false;
                    if (caps != null) {
                        for (int cap : caps) {
                            if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) {
                                hasDepth = true;
                                break;
                            }
                        }
                    }
                    if (hasDepth) {
                        Integer lensFacing = chars.get(CameraCharacteristics.LENS_FACING);
                        String facing = lensFacing == null ? "UNKNOWN" :
                            (lensFacing == CameraCharacteristics.LENS_FACING_FRONT ? "FRONT" :
                            (lensFacing == CameraCharacteristics.LENS_FACING_BACK ? "BACK" : "EXTERNAL"));
                        Log.i(TAG, "Camera ID: " + cameraId + ", Facing: " + facing + " supports DEPTH_OUTPUT");
                    }
                }
            } catch (CameraAccessException e) {
                Log.e(TAG, "Error enumerating cameras: " + e.getMessage());
            }
        }
    private static final String TAG = Camera.class.getSimpleName();

    private static int FPS_MIN = 15;
    private static int FPS_MAX = 30;

    private Context context;
    private CameraManager cameraManager;
    private ImageReader previewReader;
    private CaptureRequest.Builder previewBuilder;
    private DepthFrameAvailableListener imageAvailableListener;

    public Camera(Context context, DepthFrameVisualizer depthFrameVisualizer) {
        this.context = context;
        cameraManager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        imageAvailableListener = new DepthFrameAvailableListener(depthFrameVisualizer);
        previewReader = ImageReader.newInstance(DepthFrameAvailableListener.WIDTH,
                DepthFrameAvailableListener.HEIGHT, ImageFormat.DEPTH16,2);
        previewReader.setOnImageAvailableListener(imageAvailableListener, null);
    }

    // Open the front depth camera and start sending frames
    public void openFrontDepthCamera() {
        final String cameraId = getFrontDepthCameraID();
        if (cameraId == null) {
            Log.e(TAG, "No suitable front depth camera found (cameraId is null)");
            return;
        }
        Log.i(TAG, "Opening front depth camera with ID: " + cameraId);
        openCamera(cameraId);
    }

    private String getFrontDepthCameraID() {
        try {
            for (String camera : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(camera);
                final int[] capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                boolean facingFront = chars.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT;
                boolean depthCapable = false;
                for (int capability : capabilities) {
                    boolean capable = capability == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT;
                    depthCapable = depthCapable || capable;
                }
                Log.i(TAG, "Camera ID: " + camera + ", facingFront=" + facingFront + ", depthCapable=" + depthCapable);
                if (depthCapable && facingFront) {
                    // Note that the sensor size is much larger than the available capture size
                    SizeF sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                    Log.i(TAG, "Sensor size: " + sensorSize);

                    // Since sensor size doesn't actually match capture size and because it is
                    // reporting an extremely wide aspect ratio, this FoV is bogus
                    float[] focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    if (focalLengths.length > 0) {
                        float focalLength = focalLengths[0];
                        double fov = 2 * Math.atan(sensorSize.getWidth() / (2 * focalLength));
                        Log.i(TAG, "Calculated FoV: " + fov);
                    }
                    return camera;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not initialize Camera Cache");
            e.printStackTrace();
        }
        return null;
    }

    private void openCamera(String cameraId) {
        if (cameraId == null) {
            Log.e(TAG, "cameraId was null. Aborting openCamera.");
            return;
        }
        try{
            int permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA);
            if(PackageManager.PERMISSION_GRANTED == permission) {
                cameraManager.openCamera(cameraId, this, null);
            }else{
                Log.e(TAG,"Permission not available to open camera");
            }
        }catch (CameraAccessException | IllegalStateException | SecurityException e){
            Log.e(TAG,"Opening Camera has an Exception " + e);
            e.printStackTrace();
        }
    }


    @Override
    public void onOpened(@NonNull CameraDevice camera) {
        try {
            previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0);
            Range<Integer> fpsRange = new Range<>(FPS_MIN, FPS_MAX);
            previewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            previewBuilder.addTarget(previewReader.getSurface());

            List<Surface> targetSurfaces = Arrays.asList(previewReader.getSurface());
            camera.createCaptureSession(targetSurfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            onCaptureSessionConfigured(session);
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG,"!!! Creating Capture Session failed due to internal error ");
                        }
                    }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void onCaptureSessionConfigured(@NonNull CameraCaptureSession session) {
        Log.i(TAG,"Capture Session created");
        previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            session.setRepeatingRequest(previewBuilder.build(), null, null);
            // Attempt to log Samsung vendor tags for depth tuning (if available)
            logSamsungDepthVendorTags(previewBuilder.build(), session);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onDisconnected(@NonNull CameraDevice camera) {

    }

    @Override
    public void onError(@NonNull CameraDevice camera, int error) {

    }
}

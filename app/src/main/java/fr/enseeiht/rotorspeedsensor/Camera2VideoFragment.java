/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.enseeiht.rotorspeedsensor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import fr.enseeiht.rotorspeedsensor.Calculator.RegionOfInterest;
import fr.enseeiht.rotorspeedsensor.Calculator.SpatialAnalyzer;
import fr.enseeiht.rotorspeedsensor.Calculator.SpeedCalculator;
import fr.enseeiht.rotorspeedsensor.Calculator.SpeedCalculatorCallback;
import fr.enseeiht.rotorspeedsensor.Processor.VideoAnalyzer;

@RequiresApi(api = Build.VERSION_CODES.M)
public class Camera2VideoFragment extends Fragment
        implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback {

    MediaCodec decoder = null;
    //CodecOutputSurface outputSurface = null;
    MediaExtractor extractor = null;
    private boolean isComputationStarted = false;

    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    private static final String TAG = "Camera2VideoFragment";
    private static final int REQUEST_VIDEO_PERMISSIONS = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    List<Surface> surfaces = new ArrayList<Surface>();

    /**
     * Camcorder Profile
     */
    private Range<Integer>[] availableFpsRange;

    private int maxFPS;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * Button to record video
     */
    private ImageButton mButtonVideo;

    /**
     * A reference to the opened {@link android.hardware.camera2.CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * A reference to the current {@link android.hardware.camera2.CameraCaptureSession} for
     * preview.
     */
    private CameraCaptureSession mPreviewSession;
    private CameraConstrainedHighSpeedCaptureSession mPreviewSessionHighSpeed;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * The {@link android.util.Size} of video recording.
     */
    private Size mVideoSize;

    /**
     * MediaRecorder
     */
    private MediaRecorder mMediaRecorder;

    /**
     * Whether the app is recording video now
     */
    private boolean mIsRecordingVideo;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            mCameraOpenCloseLock.release();
            if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };
    private Integer mSensorOrientation;
    private String mNextVideoAbsolutePath;
    private CaptureRequest.Builder mPreviewBuilder;

    private SpeedCalculator mCalculator;

    private TextView currentFPS, currentStatus, processedFrames;

    // ROI :
    private RegionOfInterest regionOfInterest;
    private RegionOfInterestView roiView;
    private float zoomInertiaCoefficientForROI = 0.0015f;
    private int amountOfTrackedPixelsPerLine = 3;
    private float moveInertiaCoefficientForROI = 0.0015f;

    public static Camera2VideoFragment newInstance() {
        return new Camera2VideoFragment();
    }

    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2_video, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        mButtonVideo = (ImageButton) view.findViewById(R.id.video);
        currentFPS = (TextView) view.findViewById(R.id.currentFPS);
        currentStatus = (TextView) view.findViewById(R.id.currentStatus);
        processedFrames = (TextView) view.findViewById(R.id.processedFrames);
        roiView = (RegionOfInterestView) view.findViewById(R.id.roiView);

        mButtonVideo.setOnClickListener(this);
        view.findViewById(R.id.info).setOnClickListener(this);

        // Set the region of interest.
        regionOfInterest = new RegionOfInterest(0.5f, 0.5f, 0.5f);

        // Initializer the region of interest view.
        roiView.initializeRegionOfInterestSurface(regionOfInterest);

        mTextureView.setOnTouchListener(new ScreenListener());
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.video: {
                if (mIsRecordingVideo) {
                    stopRecordingVideo();
                } else {
                    startRecordingVideo();
                }
                break;
            }
            case R.id.info: {
                Activity activity = getActivity();
                if (null != activity) {
                    new AlertDialog.Builder(activity)
                            .setMessage(R.string.intro_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
                break;
            }
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private boolean shouldShowRequestPermissionRationale(String[] permissions) {
        for (String permission : permissions) {
            if (FragmentCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Requests permissions needed for recording video.
     */
    private void requestVideoPermissions() {
        if (shouldShowRequestPermissionRationale(VIDEO_PERMISSIONS)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            FragmentCompat.requestPermissions(this, VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
            if (grantResults.length == VIDEO_PERMISSIONS.length) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        ErrorDialog.newInstance(getString(R.string.permission_request))
                                .show(getChildFragmentManager(), FRAGMENT_DIALOG);
                        break;
                    }
                }
            } else {
                ErrorDialog.newInstance(getString(R.string.permission_request))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    ImageReader.OnImageAvailableListener mImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.i("OnImageAvailableList", "Triggered.");
            Image image = reader.acquireNextImage();

            // Process the frame.
            mCalculator.processFrame(image.getPlanes()[0].getBuffer(), image.getWidth(), image.getHeight());

            image.close();
        }
    };

    /**
     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
     */
    @SuppressWarnings("MissingPermission")
    private void openCamera(int width, int height) {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            requestVideoPermissions();
            return;
        }
        final Activity activity = getActivity();
        if (null == activity || activity.isFinishing()) {
            return;
        }
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            Log.d(TAG, "tryAcquire");
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            String cameraId = manager.getCameraIdList()[0];

            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }

            Range<Integer>[] fpsRanges = map.getHighSpeedVideoFpsRanges();
            Range<Integer> highestRange = fpsRanges[fpsRanges.length - 1];
            Log.i("CameraCharacteristics", String.valueOf(highestRange));
            Size highSpeedVideoSize = map.getHighSpeedVideoSizesFor(highestRange)[0];
            Log.i("CameraCharacteristics", String.valueOf(highSpeedVideoSize));

            mVideoSize = chooseVideoSize(map.getHighSpeedVideoSizesFor(highestRange));
            mPreviewSize = mVideoSize; /* chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, mVideoSize); */

            Log.i("CameraCharacteristics", "Effective chosen video size: " + String.valueOf(mVideoSize) + ". Preview size: " + String.valueOf(mPreviewSize));

            // Select high speed FPS.
            availableFpsRange = map.getHighSpeedVideoFpsRangesFor(mVideoSize);
            maxFPS = 0;
            int minFPS;
            for (Range<Integer> r : fpsRanges) {
                if (maxFPS < r.getUpper()) {
                    maxFPS = r.getUpper();
                }
            }
            minFPS = maxFPS;
            for (Range<Integer> r : fpsRanges) {
                if (minFPS > r.getLower()) {
                    minFPS = r.getUpper();
                }
            }
            Log.i("FPS", "Selected high speed range: [" + minFPS + ", " + maxFPS + "].");
            currentFPS.setText(maxFPS + " FPS");

            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            configureTransform(width, height);
            mMediaRecorder = new MediaRecorder();
            manager.openCamera(cameraId, mStateCallback, null);

            mCalculator = new SpatialAnalyzer(mVideoSize.getHeight(), mVideoSize.getWidth(), 1, regionOfInterest, new SpeedCalculatorCallbackImp(), maxFPS, 0, 1000);
        } catch (CameraAccessException e) {
            Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            activity.finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            surfaces.clear();
            closePreviewSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mPreviewSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Activity activity = getActivity();
                            if (null != activity) {
                                Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();

            if(mIsRecordingVideo) {
                setUpCaptureRequestBuilder(mPreviewBuilder);
                List<CaptureRequest> mPreviewBuilderBurst = mPreviewSessionHighSpeed.createHighSpeedRequestList(mPreviewBuilder.build());
                mPreviewSessionHighSpeed.setRepeatingBurst(mPreviewBuilderBurst, null, mBackgroundHandler);
            } else {
                mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Finds the framerate-range with the highest capturing framerate, and the lowest
     * preview framerate.
     *
     * @param fpsRanges A list contains framerate ranges.
     * @return The best option available.
     */
    private Range<Integer> getHighestFpsRange(Range<Integer>[] fpsRanges) {
        Range<Integer> fpsRange = Range.create(fpsRanges[0].getLower(), fpsRanges[0].getUpper());
        for (Range<Integer> r : fpsRanges) {
            if (r.getUpper() > fpsRange.getUpper()) {
                fpsRange.extend(0, r.getUpper());
            }
        }

        for (Range<Integer> r : fpsRanges) {
            if (Objects.equals(r.getUpper(), fpsRange.getUpper())) {
                if (r.getLower() < fpsRange.getLower()) {
                    fpsRange.extend(r.getLower(), fpsRange.getUpper());
                }
            }
        }
        return fpsRange;
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        // Range<Integer> fpsRange = Range.create(240, 240);
        Range<Integer> fpsRange = getHighestFpsRange(availableFpsRange);
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
        builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void setUpMediaRecorder() throws IOException {

        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }
        // mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath(getActivity());
        }
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        mMediaRecorder.setVideoEncodingBitRate(20000000);
        mMediaRecorder.setVideoFrameRate(maxFPS);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        // mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        mMediaRecorder.prepare();
        Log.i("MediaRecorder", "Preparation finished.");
    }

    private String getVideoFilePath(Context context) {
        final File dir = context.getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis() + ".mp4";
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            mIsRecordingVideo = true;
            currentStatus.setText("Recording video...");
            surfaces.clear();
            closePreviewSession();
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            //List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            Log.i("FPS", "Actual AE_TARGET_FPS_RANGE: " + CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE.toString());
            Log.i("Preview surface", "Width: " + mPreviewSize.getWidth() + ", height: " + mPreviewSize.getHeight());
            Log.i("Media recorder surface", "Width: " + mVideoSize.getWidth() + ", height: " + mVideoSize.getHeight());

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice.createConstrainedHighSpeedCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    mPreviewSessionHighSpeed = (CameraConstrainedHighSpeedCaptureSession) mPreviewSession;
                    updatePreview();
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // UI
                            mButtonVideo.setImageResource(R.drawable.ic_stop);
                            mIsRecordingVideo = true;

                            // Start recording
                            mMediaRecorder.start();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }

    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    private void stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false;

        // Stop recording
        try {
            mPreviewSessionHighSpeed.stopRepeating();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        mButtonVideo.setImageResource(R.drawable.ic_play);
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        mMediaRecorder.release();

        Activity activity = getActivity();
        if (null != activity) {
            Toast.makeText(activity, "Video saved: " + mNextVideoAbsolutePath,
                    Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Video saved: " + mNextVideoAbsolutePath);
        }
        currentStatus.setText("Processing computation...");

        isComputationStarted = true;
        VideoAnalyzerThread videoAnalyzerThread =
                new VideoAnalyzerThread(mNextVideoAbsolutePath, mCalculator);

        // Launch video analyzer thread.
        new Thread(videoAnalyzerThread).start();

        mNextVideoAbsolutePath = null;
        startPreview();
    }

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private class VideoAnalyzerThread implements Runnable {
        private String path;
        private SpeedCalculator calculator;

        public VideoAnalyzerThread(String path, SpeedCalculator calculator) {
            this.path = path;
            this.calculator = calculator;
        }

        @Override
        public void run() {
            Log.i("videoAnalyzerThread", "Started with path: " + path);
            VideoAnalyzer videoAnalyzer = new VideoAnalyzer(this.path, this.calculator);
            videoAnalyzer.sendFramesToCalculator();
        }
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.permission_request)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent, VIDEO_PERMISSIONS,
                                    REQUEST_VIDEO_PERMISSIONS);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    parent.getActivity().finish();
                                }
                            })
                    .create();
        }

    }

    public static void runOnUiThread(Runnable runnable){
        final Handler UIHandler = new Handler(Looper.getMainLooper());
        UIHandler.post(runnable);
    }

    private class SpeedCalculatorCallbackImp implements SpeedCalculatorCallback {
        @Override
        public void callSpeed(final double speed) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    currentStatus.setText(String.format("%.2f", speed) + " r.p.m."); // Display the speed
                }
            });
        }

        @Override
        public void setStatus(final String status) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    currentStatus.setText(status); // Display the speed
                }
            });
        }

        @Override
        public void notifyAmountOfProcessedFrames(final int amountOfProcessedFrames, final int expectedFramesAmount) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    processedFrames.setText(amountOfProcessedFrames + "/" + expectedFramesAmount);
                }
            });
        }

        @Override
        public void callFps(final double fps) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    currentFPS.setText(Double.toString(fps) + " FPS");
                }
            });
        }

        @Override
        public void onProcessStarted() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mButtonVideo.setEnabled(false);
                }
            });
        }

        @Override
        public void onProcessStopped() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    currentStatus.setText(getActivity().getResources().getString(R.string.positionateROI));
                    mButtonVideo.setEnabled(true);
                }
            });
        }

        @Override
        public void onProcessCompleted() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    currentStatus.setText(getActivity().getResources().getString(R.string.positionateROI));
                    mButtonVideo.setEnabled(true);
                }
            });
        }
    }

    private class ScreenListener implements View.OnTouchListener {

        // Distance between 2 fingers :
        float distance;

        // position when use one finger :
        float xPos;
        float yPos;

        float xPosInitial;
        float yPosInitial;

        @Override
        public boolean onTouch(View v, MotionEvent event) {

            if (!isComputationStarted) {

                int action = event.getAction();
                int actionMasked = event.getActionMasked();

                // Move the ROI when moving on the screen with 1 finger
                if (event.getPointerCount() == 1) {

                    // The user move his finger -> perform a move of the ROI
                    if (action == MotionEvent.ACTION_MOVE) {

                        float newXPos = event.getX(0);
                        float newYPos = event.getY(0);
                        if (newXPos != xPos && newYPos != yPos) {
                            regionOfInterest.applyMove((newXPos - xPos) * moveInertiaCoefficientForROI, (newYPos - yPos) * moveInertiaCoefficientForROI);
                            roiView.invalidate();
                        }
                        xPos = newXPos;
                        yPos = newYPos;
                    }

                    // The user put a finger on the screen -> set the position
                    else if (actionMasked == MotionEvent.ACTION_DOWN) {
                        xPos = event.getX(0);
                        yPos = event.getY(0);
                        xPosInitial = xPos;
                        yPosInitial = yPos;
                    }

                    // The user remove his finger from the screen
                    //  -> Auto focus if the finger not moved from the beginning
                    else if (actionMasked == MotionEvent.ACTION_UP && xPosInitial == event.getX(0) && yPosInitial == event.getY(0)) {
                        // Auto focus on the ROI if the user don't move his finger :
                    }
                }
                // Perform a move if 2 moving fingers are detected on the screen :
                else if (event.getPointerCount() == 2) {

                    // The user move his 2 fingers -> perform a zoom
                    if (action == MotionEvent.ACTION_MOVE) {

                        float newDistance = getSquareDistance(event);
                        if (newDistance < distance) {
                            regionOfInterest.applyZoomMore((distance - newDistance) * zoomInertiaCoefficientForROI);
                            roiView.invalidate();
                        } else if (newDistance > distance) {
                            regionOfInterest.applyZoomLess((newDistance - distance) * zoomInertiaCoefficientForROI);
                            roiView.invalidate();
                        }
                        distance = newDistance;
                    }

                    // The user add an other finger : update the position of the pointer
                    else if (actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                        // Need to update the position of the pointer
                        if (action == actionMasked) {
                            xPos = event.getX(0);
                            yPos = event.getY(0);
                        }
                        distance = getSquareDistance(event);
                    }

                    // The user remove an finger : update the position of the pointer
                    else if (actionMasked == MotionEvent.ACTION_POINTER_UP) {
                        // Need to update the position of the pointer
                        if (action == actionMasked) {
                            xPos = event.getX(1);
                            yPos = event.getY(1);
                        } else {
                            xPos = event.getX(0);
                            yPos = event.getY(0);
                        }
                    }
                }
            }
            return true;
        }

        private float getSquareDistance(MotionEvent event) {
            float x = event.getX(0) - event.getX(1);
            float y = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(x * x + y * y);
        }
    }



}

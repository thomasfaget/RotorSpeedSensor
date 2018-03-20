package fr.enseeiht.rotorspeedsensor;


import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import fr.enseeiht.rotorspeedsensor.Calculator.RegionOfInterest;
import fr.enseeiht.rotorspeedsensor.Calculator.SpatialAnalyzer;
import fr.enseeiht.rotorspeedsensor.Calculator.SpeedCalculator;
import fr.enseeiht.rotorspeedsensor.Calculator.SpeedCalculatorCallback;
import fr.enseeiht.rotorspeedsensor.Processor.FramesProcessor;
import fr.enseeiht.rotorspeedsensor.Processor.FramesProcessorOnPreviewCallback;


/**
 * @author Matthieu Le Boucher <matt.leboucher@gmail.com>
 */

public class CameraVideoFragment extends Fragment implements SpeedCalculatorCallback, VideoFragment {

    private Camera camera; // The camera

    // Tags :
    public static final String CAMERA_INFO = "CAMERA_INFO";
    public static final String SPEED_COMPUTATION = "SPEED_COMPUTATION";
    public static final String ERROR = "ERROR";
    public static final String PHOTO_TAKEN = "PHOTO_TAKEN";
    public static final String UPDATE_SPEED = "UPDATE_SPEED";
    public static final String DEBUG = "COUCOU_DEBUG";

    private boolean isComputationStarted = false;

    // Views :
    private Button startButton;
    private Button helpButton;
    private Button debugButton;
    private FrameLayout videoView;
    private CameraPreview cameraPreview;
    private TextView speedField;
    private RegionOfInterestView roiView;
    private TextView fpsField;
    private TextView frameComputedField;

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // SpeedCalculator & FramesProcessor
    private FramesProcessor framesProcessor;
    private SpeedCalculator speedCalculator;

    // Parameters :
    private int pictureWidth;
    private int pictureHeight;
    private int fps = 30; // camera fps
    private long initialDelaySpeedComputation = 1000; // The delay before the first speed computation (millisecond)
    private long periodBetweenComputation = 1000; // the delay between each speed computation (millisecond)
    private double sampleTime = 20.0; // in seconds
    private int nbBuffers = 3;
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // ROI :
    private RegionOfInterest regionOfInterest;
    private float zoomInertiaCoefficientForROI = 0.0015f;
    private int amountOfTrackedPixelsPerLine = 3;
    private float moveInertiaCoefficientForROI = 0.0015f;

    private StatsFragment statsFragment;

    public static CameraVideoFragment newInstance() {
        return new CameraVideoFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera_video, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Get the Components :
        startButton = (Button) view.findViewById(R.id.startButton);
        helpButton = (Button) view.findViewById(R.id.helpButton);
        debugButton = (Button) view.findViewById(R.id.debugButton);
        videoView = (FrameLayout) view.findViewById(R.id.videoView);
        speedField = (TextView) view.findViewById(R.id.speedField);
        roiView = (RegionOfInterestView) view.findViewById(R.id.roiView);
        fpsField = (TextView) view.findViewById(R.id.fpsField);
        frameComputedField = (TextView) view.findViewById(R.id.frameComputedField);

        // Start recording and computing when start is clicked
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start computation :
                if (!isComputationStarted) {
                    startVideo();
                    isComputationStarted = true;
                }
                // Stop computation :
                else {
                    stopVideo();
                    isComputationStarted = false;
                }
            }
        });

        // Open a tutorial when the button Help is pressed :
        helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((CameraActivity) getActivity()).getPager().setCurrentItem(2);
            }
        });

        // Open the debug to show data :
        debugButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((CameraActivity) getActivity()).getPager().setCurrentItem(1);
            }
        });

        // Set the region of interest.
        regionOfInterest = new RegionOfInterest(0.5f, 0.5f, 0.5f);

        // Init the region of interest view :
        roiView.initializeRegionOfInterestSurface(regionOfInterest);

        // Add the zoom/move/auto-focus
        videoView.setOnTouchListener(new ScreenListener());

    }

    @Override
    public void onResume() {
        super.onResume();

        // Set-up videoView
        camera = getFacingCamera();
        cameraPreview = new CameraPreview(this.getActivity(), camera);
        videoView.addView(cameraPreview);

        // Start the camera
        camera.startPreview();
        Log.i(CAMERA_INFO, "Camera is starting !");

        // Print the fps on the screen :
        String field = String.valueOf(fps) + " fps";
        fpsField.setText(field);

        // Print the number of frame on the screen :
        field = "0/" + (int) (((double) fps) * sampleTime);
        frameComputedField.setText(field);

        // Build the SpeedCalculator :
        speedCalculator = new SpatialAnalyzer(pictureHeight, pictureWidth, fps, sampleTime, amountOfTrackedPixelsPerLine, regionOfInterest, this, initialDelaySpeedComputation, periodBetweenComputation);
        speedCalculator.setDataListener(statsFragment);

        // Build the FrameProcessor :
        framesProcessor = new FramesProcessorOnPreviewCallback(speedCalculator, camera, pictureWidth, pictureHeight, nbBuffers);

    }

    @Override
    public void onPause() {
        super.onPause();

        // Interrupt the computation if processing
        if (isComputationStarted) {
            stopVideo();
            isComputationStarted = false;
        }

        // Stop and remove the Camera Preview :
        cameraPreview.getHolder().removeCallback(cameraPreview);
        cameraPreview = null;

        // Stop and remove the camera
        camera.stopPreview();
        camera.release();
        camera = null;

        Log.i(CAMERA_INFO, "Camera was stopped !");
    }

    /**
     * Get the facing camera
     * Set also the maximum fps possible for capture.
     */
    private Camera getFacingCamera() {
        Camera camera = null;
        try {
            // Get the facing back camera :
            camera = Camera.open();
            Camera.Parameters parameters = camera.getParameters();

            // Set the Max stable fps available :
            boolean isStableFPSOnDevice = false;
            int cameraFps = 0;
            List<int[]> fpsRangeList = parameters.getSupportedPreviewFpsRange(); // List of all fps range
            for (int[] fpsRange : fpsRangeList) {
                // Detect the Fix Range, and get the max Range :
                if (fpsRange[0] == fpsRange[1] && fpsRange[0] > cameraFps) {
                    cameraFps = fpsRange[0];
                    isStableFPSOnDevice = true;
                }
            }

            // If not stable fps -> Try to find the more accurate FPS on the device
            if (!isStableFPSOnDevice) {
                List<Integer> fpsList = parameters.getSupportedPreviewFrameRates();
                for (int fpsAux : fpsList) {
                    if (fpsAux > cameraFps) {
                        cameraFps = fpsAux;
                    }
                }
            }

            // Set the fps :
            if (isStableFPSOnDevice) {
                fps = cameraFps / 1000;
                parameters.setPreviewFpsRange(fps, fps);
            }
            else {
                fps = cameraFps;
                parameters.setPreviewFrameRate(fps);
            }
            Log.i(CAMERA_INFO, "The camera was set at " + fps + " fps.");

            List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();

            for (Camera.Size size: sizes) {
                Log.d(DEBUG, "Size avalaible : " + size.width + " x " + size.height);
            }

            Camera.Size previewSize = parameters.getPreferredPreviewSizeForVideo();
            pictureWidth = previewSize.width;
            pictureHeight = previewSize.height;
            Log.i(CAMERA_INFO, "Picture dimensions: [" + pictureWidth + "x" + pictureHeight + "].");
            // Set picture size :
            parameters.setPictureSize(pictureWidth, pictureHeight);

            // Set Image format :
            parameters.setPictureFormat(ImageFormat.YV12);

            // Set the parameters to the camera :
            camera.setParameters(parameters);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return camera;
    }

    /**
     * Start computation and update interface
     */
    private void startVideo() {

        // Update the interface
        startButton.setBackgroundResource(R.drawable.stop_button);
        startButton.setText(R.string.stop);

        // Set the exposition of the camera :
        Camera.Parameters parameters = camera.getParameters();
        parameters.setRecordingHint(true);
        if (parameters.isAutoExposureLockSupported()) {
            parameters.setAutoExposureLock(true);
        }
        camera.setParameters(parameters);

        // Start the computation :
        framesProcessor.start();
    }

    /**
     * Stop computation and update interface
     */
    private void stopVideo() {

        // Update the interface
        startButton.setBackgroundResource(R.drawable.start_button);
        startButton.setText(R.string.start_video);


        // Set the exposition of the camera :
        Camera.Parameters parameters = camera.getParameters();
        parameters.setRecordingHint(false);
        if (parameters.isAutoExposureLockSupported()) {
            parameters.setAutoExposureLock(false);
        }
        camera.setParameters(parameters);

        // Interrupt the program :
        framesProcessor.stop();
    }

    @Override
    public void callSpeed(final double speed) {

        // Need the main Thread to update UI :
        this.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String field = String.format(Locale.ENGLISH, "%.2f", speed) + " r.p.m.";
                speedField.setText(field); // Display the speed
            }
        });

    }

    @Override
    public void setStatus(String status) {

    }

    @Override
    public void notifyAmountOfProcessedFrames(int amountOfProcessedFrames, int expectedFramesAmount) {

    }

    @Override
    public void callFps(final double fps) {

        // Need the main Thread to update UI :
        this.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String field = String.format(Locale.ENGLISH, "%.1f", fps) + " fps";
                fpsField.setText(field); // Display the speed
            }
        });
    }

    @Override
    public void onProcessStarted() {}

    @Override
    public void onProcessStopped() {}

    @Override
    public void onProcessCompleted() {}

    @Override
    public SpeedCalculator getCalculator() {
        return speedCalculator;
    }

    @Override
    public void setDataListener(StatsFragment statsFragment) {
        this.statsFragment = statsFragment;

        if(speedCalculator != null)
            speedCalculator.setDataListener(statsFragment);
    }

    /**
     * A Touch listener to handle interaction with the Camera Prewiew, can handle :
     * - zooming the ROI (when moving using 2 fingers)
     * - moving the ROI (when moving using 2 fingers)
     * - Auto focus of the camera (when just click on the screen)
     */
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
                        camera.autoFocus(null);
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
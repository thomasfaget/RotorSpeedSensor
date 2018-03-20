package fr.enseeiht.rotorspeedsensor.Calculator;

import android.graphics.Bitmap;
import android.util.Log;
import android.util.SparseArray;

import org.jtransforms.fft.DoubleFFT_1D;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import fr.enseeiht.rotorspeedsensor.CameraVideoFragment;
import fr.enseeiht.rotorspeedsensor.Helper.CoordinatesHelper;
import fr.enseeiht.rotorspeedsensor.Helper.MathHelper;
import fr.enseeiht.rotorspeedsensor.StatsFragment;

import static fr.enseeiht.rotorspeedsensor.CameraVideoFragment.DEBUG;

/**
 * This process computes the speed of the motor with the preview images taken by the camera.
 * Method :
 *      1 - At the start :  Compute a grid of tracked pixels in the Region of Interest (the Tracked Pixels)
 *      2 - Every frame :   Acquire an image with processFrame, and compute the gray level on the Tracked Pixels
 *      3 - Every second :  Compute the motor speed with all the data acquired with the Tracked Pixels
 *
 * @author Thomas Faget
 * @author Matthieu Le Boucher <matt.leboucher@gmail.com>
 */
public class SpatialAnalyzer implements SpeedCalculator {
    private StatsFragment statsFragment;

    private double maximalFrameRate = 120;
    private int amountOfProcessedFrames = 0;
    private boolean isProcessingLive = true;
    private int expectedAmountOfFrames;

    private int pictureHeight;
    private int pictureWidth;
    private double cameraFPS;

    private SpeedCalculatorCallback speedCalculatorCallback;

    // Tracked pixels :
    private RegionOfInterest regionOfInterest;
    private int amountOfTrackedPixelsPerLine;
    private int amountOfTrackedPixels;
    private ArrayList<TrackedPixel> trackedPixels; // Tracked pixels

    // Speed Computation process :
    private long initialDelay;
    private long period;
    private double sampleTime;
    private ScheduledThreadPoolExecutor speedProcess; // The process which compute the motor speed at fixed rate

    /**
     * @param pictureHeight                 the height of a picture
     * @param pictureWidth                  the width of a picture
     * @param cameraFPS                     the theoretical FPS set for the camera
     * @param sampleTime                    the approximate sample time (second)
     * @param amountOfTrackedPixelsPerLine  the amount of pixels per line and column that should be tracked within the ROI.
     * @param roi                           the region of interest
     * @param callback                      the speedCalculatorCallback used to inform that the speed was computed
     * @param initialDelay                  the initial delay before the first speed computation (millisecond)
     * @param period                        the delay between each speed computation (millisecond)
     */
    public SpatialAnalyzer(int pictureHeight, int pictureWidth, int cameraFPS, double sampleTime, int amountOfTrackedPixelsPerLine, RegionOfInterest roi, SpeedCalculatorCallback callback, long initialDelay, long period) {
        this.pictureHeight = pictureHeight;
        this.pictureWidth = pictureWidth;
        this.cameraFPS = cameraFPS;

        this.speedCalculatorCallback = callback;

        this.regionOfInterest = roi;

        // Set the approximate number of tracked pixels (will be modified to fit the ROI at best.)
        this.amountOfTrackedPixelsPerLine = amountOfTrackedPixelsPerLine;
        this.amountOfTrackedPixels = amountOfTrackedPixelsPerLine*amountOfTrackedPixelsPerLine;

        this.initialDelay = initialDelay;
        this.period = period;
        this.sampleTime = sampleTime;
    }

    // TODO doc
    public SpatialAnalyzer(int pictureHeight, int pictureWidth, int amountOfTrackedPixels, RegionOfInterest regionOfInterest, SpeedCalculatorCallback callback, double maximalFrameRate, long initialDelay, long period) {
        this.pictureHeight = pictureHeight;
        this.pictureWidth = pictureWidth;
        this.regionOfInterest = regionOfInterest;
        this.trackedPixels = new ArrayList<>();
        this.maximalFrameRate = maximalFrameRate;
        this.amountOfProcessedFrames = 0;

        this.speedCalculatorCallback = callback;

        // Set the approximate number of tracked pixels (will be modified to fit the ROI at best.)
        this.amountOfTrackedPixels = amountOfTrackedPixels;
        this.amountOfTrackedPixelsPerLine = amountOfTrackedPixels;
        this.initialDelay = initialDelay;
        this.period = period;
    }

    /** Start all the processes to compute speed
     */
    @Override
    public void startComputation() {

        Log.i(CameraVideoFragment.SPEED_COMPUTATION, "Speed Computation is starting !");

        speedCalculatorCallback.onProcessStarted();
        speedCalculatorCallback.setStatus(isProcessingLive ? "Starting processing (live)..."
                : "Starting processing (postponed)...");
        speedCalculatorCallback.notifyAmountOfProcessedFrames(0,
                isProcessingLive ? 0 : expectedAmountOfFrames);

        // Compute the best grid corresponding to that number.
        computeTrackingGrid();

        // Use a ScheduledThreadPoolExecutor to compute the speed at fixed rate
        this.speedProcess = new ScheduledThreadPoolExecutor(1);
        speedProcess.scheduleAtFixedRate(new SpeedComputationCore(), initialDelay, period, TimeUnit.MILLISECONDS);
    }

    /** Stop all the process
     */
    @Override
    public void stopComputation() {
        speedProcess.shutdownNow();

        clearData();

        speedCalculatorCallback.onProcessStopped();

        speedCalculatorCallback.setStatus("Processing finished.");

        Log.i(CameraVideoFragment.SPEED_COMPUTATION, "Speed Computation was stopped !");
    }

    /** Reset the data : the trackedPixels
     */
    @Override
    public void clearData() {
        trackedPixels = null;

        Log.i(CameraVideoFragment.SPEED_COMPUTATION, "Data cleared !");
    }

    /** The main process used to compute the motor speed at fixed rate
     */
    private class SpeedComputationCore implements Runnable {

        // A list to save all the average computed each computation of the speed
        private List<Double> fpsList;
        private List<Double> speedHistory;

        // The index of the Tracked pixels, used to compute an average fps
        private int currentIndex;
        private int oldIndex;

        private boolean isTrackedPixelFull;

        SpeedComputationCore() {
            this.fpsList = new ArrayList<>();
            this.currentIndex = -1;
            this.oldIndex = -1;
            this.isTrackedPixelFull = false;
            this.speedHistory = new ArrayList<>();
        }

        @Override
        public void run() {
            try {
                List<Double> maxFftList = new ArrayList<>(); // The max of the fft signal for all the tracked pixel

                int nbValues = trackedPixels.get(0).nbValues();

                Log.i(CameraVideoFragment.SPEED_COMPUTATION, "[RUN] Tracked pixels: " + amountOfTrackedPixels + ", values: " + nbValues);

                // Update the index :
                oldIndex = currentIndex;
                currentIndex = trackedPixels.get(0).getCurrentIndex();

                // Update the fps list :
                updateFpsList();

                // compute the current fps :
                double fps = isProcessingLive ? MathHelper.mean(fpsList) : cameraFPS;

                // Get All the tracked pixels and allocate the memory for using fft :
                double [][] fftList = new double[amountOfTrackedPixels][2*nbValues];
                for (int i = 0; i < amountOfTrackedPixels; i++) {
                    trackedPixels.get(i).getValuesCopy(fftList[i]);
                }

                // Perform the analysis over tracked pixels values.
                for (int i = 0; i < amountOfTrackedPixels; i++) {

                    double[] fft = fftList[i];

                    // Perform a pre-analysis : get all the max in the signal try to compute a first speed :
                    SparseArray<Double> peakSignal = MathHelper.findLocalMaximaWithIndices(fft); // Find all the max
                    MathHelper.keepAboveThreshold(0.3, peakSignal); // Keep only the higher max

                    // Get a first approximation of the frequency :
                    double averageDistanceBetweenPeak = MathHelper.averageDistanceBetweenValues(peakSignal);
                    double approximateFundamentalOrder = 2 * ((double) nbValues) / (averageDistanceBetweenPeak);


                    // Log.i("Calculator", "Average fundamental order: " + approximateFundamentalOrder + "," +
                    //        " corresponding frequency: " + (approximateFundamentalOrder * fps / nbValues));

                    // Perform Fourier's transform
                    //MathHelper.applyHammingWindow(fft);
                    DoubleFFT_1D fftDo = new DoubleFFT_1D(nbValues);
                    fftDo.realForwardFull(fft);

                    //MathHelper.applyHammingWindowFrequencyDomain(fft);

                    // Convert to abs values :
                    MathHelper.convertToAbsoluteValues(fft);

                    // Research the fundamental frequency in the Fourier signal and add to the list:
                    double percentageInterval = 0.5;
                    int maxFft = MathHelper.findMaximumAroundApproximateValue(fft, (int) approximateFundamentalOrder, percentageInterval);
                    maxFftList.add((double) (maxFft) / 2);

                    if(i == 0) {
                        int borneMin = Math.max(0, (int) ((1-percentageInterval)*(approximateFundamentalOrder)));
                        int borneMax = Math.min(fft.length, (int) ((1+percentageInterval)*(approximateFundamentalOrder)));
                        statsFragment.sendFundamentalResearchInterval(borneMin, borneMax);
                        statsFragment.sendApproximateFundamentalOrder((int) approximateFundamentalOrder);
                        statsFragment.sendSpectrumData(fft);
                        statsFragment.sendClosestFrequencyOrderFound(maxFft);
                    }
                }

                // Compute the final speed (the mean of the max) :
                double speed = MathHelper.median(maxFftList) * fps * 60.0 / ((double) nbValues);

                Log.i(CameraVideoFragment.SPEED_COMPUTATION, "[RUN] Speed: " + String.valueOf(speed) + " r.p.m.");

                // Call the callback to notify FPS and speed changes.

                speedHistory.add(speed);
                // The speed should be changed only if the new one is relatively close to the
                // majority of the previous computed speeds. To do so, we use the standard
                // deviation of the computed speeds list.
//                if (Math.abs(MathHelper.mean(speedHistory) - speed)
//                        <= MathHelper.standardDeviation(speedHistory))
//                    speedCalculatorCallback.callSpeed(speed);
//
                speedCalculatorCallback.callFps(fps);

                Log.d(DEBUG, String.valueOf(speedHistory));

                double meanSpeed = MathHelper.meanInsideStandardDeviation(speedHistory);
                speedCalculatorCallback.callSpeed(meanSpeed);
            }
            // TODO replace it by Log.e when it will works
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        /** Update the fpsList with adding a new fps in the list and remove the oldest if necessary.
         */
        private void updateFpsList() {

            double fps;

            // Add a fps in the list
            if (currentIndex < oldIndex) {
                int size = trackedPixels.get(0).nbValues();
                fps = 1000.0 * ((double) (size + currentIndex - oldIndex)) / period;
                isTrackedPixelFull = true;
            } else {
                fps = 1000.0 * ((double) (currentIndex - oldIndex)) / period;
            }
            this.fpsList.add(fps);

            // Remove if necessary :
            if (isTrackedPixelFull) {
                fpsList.remove(0);
            }
        }
    }

    /** Process a frame dimensions width * height stored in the provided buffer.
     *
     * @param buffer A buffer of bytes.
     * @param width The width of the frame.
     * @param height The height of the frame.
     */
    public void processFrame(byte[] buffer, int width, int height) {
        Log.i("Quick", "Received frame.");

        int value = 0;
        // Get the tracked pixels on the image and add them in the tracked pixels list:
        for (TrackedPixel trackedPixel : trackedPixels) {
            value = buffer[trackedPixel.getX() + width * (trackedPixel.getY() - 1)] & 0xff;
            trackedPixel.pushValue(value);

        }

        notifyOnProcessedFrame();
        statsFragment.sendData(value);
    }

    /** Process a frame dimensions width * height stored in the provided buffer.
     *
     * @param buffer A buffer of bytes.
     * @param width The width of the frame.
     * @param height The height of the frame.
     */
    public void processFrame(ByteBuffer buffer, int width, int height) {
        // Log.i(CameraVideoFragment.SPEED_COMPUTATION, "Received new frame of width: " + width + ", height: " + height);
        DoubleBuffer luminosityBuffer = buffer.asDoubleBuffer();

        // Log.i(CameraVideoFragment.SPEED_COMPUTATION, "Luminosity buffer size : " + luminosityBuffer);

        for (TrackedPixel trackedPixel : trackedPixels)
            trackedPixel.pushValue(luminosityBuffer.get(
                    CoordinatesHelper.cartesianToOneDimension(
                            regionOfInterest.getWidth(width), trackedPixel.getX(), trackedPixel.getY()
                    )
            ));

        notifyOnProcessedFrame();
    }


    public void processFrame(Bitmap frame) {
        Log.i("Calculator", "Received a new Bitmap frame (" + amountOfProcessedFrames + ").");

        /*int R, G, B;

        for (TrackedPixel trackedPixel : trackedPixels) {
            int argb = frame.getPixel(trackedPixel.getX(), trackedPixel.getY());
            R = (argb & 0xff0000) >> 16;
            G = (argb & 0xff00) >> 8;
            B = (argb & 0xff);

            // Push the Y value to the tracked pixel's values.
            trackedPixel.pushValue(((66 * R + 129 * G +  25 * B + 128) >> 8) + 16);
        }

        notifyOnProcessedFrame();*/
    }

    public void notifyOnProcessedFrame() {
        amountOfProcessedFrames++;
        speedCalculatorCallback.notifyAmountOfProcessedFrames(amountOfProcessedFrames,
                isProcessingLive ? amountOfProcessedFrames : expectedAmountOfFrames);
    }
    /** Set the bound of the region of interest and compute the tracking grid of pixels.
     */
    private void computeTrackingGrid() {
        // Size of the ROI in the context of the picture :
        int widthRoi = regionOfInterest.getWidth(pictureWidth);
        int heightRoi = regionOfInterest.getHeight(pictureHeight);

        // Position of the ROI in the context of the picture :
        int horizontalOffset = regionOfInterest.getHorizontalLowerBound(pictureWidth);
        int verticalOffset = regionOfInterest.getVerticalLowerBound(pictureHeight);

        // Create the tracked pixels :
        this.trackedPixels = new ArrayList<>();
        for (int i = 1; i <= amountOfTrackedPixelsPerLine; i++) {
            for (int j = 1; j <= amountOfTrackedPixelsPerLine; j++) {
                trackedPixels.add(new TrackedPixel(
                        horizontalOffset + (i*widthRoi)/(amountOfTrackedPixelsPerLine+1),
                        verticalOffset + (j*heightRoi)/(amountOfTrackedPixelsPerLine+1),
                        sampleTime,
                        cameraFPS
                ));
            }
        }
    }

    /** Tracked can store the values of the gray level for a particular pixel in the image.
     * The amount of values saved is fixed, and is determined in the Constructor (as sampleTime*frameRate).
     * The values are pushed through a sliding-window. This means that, during pushing values,
     *  first saved values are overwrite if there is no empty place fir this values.
     */
    private class TrackedPixel {

        private boolean isArrayFull;
        private int x, y, currentIndex, arraySize;
        private double[] values;

        TrackedPixel(int x, int y, double sampleTime, double frameRate) {

            this.isArrayFull = false;
            this.x = x;
            this.y = y;
            this.currentIndex = 0;
            this.arraySize = (int) Math.ceil(sampleTime * frameRate);
            this.values = new double[arraySize];

        }

        /** Get the X coordinate in the frame
         */
        int getX() {
            return x;
        }

        /** Get the Y coordinate in the frame
         */
        int getY() {
            return y;
        }

        /** Get all the values, as stored in the tracked pixels
         */
        double[] getValues() {
            return values;
        }

        /** Return the current index of writing
         */
        int getCurrentIndex() {
            return currentIndex;
        }

        /** Return a copy of the array, with considering the sliding-windows used for the pixels
         * The results array needs to be initialized before ! (results = new double[...]).
         *
         * @param results the result array used to put the values in
         */
        void getValuesCopy(double[] results) {

            // The array is not fulled -> return only the available data ;
            if (!isArrayFull) {
                System.arraycopy(values, 0, results, 0, currentIndex);
            }
            // The array is fulled -> return all the values with respecting the order :
            else {
                // Copy first values (after current index)
                for (int i = 0; i < arraySize - currentIndex - 1; i++) {
                    results[i] = values[currentIndex+i+1];
                }
                // Copy last values (before current index)
                System.arraycopy(values, 0, results, arraySize - currentIndex - 1, currentIndex + 1);
            }
        }

        /** Add a value in the Tracked pixels
         */
        void pushValue(double value) {
            this.values[currentIndex] = value;
            currentIndex++;
            // Loop if the array is fulled
            if (currentIndex == arraySize) {
                isArrayFull = true;
                currentIndex = 0;
            }
        }

        /** Get the number of values in the Tracked Pixel :
         */
        int nbValues() {
            return isArrayFull ? arraySize : currentIndex;
        }
    }

    /** Set the sample time, i.e. the time needed to acquire a correct amount of data
     *
     * @param sampleTime the sample time (in seconds)
     */
    public void setSampleTime(double sampleTime) {
        this.sampleTime = sampleTime;
    }

    /** Set the theoretical fps of the camera
     *
     * @param cameraFPS theoretical fps
     */
    public void setCameraFPS(double cameraFPS) {
        this.cameraFPS = cameraFPS;
    }

    public void postponeProcessing(int expectedFramesAmount) {
        this.isProcessingLive = false;
        this.expectedAmountOfFrames = expectedFramesAmount;
    }

    @Override
    public void setDataListener(StatsFragment statsFragment) {
        Log.i("Quick", "Data listener set in calculator.");
        this.statsFragment = statsFragment;
    }
}

package fr.enseeiht.rotorspeedsensor.Processor;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.util.Log;

import java.io.File;

import fr.enseeiht.rotorspeedsensor.Calculator.SpeedCalculator;

/**
 * @author : Matthieu Le Boucher
 */

public class VideoAnalyzer implements FramesProcessor {
    private String filename;
    private File file;
    private MediaMetadataRetriever retriever;

    private SpeedCalculator calculator;

    private long duration;
    private double fps;

    public static String TAG = "VideoAnalyzer";

    public VideoAnalyzer(String filename, SpeedCalculator calculator) {
        this.retriever = new MediaMetadataRetriever();
        this.filename = filename;
        file = new File(filename);
        this.calculator = calculator;

        retriever.setDataSource(file.getAbsolutePath());

        Log.i(TAG, "Constructed.");
    }

    public void sendFramesToCalculator() {
        this.retrieveVideoMetadata();

        // Compute time step (microseconds).
        long timeStep = (long) (1000000 / this.getFps());

        int amountOfFrames = (int) Math.ceil(this.getDuration() * 1000 / timeStep);

        calculator.setSampleTime((double) this.getDuration() / 1000.);
        calculator.setCameraFPS(this.getFps());
        calculator.postponeProcessing(amountOfFrames);

        Log.i(TAG, "TimeStep: " + timeStep + ", amountOfFrames: " + amountOfFrames);

        for (int i = 0; i < amountOfFrames; i++) {
            // Send Bitmap frame to the calculator.
            Log.i("Calculator", "Start a new Bitmap frame (" + i + ").");

            Bitmap test = retriever.getFrameAtTime(i * timeStep,
                    MediaMetadataRetriever.OPTION_CLOSEST);
            /*this.calculator.processFrame(retriever.getFrameAtTime(i * timeStep,
                            MediaMetadataRetriever.OPTION_CLOSEST));*/

            Log.i("Calculator", "End a new Bitmap frame (" + i + ").");

        }

        retriever.release();

        this.calculator.startComputation();

        this.calculator.stopComputation();
    }

    public void retrieveVideoMetadata() {
        String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        duration = Long.parseLong(time);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String retrievedFPS = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);

            fps = (retrievedFPS != null) ? Double.parseDouble(retrievedFPS) : 120;
        } else
            fps = this.computeApproximateFrameRate();

        Log.i(TAG, "Video metadata retrieved. Duration: " + duration + ", FPS: " + fps + ".");
    }

    public long getDuration() {
        return duration;
    }

    public double getFps() {
        return fps;
    }

    private double computeApproximateFrameRate() {
        // TODO.
        return 118;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    public SpeedCalculator getCalculator() {
        return calculator;
    }
}
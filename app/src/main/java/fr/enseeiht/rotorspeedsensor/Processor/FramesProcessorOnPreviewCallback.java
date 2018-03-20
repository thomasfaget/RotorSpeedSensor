package fr.enseeiht.rotorspeedsensor.Processor;

import android.hardware.Camera;
import android.util.Log;

import fr.enseeiht.rotorspeedsensor.Calculator.SpeedCalculator;

/** Created by thomas on 05/07/2017.
 *
 * FrameProcessorOnCallback use the old hardware.Camera to acquire images by the camera.
 * The frame are acquired by a preview callback, and are sent to the Speed Calculator
 */

public class FramesProcessorOnPreviewCallback implements FramesProcessor, Camera.PreviewCallback {

    private SpeedCalculator speedCalculator;

    private Camera camera;

    private int pictureWidth;
    private int pictureHeight;

    private int currentBuffer;
    private int nbBuffers;
    private byte[][] buffers;

    public FramesProcessorOnPreviewCallback(SpeedCalculator speedCalculator, Camera camera, int pictureWidth, int pictureHeight, int nbBuffers) {

        this.speedCalculator = speedCalculator;

        this.camera = camera;

        this.pictureHeight = pictureHeight;
        this.pictureWidth = pictureWidth;

        this.nbBuffers = nbBuffers;
    }

    @Override
    public void start() {

        // Allocate the buffers
        currentBuffer = 0;
        this.buffers = new byte[nbBuffers][3*pictureHeight*pictureWidth/2];

        // Set the first buffer :
        camera.addCallbackBuffer(buffers[0]);

        // Start the acquisition of frame :
        camera.setPreviewCallbackWithBuffer(this);

        // Start the Speed Calculator :
        speedCalculator.startComputation();

    }

    @Override
    public void stop() {

        // Stop the Speed Calculator :
        speedCalculator.stopComputation();
        speedCalculator.clearData();

        // Stop the acquisition of frame :
        camera.setPreviewCallbackWithBuffer(null);

        // Clear the buffer :
        buffers = null;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {

        // Set the next buffer :
        currentBuffer = currentBuffer + 1 == nbBuffers ? 0 : currentBuffer + 1;

        camera.addCallbackBuffer(buffers[currentBuffer]);

        // Send the frame to the calculator :
        speedCalculator.processFrame(data, pictureWidth, pictureHeight);
    }
}

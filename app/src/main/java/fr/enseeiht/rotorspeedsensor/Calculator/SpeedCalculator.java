package fr.enseeiht.rotorspeedsensor.Calculator;

import android.graphics.Bitmap;

import java.nio.ByteBuffer;

import fr.enseeiht.rotorspeedsensor.StatsFragment;

/** Created by thomas on 14/06/2017.
 *
 * An interface to manage the actions of speed computation
 */
public interface SpeedCalculator {

    /** Start the speed computation
     */
    void startComputation();

    /** Interrupt the speed computation
     */
    void stopComputation();

    /** Clear all data acquired
     */
    void clearData();

    /** Process a frame dimensions width * height stored in the provided buffer.
     *
     * @param buffer A buffer of bytes.
     * @param width The width of the frame.
     * @param height The height of the frame.
     */
    void processFrame(byte[] buffer, int width, int height);

    /** Process a frame dimensions width * height stored in the provided buffer.
     *
     * @param buffer A buffer of bytes.
     * @param width The width of the frame.
     * @param height The height of the frame.
     */
    void processFrame(ByteBuffer buffer, int width, int height);

    void processFrame(Bitmap frame);

    /** Set the sample time, i.e. the time needed to acquire a correct amount of data
     *
     * @param sampleTime the sample time (in seconds)
     */
    void setSampleTime(double sampleTime);

    /** Set the theoretical fps of the camera
     *
     * @param cameraFPS theoretical fps
     */
    void setCameraFPS(double cameraFPS);

    void postponeProcessing(int expectedFramesAmount);

    void setDataListener(StatsFragment statsFragment);
}

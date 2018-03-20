package fr.enseeiht.rotorspeedsensor.Calculator;

/** Created by thomas on 12/06/2017.
 *
 * A simple callback used by SpeedCalculator to notify some information to the interface
 */

public interface SpeedCalculatorCallback {

    /** Notify that the speed was computed
     */
    void callSpeed(double speed);

    // TODO add doc
    void setStatus(String status);

    /** Notify that a frame was acquired by the camera.
     */
    void notifyAmountOfProcessedFrames(int amountOfProcessedFrames, int expectedFramesAmount);

    /** Notify that an approximate fps is computed
     */
    void callFps(double fps);

    void onProcessStarted();

    void onProcessStopped();

    void onProcessCompleted();
}

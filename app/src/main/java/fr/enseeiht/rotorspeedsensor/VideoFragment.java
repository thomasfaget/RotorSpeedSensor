package fr.enseeiht.rotorspeedsensor;

import android.app.Fragment;

import fr.enseeiht.rotorspeedsensor.Calculator.SpeedCalculator;

/**
 * Minimal behavior of a video fragment.
 *
 * @author : Matthieu Le Boucher
 */

public interface VideoFragment {

    /**
     * @return The calculator used by the video fragment to process frames.
     */
    SpeedCalculator getCalculator();

    void setDataListener(StatsFragment statsFragment);
}

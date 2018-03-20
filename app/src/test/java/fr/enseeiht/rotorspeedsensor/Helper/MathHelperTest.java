package fr.enseeiht.rotorspeedsensor.Helper;

import android.util.SparseArray;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author : Matthieu Le Boucher
 */
public class MathHelperTest {
    SparseArray<Double> localMaxima;

    SparseArray<Double> cleanedMaxima;

    @Before
    public void initialize() {
        localMaxima = new SparseArray<>();
        cleanedMaxima = new SparseArray<>();
    }

    @Test
    public void findLocalMaximaWithIndices() throws Exception {
        double[] input = new double[]{1, 2, 3, 2, 4, 5, 6, 3, 2, 3, 4};

        localMaxima = MathHelper.findLocalMaximaWithIndices(input);

        assertEquals(localMaxima.keyAt(0), 2);
        assertEquals((Double) localMaxima.valueAt(0), 3, 0.01);
        assertEquals(localMaxima.keyAt(1), 6);
        assertEquals((Double) localMaxima.valueAt(1), 6, 0.01);
    }

    @Test
    public void keepAboveThreshold() throws Exception {
        SparseArray<Double> maxima = new SparseArray<>();
        maxima.put(2, 30.);
        maxima.put(4, 40.);
        maxima.put(6, 60.);
        maxima.put(7, 59.);
        maxima.put(8, 61.);
        maxima.put(9, 100.);

        cleanedMaxima = MathHelper.keepAboveThreshold(0.6, maxima);

        assertEquals(cleanedMaxima.keyAt(0), 6);
        assertEquals(cleanedMaxima.valueAt(0), 60, 0.01);
        assertEquals(cleanedMaxima.keyAt(1), 8);
        assertEquals(cleanedMaxima.valueAt(1), 61, 0.01);
        assertEquals(cleanedMaxima.keyAt(2), 9);
        assertEquals(cleanedMaxima.valueAt(2), 100, 0.01);
    }

    @Test
    public void ignoreContinuousComponent() throws Exception {
        double[] input = new double[]{10, 2, 3, 2, 4, 5, 6, 3, 2, 3, 4};

        localMaxima = MathHelper.findLocalMaximaWithIndices(input);

        assertEquals(localMaxima.keyAt(0), 2);
        assertEquals((Double) localMaxima.valueAt(0), 3, 0.01);
        assertEquals(localMaxima.keyAt(1), 6);
        assertEquals((Double) localMaxima.valueAt(1), 6, 0.01);
    }
}
package fr.enseeiht.rotorspeedsensor.Helper;

import android.util.Log;
import android.util.SparseArray;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;


/**
 * @author : Matthieu Le Boucher
 */

public class MathHelper {

    private enum Sign {
        NEGATIVE,
        POSITIVE
    }


    public static void convertToAbsoluteValues(double[] values) {

        for (int i = 0; i < values.length; i++) {
            values[i] = Math.abs(values[i]);
        }

    }

    public static SparseArray<Double> findLocalMaximaWithIndices(double[] values) {
        SparseArray<Double> localMaxima = new SparseArray<>();

        if (values.length <= 2)
            return localMaxima;

        // Values is not empty.
        Sign lastSign = getSign(values[1] - values[0]);

        for (int i = 2; i < values.length; i++) {
            Sign currentSign = getSign(values[i] - values[i - 1]);
            if (lastSign != currentSign && currentSign == Sign.NEGATIVE)
                localMaxima.append(i - 1, values[i - 1]);

            lastSign = currentSign;
        }

        return localMaxima;
    }

    /** Return the index of the maximum of a list, researched in an interval
     *
     * @param values set of values
     * @param indexApproximate the index of the approximate value
     * @param percentageInterval the percentage for the interval of research, i.e. [percentage*approximate , (1+percentage)*approximate]
     * @return the index of the max
     */
    public static int findMaximumAroundApproximateValue(double[] values, int indexApproximate, double percentageInterval) {
        
        int indexMax = 0;
        double max = 0;
        int borneMin = Math.max(0, (int) ((1-percentageInterval)*((double) indexApproximate)));
        int borneMax = Math.min(values.length, (int) ((1+percentageInterval)*((double) indexApproximate)) );

        for (int i = borneMin; i < borneMax; i++) {
            if (values[i] > max) {
                max = values[i];
                indexMax = i;
            }
        }

        return indexMax;
    }

    private static Sign getSign(double value) {
        return value >= 0 ? Sign.POSITIVE : Sign.NEGATIVE;
    }

    public static void keepAboveThreshold(double threshold, SparseArray<Double> values) {

        if (values.size() == 0)
            return;

        // Find maximum and minimum values
        double maximum = values.valueAt(0);
        double minimum = maximum;

        for (int i = 1; i < values.size(); i++) {
            if (values.valueAt(i) > maximum)
                maximum = values.valueAt(i);
            if (values.valueAt(i) < minimum) {
                minimum = values.valueAt(i);
            }
        }

        // Only keep values that respect the given threshold with respect to the maximum and minimum value.
        int i = 0;
        int size = values.size();
        while (i < size) {
            if ( (values.valueAt(i) - minimum) >= threshold * (maximum - minimum)) {
                i++;
            }
            else {
                values.removeAt(i);
                size--;
            }
        }
    }

    /** Return the average distance between a set of value.
     *
     * @param values the set of values
     * @return The average distance between all the values
     */
    public static double averageDistanceBetweenValues(SparseArray<Double> values) {

        double sumDistance = 0;

        for (int i = 0; i < values.size() - 1; i++) {
            sumDistance += values.keyAt(i+1) - values.keyAt(i);
        }

        return sumDistance <= 1 ? 0 : sumDistance/((double) (values.size() -1));

    }

    /** Return the median value in a List of Double
     */
    public static double median(List<Double> list) {
        if (list.isEmpty()) {
            return 0;
        }
        else {
            Collections.sort(list);
            int middle = list.size() / 2;

            return list.get(middle);
        }
    }

    /** Return the mean value in a List of Double
     */
    public static double mean(List<Double> list) {

        int n = list.size();
        double mean = 0;

        for (Double number : list) {
            mean += number;
        }
        return mean/n;
    }

    public static double variance(List<Double> data)
    {
        double mean = mean(data);
        double temp = 0;
        for(double a : data)
            temp += (a - mean) * (a - mean);
        return temp / data.size();
    }

    public static double standardDeviation(List<Double> data)
    {
        return Math.sqrt(variance(data));
    }

    /** Compute the mean of set values, only with the values such are |value - mean| <= standard deviation
     *
     */
    public static double meanInsideStandardDeviation(List<Double> values) {

        double mean = MathHelper.mean(values);
        double standardDeviation = MathHelper.standardDeviation(values);

        int nbValues = 0;
        double meanSD = 0;

        for (double value : values) {
            if (Math.abs(value - mean) <= standardDeviation) {
                meanSD += value;
                nbValues ++;
            }
        }
        return  meanSD/nbValues;
    }

    /** Return the mode of an Double List (i.e. the most frequent value)
     */
    public static double mode(List<Double> list) {

        HashMap<Double,Integer> occ = new HashMap<>();
        double mode = 0;
        int countMode = 0;

        for (double number: list) {
            if ( occ.containsKey(number) ) {
                int count = occ.get(number);
                occ.put(number, count+1);
                if ((count+1) > countMode) {
                    mode = number;
                    countMode = count;
                }
            }
            else {
                occ.put(number, 1);
            }
        }

        return mode;
    }

    public static void applyHammingWindow(double[] values) {
        for (int i = 0; i < values.length; i++) {
            values[i] = values[i] * (0.54 - 0.46 * Math.cos(2 * Math.PI * i / (values.length - 1)));
        }
    }

    public static void applyHammingWindowFrequencyDomain(double[] values) {
        for (int i = 1; i < values.length - 1; i++) {
            values[i] = 0.54 * values[i] - 0.23 * (values[i-1] + values[i+1]);
        }
    }

    public static double rationalFractionApproximationDenominator(double value) {
        double tolerance = 1.0E-6;
        double h1=1; double h2=0;
        double k1=0; double k2=1;
        double b = value;

        do {
            double a = Math.floor(b);
            double aux = h1;
            h1 = a * h1 + h2;
            h2 = aux;
            aux = k1;
            k1 = a * k1 + k2;
            k2 = aux;
            b = 1 / (b-a);
        } while (Math.abs(value - h1 / k1) > value * tolerance);

        return k1;
    }

    public static double computeAutocorrelationFunction(double[] signal, int offset) {
        double r = 0;

        for (int i = 0; i < signal.length; i++) {
            if (i + offset >= signal.length)
                r += Math.abs(signal[i] - signal[i + offset - signal.length]);
            else
                r += Math.abs(signal[i] - signal[i + offset]);
        }

        return r;
    }

    public static void reverseArraySign(double[] values) {
        for (int i = 0; i < values.length; i++) {
            values[i] = -values[i];
        }
    }

    public static int findBestOffsetForMinimalAutocorrelationFunction(double[] signal) {
        int initialOffset = 5;
        int maximalOffset = 100;
        double[] autocorrelationValues = new double[maximalOffset - initialOffset];


        for (int s = initialOffset; s < maximalOffset; s++) {
            autocorrelationValues[s - initialOffset] = computeAutocorrelationFunction(signal, s);
        }

        reverseArraySign(autocorrelationValues);

        SparseArray<Double> localMinima = findLocalMaximaWithIndices(autocorrelationValues);

        Log.i("MathHelper", "Local minima: " + localMinima);

        return localMinima.keyAt(0) + initialOffset;
    }
}
package fr.enseeiht.rotorspeedsensor;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;

import fr.enseeiht.rotorspeedsensor.Calculator.SpeedCalculator;

/**
 * Displays various statistics about the gathered data.
 *
 * @author : Matthieu Le Boucher
 */

public class StatsFragment extends Fragment {
    private SpeedCalculator calculator;
    private GraphView graph, graph2;

    LineGraphSeries<DataPoint> luminositySeries = new LineGraphSeries<>();
    LineGraphSeries<DataPoint> spectrumSeries = new LineGraphSeries<>();
    PointsGraphSeries<DataPoint> orders = new PointsGraphSeries<>();
    PointsGraphSeries<DataPoint> orders2 = new PointsGraphSeries<>();
    PointsGraphSeries<DataPoint> interval = new PointsGraphSeries<>();

    private double currentIndex = 1;
    private double currentFrequencyOrder = 0;

    public StatsFragment() {
    }

    public void setCalculator(SpeedCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.fragment_stats, container, false);

        graph = (GraphView) rootView.findViewById(R.id.graph);
        graph2 = (GraphView) rootView.findViewById(R.id.graph2);

        graph.setTitle("Luminosity over time");
        graph.getGridLabelRenderer().setPadding(40);
        graph.addSeries(luminositySeries);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(1);
        graph.getViewport().setMaxX(150);
        graph.getViewport().setMaxY(255);
        graph.setBackgroundColor(Color.WHITE);

        graph2.setTitle("Spectral decomposition");
        graph2.getGridLabelRenderer().setPadding(40);
        graph2.addSeries(spectrumSeries);
        graph2.getViewport().setXAxisBoundsManual(true);
        graph2.getViewport().setMinX(1);
        graph2.setBackgroundColor(Color.WHITE);

        orders.setColor(Color.RED);
        orders.setShape(PointsGraphSeries.Shape.TRIANGLE);
        graph2.addSeries(orders);

        orders2.setColor(Color.GREEN);
        orders2.setShape(PointsGraphSeries.Shape.TRIANGLE);
        graph2.addSeries(orders2);

        interval.setColor(Color.BLACK);
        interval.setShape(PointsGraphSeries.Shape.TRIANGLE);
        graph2.addSeries(interval);

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    public void sendData(double data) {
        Log.i("Quick", "New data received from stats fragment: " + data);
        this.luminositySeries.appendData(new DataPoint(currentIndex, data), true, 150);
        currentIndex++;
        Log.i("Quick", "Current data luminositySeries max X value: " + luminositySeries.getHighestValueX());
    }

    public void sendFundamentalResearchInterval(int minOrder, int maxOrder) {
        interval.resetData(new DataPoint[]{new DataPoint(minOrder, 0), new DataPoint(maxOrder, 0)});
    }

    public void sendApproximateFundamentalOrder(int order) {
        orders.resetData(new DataPoint[]{new DataPoint(order, 0)});
        Log.i("Quick", "Received new order: " + order);
    }

    public void sendClosestFrequencyOrderFound(int order) {
        orders2.resetData(new DataPoint[]{new DataPoint(order, 0)});
        Log.i("Quick", "Received new order (exact): " + order);
    }

    public void sendSpectrumData(double[] spectrumData) {
        int halfFrequency = (int) Math.ceil(spectrumData.length / 2);
        Log.i("Quick", "Received spectrum or length " + spectrumData.length);
        graph2.getViewport().setMaxX(halfFrequency);

        DataPoint[] newSpectrumDataPoints = new DataPoint[halfFrequency - 5];
        for (int i = 0; i < halfFrequency - 5; i++) {
            newSpectrumDataPoints[i] = new DataPoint(i, spectrumData[i + 5]);
        }

        spectrumSeries.resetData(newSpectrumDataPoints);
    }
}

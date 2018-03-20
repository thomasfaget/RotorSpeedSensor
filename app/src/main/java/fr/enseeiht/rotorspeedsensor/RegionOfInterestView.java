package fr.enseeiht.rotorspeedsensor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import fr.enseeiht.rotorspeedsensor.Calculator.RegionOfInterest;


/** Created by thomas on 22/06/2017.
 *
 * A view to display the regionOfInterest once computed
 */

public class RegionOfInterestView extends View {

    private Paint paint;
        private RegionOfInterest roi;

    public RegionOfInterestView(Context context) {
        super(context);
    }

    public RegionOfInterestView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public RegionOfInterestView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (roi != null) {

            int h = getHeight();
            int w = getWidth();

            // Set the ROI in the context of the view

            float leftROI = roi.getHorizontalLowerBound(w);
            float rightROI = roi.getHorizontalUpperBound(w);
            float topROI = roi.getVerticalLowerBound(h);
            float bottomROI = roi.getVerticalUpperBound(h);

            // Add a transparent mask around the roi
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(getResources().getColor(R.color.grayTransparent));
            canvas.drawRect(0, 0, leftROI, h, paint);
            canvas.drawRect(rightROI, 0, w, h, paint);
            canvas.drawRect(leftROI, 0, rightROI, topROI, paint);
            canvas.drawRect(leftROI, bottomROI, rightROI, h, paint);

            // Draw border
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.DKGRAY);
            if (leftROI == rightROI && topROI == bottomROI) {
                paint.setStrokeWidth(10);
                canvas.drawPoint(leftROI, topROI, paint);
            }
            else {
                paint.setStrokeWidth(3);
                canvas.drawRect(leftROI, topROI, rightROI, bottomROI, paint);
            }

        }
    }

    /** Set the ROI in the view
     */
    public void initializeRegionOfInterestSurface(RegionOfInterest roi) {
        this.roi = roi;
        this.paint = new Paint();
    }


}

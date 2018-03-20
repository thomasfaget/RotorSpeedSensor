package fr.enseeiht.rotorspeedsensor.Calculator;

import android.graphics.Rect;

/** Region Of Interest
 */
public class RegionOfInterest {

    // The center of the ROI
    private float horizontalRoiCenter, verticalRoiCenter;

    // The side of the ROI
    private float roiSide;

    /** Create a region of interest with absolute values (between 0 and 1)
     *
     * @param horizontalRoiCenter the horizontal coordinates of the center of the region of interest (between 0 and 1)
     * @param verticalRoiCenter the vertical coordinates of the center of the region of interest (between 0 and 1)
     * @param roiSide the side of the region of interest (between 0 and 1)
     */
    public RegionOfInterest(float horizontalRoiCenter, float verticalRoiCenter, float roiSide) {

        this.horizontalRoiCenter = horizontalRoiCenter;
        this.verticalRoiCenter = verticalRoiCenter;

        this.roiSide = roiSide;
    }

    /** Get the width of the Region of interest, relative to the given width in argument
     *
     * @param widthImage the width of the image in which the ROI is computed
     */
    int getWidth(int widthImage) {
        return (int) (roiSide*widthImage);
    }

    /** Get the height of the Region of interest, relative to the given height in argument
     *
     * @param heightImage the height of the image in which the ROI is computed
     */
    int getHeight(int heightImage) {
        return (int) (roiSide*heightImage);
    }

    /** Get the size of the Region of interest (in number of pixels).
     * This size is relative to the given width and height in argument.
     *
     * @param widthImage the width of the image in which the ROI is computed
     * @param heightImage the height of the image in which the ROI is computed
     */
    public int getSize(int widthImage, int heightImage) {
        return (int) (roiSide*widthImage*roiSide*heightImage);
    }

    /** Get horizontal lower bound of the Region of interest (The left bound of the ROI)
     * It is relative to the given width in argument
     *
     * @param widthImage the width of the image in which the ROI is computed
     */
    public int getHorizontalLowerBound(int widthImage) {
        return (int) ((horizontalRoiCenter - roiSide/2)*widthImage);
    }

    /** Get horizontal upper bound of the Region of interest (The right bound of the ROI)
     * It is relative to the given width in argument
     *
     * @param widthImage the width of the image in which the ROI is computed
     */
    public int getHorizontalUpperBound(int widthImage) {
        return (int) ((horizontalRoiCenter + roiSide/2)*widthImage);
    }

    /** Get vertical lower bound of the Region of interest (The top bound of the ROI)
     * It is relative to the given height in argument
     *
     * @param heightImage the height of the image in which the ROI is computed
     */
    public int getVerticalLowerBound(int heightImage) {
        return (int) ((verticalRoiCenter - roiSide/2)*heightImage);
    }

    /** Get vertical upper bound of the Region of interest (The top bound of the ROI)
     * It is relative to the given height in argument
     *
     * @param heightImage the height of the image in which the ROI is computed
     */
    public int getVerticalUpperBound(int heightImage) {
        return (int) ((verticalRoiCenter + roiSide/2)*heightImage);
    }

    /** Get the 4 corners of the Region of Interest (left, right, top and bottom bound of the ROI)
     * It is relative to the given height and width in argument
     *
     * @param widthImage the width of the image in which the ROI is computed
     * @param heightImage the height of the image in which the ROI is computed
     * @return A android.graphics.Rect with the 4 bound of the ROI
     */
    public Rect getCorners(int widthImage, int heightImage) {
        Rect rect = new Rect();

        rect.left = getHorizontalLowerBound(widthImage);
        rect.right = getHorizontalUpperBound(widthImage);
        rect.top = getVerticalLowerBound(heightImage);
        rect.bottom = getVerticalUpperBound(heightImage);

        return rect;
    }

    /** Perform a zoom + of the ROI on the view.
     */
    public void applyZoomMore(float zoomAcceleration) {

        // Perform the zoom, with keeping a positive side
        roiSide = Math.max(0.1f, roiSide - zoomAcceleration);
    }

    /** Perform a zoom - of the ROI on the view.
     */
    public void applyZoomLess(float zoomAcceleration) {

        // Verify if the zoom can be perform :
        float distanceToScreenBorder = 0.5f - Math.max(Math.abs(horizontalRoiCenter - 0.5f), Math.abs(verticalRoiCenter - 0.5f));

        if (distanceToScreenBorder <= roiSide/2 + zoomAcceleration) {
            // The zoom can be perform, but not as far as it should (too close from the border)
            roiSide = 2*distanceToScreenBorder;

        }
        else {
            // The zoom can be perform
            roiSide += zoomAcceleration;
        }

    }

    /** Perform a move of the ROI on the view.
     */
    public void applyMove(float moveX, float moveY) {

        // Check X move :
        if (moveX < 0) {
            // Move to the left :
            horizontalRoiCenter = Math.max(roiSide/2, horizontalRoiCenter + moveX);
        }
        else {
            // Move to the right :
            horizontalRoiCenter = Math.min(1 - roiSide/2, horizontalRoiCenter + moveX);
        }

        // Check Y move :
        if (moveY < 0) {
            // Move to the top :
            verticalRoiCenter = Math.max(roiSide/2, verticalRoiCenter + moveY);
        }
        else {
            // Move to the bottom
            verticalRoiCenter = Math.min(1 - roiSide/2, verticalRoiCenter + moveY);
        }
    }
}
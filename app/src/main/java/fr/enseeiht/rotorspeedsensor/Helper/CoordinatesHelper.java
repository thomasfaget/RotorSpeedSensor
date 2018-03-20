package fr.enseeiht.rotorspeedsensor.Helper;

/**
 * @author Matthieu Le Boucher
 *
 * Helps going back and forth between cartesian coordinates and one dimensional representation
 * coordinates, used for performance purposes.
 */

public class CoordinatesHelper {
    /**
     * @param width         The width of the considered cartesian plane.
     * @param i             The pixel index in one dimensional representation.
     * @return int[x, y]    The pixel's cartesian coordinates.
     */
    public static int[] oneDimensionToCartesian(int width, int i) {
        int[] cartesianCoordinates = new int[2];
        cartesianCoordinates[0] = i % width; // x
        cartesianCoordinates[1] = i / width; // y
        return cartesianCoordinates;
    }

    /**
     * @param width     The width of the considered cartesian plane.
     * @param x         The pixel's x coordinate.
     * @param y         The pixel's y coordinate.
     * @return int      The one dimensional index of the pixel situated at (x, y).
     */
    public static int cartesianToOneDimension(int width, int x, int y) {
        return x + width * y;
    }
}

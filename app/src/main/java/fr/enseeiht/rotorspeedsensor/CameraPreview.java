package fr.enseeiht.rotorspeedsensor;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;


/** A basic VideoRecorder preview class
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private Camera camera;

    public CameraPreview(Context context, Camera camera) {
        super(context);
        this.camera = camera;

        getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        Log.i("Quick", "Camera preview resolution: " + this.getWidth() + "x" + this.getHeight());

        try {
            camera.setPreviewDisplay(holder);
        } catch (IOException e) {
            Log.e(CameraVideoFragment.ERROR, e.getMessage());
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Do nothing
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Do not handle Change or Rotation
    }


}
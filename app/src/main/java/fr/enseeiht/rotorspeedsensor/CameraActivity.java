package fr.enseeiht.rotorspeedsensor;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Range;
import android.view.Window;
import android.view.WindowManager;

/**
 * Retrieves and executes the correct fragment based on hardware and available API version.
 *
 * @author Matthieu Le Boucher
 */

public class CameraActivity extends AppCompatActivity {
    public static final String TAG = "CameraActivity";

    /**
     * The number of pages (wizard steps) to show in this demo.
     */
    private static final int NUM_PAGES = 3;

    /**
     * The pager widget, which handles animation and allows swiping horizontally to access previous
     * and next wizard steps.
     */
    private StaticViewPager mPager;

    /**
     * The pager adapter, which provides the pages to the view pager widget.
     */
    private PagerAdapter mPagerAdapter;

    /**
     * The actual camera fragment to run.
     */
    private VideoFragment cameraFragmentToRun;

    private StatsFragment statsFragment;

    /** The tutorial fragment
     */
    private TutorialFragment tutorialFragment;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Use fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_camera);

        mPager = (StaticViewPager) findViewById(R.id.pager);
        mPager.setPagingEnabled(false);
        mPagerAdapter = new ScreenSlidePagerAdapter(getSupportFragmentManager());
        mPager.setAdapter(mPagerAdapter);

        tutorialFragment = new TutorialFragment();
        statsFragment = new StatsFragment();
        // Decide which fragment to run.
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && cameraDeviceSupportsHighSpeedRecording()) {
            // Camera2 requires API 23 or above.
            Log.i(TAG, "API 23 found and camera device supports high speed recording." +
                    " Using Camera2VideoFragment.");
            cameraFragmentToRun = Camera2VideoFragment.newInstance();
        } else {*/
            Log.i(TAG, "API 23 not found or camera device supports high speed recording." +
                    " Using CameraVideoFragment.");
            cameraFragmentToRun = CameraVideoFragment.newInstance();
        cameraFragmentToRun.setDataListener(statsFragment);
        /*}*/

        // Replace container's content and commit.
        /*if (null == savedInstanceState) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, cameraFragmentToRun)
                    .commit();
        }*/
    }

    @Override
    public void onBackPressed() {
        Log.i(TAG, "Back pressed.");
        if (mPager.getCurrentItem() == 0) {
            // If the user is currently looking at the first step, allow the system to handle the
            // Back button. This calls finish() on this activity and pops the back stack.
            super.onBackPressed();
        } else {
            // Otherwise, select the main step.
            mPager.setCurrentItem(0);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private boolean cameraDeviceSupportsHighSpeedRecording() {
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0];

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                // Can't retrieve data, suppose not.
                return false;
            }

            Range<Integer>[] fpsRanges = map.getHighSpeedVideoFpsRanges();
            Range<Integer> highestRange = fpsRanges[fpsRanges.length - 1];

            return highestRange.getUpper() >= 60;
        } catch (CameraAccessException e) {
            return false;
        }
    }

    /**
     * A simple pager adapter that represents 3 ScreenSlidePageFragment objects, in
     * sequence.
     */
    private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {
        ScreenSlidePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return (Fragment) cameraFragmentToRun;
                case 1:
                    return statsFragment;
                case 2:
                    return tutorialFragment;
            }

            return null;
        }

        @Override
        public int getCount() {
            return NUM_PAGES;
        }
    }

    public ViewPager getPager() {
        return mPager;
    }
}

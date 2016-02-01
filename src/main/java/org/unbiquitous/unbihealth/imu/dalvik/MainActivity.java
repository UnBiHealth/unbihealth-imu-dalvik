package org.unbiquitous.unbihealth.imu.dalvik;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.List;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Quaternion;
import org.unbiquitous.unbihealth.imu.IMUDriver;
import org.unbiquitous.unbihealth.imu.dalvik.util.SystemUiHider;
import org.unbiquitous.uos.core.InitialProperties;
import org.unbiquitous.uos.core.UOS;
import org.unbiquitous.uos.core.UOSLogging;
import org.unbiquitous.uos.core.adaptabitilyEngine.SmartSpaceGateway;
import org.unbiquitous.uos.core.driverManager.DriverManager;
import org.unbiquitous.uos.core.driverManager.UosDriver;
import org.unbiquitous.uos.network.socket.TCPProperties;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import org.unbiquitous.uos.network.socket.connectionManager.UDPConnectionManager;
import org.unbiquitous.uos.network.socket.radar.MulticastRadar;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class MainActivity extends Activity implements SensorEventListener {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * If set, will toggle the system UI visibility upon interaction. Otherwise,
     * will show the system UI visibility upon interaction.
     */
    private static final boolean TOGGLE_ON_CLICK = true;

    /**
     * The flags to pass to {@link SystemUiHider#getInstance}.
     */
    private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

    /**
     * The instance of the {@link SystemUiHider} for this activity.
     */
    private SystemUiHider mSystemUiHider;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private TextView mSensorDataView;
    private TextView mStatusView;
    private UOS mUOS;
    private IMUDriver mIMUDriver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UOSLogging.setLevel(Level.ALL);

        setContentView(R.layout.activity_main);

        final View controlsView = findViewById(R.id.fullscreen_content_controls);
        mSensorDataView = (TextView) findViewById(R.id.sensor_data_view);

        mStatusView = (TextView) findViewById(R.id.status_view);
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                StringWriter wr = new StringWriter();
                e.printStackTrace(new PrintWriter(wr));
                mStatusView.setText(wr.toString());
                UOSLogging.getLogger().log(Level.SEVERE, "Error!", e);
            }
        });

        // Set up an instance of SystemUiHider to control the system UI for
        // this activity.
        mSystemUiHider = SystemUiHider.getInstance(this, mSensorDataView, HIDER_FLAGS);
        mSystemUiHider.setup();
        mSystemUiHider.setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
            // Cached values.
            int mControlsHeight;
            int mShortAnimTime;

            @Override
            @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
            public void onVisibilityChange(boolean visible) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                    // If the ViewPropertyAnimator API is available
                    // (Honeycomb MR2 and later), use it to animate the
                    // in-layout UI controls at the bottom of the
                    // screen.
                    if (mControlsHeight == 0) {
                        mControlsHeight = controlsView.getHeight();
                    }
                    if (mShortAnimTime == 0) {
                        mShortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);
                    }
                    controlsView.animate().translationY(visible ? 0 : mControlsHeight).setDuration(mShortAnimTime);
                } else {
                    // If the ViewPropertyAnimator APIs aren't
                    // available, simply show or hide the in-layout UI
                    // controls.
                    controlsView.setVisibility(visible ? View.VISIBLE : View.GONE);
                }

                if (visible && AUTO_HIDE) {
                    // Schedule a hide().
                    delayedHide(AUTO_HIDE_DELAY_MILLIS);
                }
            }
        });

        // Set up the user interaction to manually show or hide the system UI.
        mSensorDataView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (TOGGLE_ON_CLICK) {
                    mSystemUiHider.toggle();
                } else {
                    mSystemUiHider.show();
                }
            }
        });

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        //mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mSensor = getRotationVectorSensor();

        new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... params) {
                runUOS();
                return null;
            }
        }.execute();
    }

    private Sensor getRotationVectorSensor() {
        List<Sensor> list = mSensorManager.getSensorList(Sensor.TYPE_ROTATION_VECTOR);
        if (!list.isEmpty())
            return list.get(0);
        return null;
    }

    private void runUOS() {
        mStatusView.setText(R.string.msg_starting_uos);

        @SuppressWarnings("serial")
        InitialProperties settings = new TCPProperties() {
            {
                setPort(getResources().getInteger(R.integer.uos_tcp_port));
                setPassivePortRange(getResources().getInteger(R.integer.uos_tcp_passivePortRange_start), getResources().getInteger(R.integer.uos_tcp_passivePortRange_end));
                addRadar(MulticastRadar.class, UDPConnectionManager.class);
                addDriver(IMUDriver.class, "imudriver42");
                put(IMUDriver.SENSOR_ID_KEY, "arm");
                put(IMUDriver.SENSITIVITY_KEY, 0.001);
            }
        };
        mUOS = new UOS();
        mUOS.start(settings);

        mIMUDriver = null;
        for (UosDriver d : ((SmartSpaceGateway) mUOS.getGateway()).getDriverManager().listDrivers())
            if (IMUDriver.DRIVER_NAME.equals(d.getDriver().getName()) && (d instanceof IMUDriver)) {
                mIMUDriver = (IMUDriver) d;
                break;
            }

        UOSLogging.getLogger().info("sensor: " + mSensor);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mSensor != null)
            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("sensor event: " + event.sensor.getType() + "\n");
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] values = event.values;
            if (values != null) {
                sb.append("x: ");
                sb.append(values[0]);
                sb.append("\n");
                sb.append("y: ");
                sb.append(values[1]);
                sb.append("\n");
                sb.append("z: ");
                sb.append(values[2]);
                sb.append("\n");
                sb.append("w: ");
                sb.append(values[3]);
                sb.append("\n");
                mSensorDataView.setText(sb.toString());
                try {
                    if (mIMUDriver != null)
                        mIMUDriver.sensorChanged(new Quaternion(values[3], values[0], values[1], values[2]), event.timestamp);
                } catch (Throwable t) {
                    UOSLogging.getLogger().log(Level.SEVERE, "Error triggering IMU driver sensor change.", t);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    Handler mHideHandler = new Handler();
    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            mSystemUiHider.hide();
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }
}

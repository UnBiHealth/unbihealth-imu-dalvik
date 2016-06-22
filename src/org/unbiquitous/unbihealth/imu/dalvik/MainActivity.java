package org.unbiquitous.unbihealth.imu.dalvik;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.complex.Quaternion;
import org.unbiquitous.unbihealth.imu.IMUDriver;
import org.unbiquitous.unbihealth.imu.dalvik.util.IPTextWatcher;
import org.unbiquitous.unbihealth.imu.dalvik.util.SystemUiHider;
import org.unbiquitous.uos.core.InitialProperties;
import org.unbiquitous.uos.core.UOS;
import org.unbiquitous.uos.core.UOSLogging;
import org.unbiquitous.uos.core.adaptabitilyEngine.SmartSpaceGateway;
import org.unbiquitous.uos.core.driverManager.UosDriver;
import org.unbiquitous.uos.core.messageEngine.dataType.UpDevice;
import org.unbiquitous.uos.core.messageEngine.messages.Call;
import org.unbiquitous.uos.core.messageEngine.messages.Response;
import org.unbiquitous.uos.network.socket.radar.MulticastRadar;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

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

    private static final int UOS_PORT = 8300;

    /**
     * The instance of the {@link SystemUiHider} for this activity.
     */
    private SystemUiHider systemUiHider;
    private SensorManager sensorManager;
    private Sensor sensor;
    private EditText txtHostIP;
    private Spinner spnListeners;
    private ArrayAdapter<String> spnListenersAdapter;
    private EditText txtNewListener;
    private ImageButton btnAddListener;
    private Button btnTare;
    private TextView sensorDataView;
    private TextView statusView;
    private UOS uos;
    private IMUDriver imuDriver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UOSLogging.setLevel(Level.INFO);

        setContentView(R.layout.activity_main);

        txtHostIP = (EditText) findViewById(R.id.txt_host_ip);

        spnListeners = (Spinner) findViewById(R.id.spn_listeners);
        spnListeners.setAdapter(spnListenersAdapter = new ListenerListAdapter());

        txtNewListener = (EditText) findViewById(R.id.txt_new_listener);
        txtNewListener.addTextChangedListener(new IPTextWatcher());

        btnAddListener = (ImageButton) findViewById(R.id.btn_add_listener);

        btnTare = (Button) findViewById(R.id.btn_tare);
        btnTare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (imuDriver != null)
                    imuDriver.tare(null, null, null);
            }
        });

        final View controlsView = findViewById(R.id.fullscreen_content_controls);
        sensorDataView = (TextView) findViewById(R.id.sensor_data_view);

        statusView = (TextView) findViewById(R.id.status_view);
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                StringWriter wr = new StringWriter();
                e.printStackTrace(new PrintWriter(wr));
                statusView.setText(wr.toString());
                UOSLogging.getLogger().log(Level.SEVERE, "Error!", e);
            }
        });

        // Set up an instance of SystemUiHider to control the system UI for
        // this activity.
        systemUiHider = SystemUiHider.getInstance(this, sensorDataView, HIDER_FLAGS);
        systemUiHider.setup();
        systemUiHider.setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
            // Cached values.
            int controlsHeight;
            int shortAnimTime;

            @Override
            @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
            public void onVisibilityChange(boolean visible) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                    // If the ViewPropertyAnimator API is available
                    // (Honeycomb MR2 and later), use it to animate the
                    // in-layout UI controls at the bottom of the
                    // screen.
                    if (controlsHeight == 0) {
                        controlsHeight = controlsView.getHeight();
                    }
                    if (shortAnimTime == 0) {
                        shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);
                    }
                    controlsView.animate().translationY(visible ? 0 : controlsHeight).setDuration(shortAnimTime);
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
        sensorDataView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (TOGGLE_ON_CLICK) {
                    systemUiHider.toggle();
                } else {
                    systemUiHider.show();
                }
            }
        });

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        //sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensor = getRotationVectorSensor();

        new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... params) {
                runUOS();
                return null;
            }
        }.execute();
    }

    private Sensor getRotationVectorSensor() {
        List<Sensor> list = sensorManager.getSensorList(Sensor.TYPE_ROTATION_VECTOR);
        if (!list.isEmpty())
            return list.get(0);
        return null;
    }

    private void runUOS() {
        //m,u5UOSLogging.setLevel(Level.FINE);
        statusView.setText(R.string.msg_starting_uos);

        @SuppressWarnings("serial")
        InitialProperties settings = new MulticastRadar.Properties() {
            {
                put("ubiquitos.multicast.broadcastAddr", getBroadcastAddr());
                put("ubiquitos.multicast.beaconFrequencyInSeconds", 10);
                setPort(UOS_PORT);
                setPassivePortRange(getResources().getInteger(R.integer.uos_tcp_passivePortRange_start), getResources().getInteger(R.integer.uos_tcp_passivePortRange_end));
                addDriver(IMUDriver.class, "imudriver42");
                put(IMUDriver.DEFAULT_SENSOR_ID_KEY, "dalvik-imu");
                put(IMUDriver.SENSITIVITY_KEY, 0.001);
            }
        };
        uos = new UOS();
        uos.start(settings);
        try {
            txtHostIP.setText(uos.getGateway().getCurrentDevice().getNetworks().get(0).getNetworkAddress());
        } catch (Throwable t) {
        }

        imuDriver = null;
        for (UosDriver d : ((SmartSpaceGateway) uos.getGateway()).getDriverManager().listDrivers())
            if (IMUDriver.DRIVER_NAME.equals(d.getDriver().getName()) && (d instanceof IMUDriver)) {
                imuDriver = (IMUDriver) d;
                break;
            }
    }

    private String getBroadcastAddr() {
        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = wifi.getDhcpInfo();
        int addr = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
        StringBuilder sb = new StringBuilder(Integer.toString(addr & 0xFF));
        for (int k = 1; k < 4; ++k) {
            sb.append(".");
            sb.append((addr >> (k << 3)) & 0xFF);
        }
        return sb.toString();
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
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensor != null)
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        StringBuilder sb = new StringBuilder();
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
                sensorDataView.setText(sb.toString());
                try {
                    if (imuDriver != null)
                        imuDriver.sensorChanged(new Quaternion(values[3], values[0], values[1], values[2]));
                } catch (Throwable t) {
                    UOSLogging.getLogger().log(Level.SEVERE, "Error triggering IMU driver sensor change.", t);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    Handler hideHandler = new Handler();
    Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            systemUiHider.hide();
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        hideHandler.removeCallbacks(hideRunnable);
        hideHandler.postDelayed(hideRunnable, delayMillis);
    }

    private class ListenerListAdapter extends ArrayAdapter<String> {
        public ListenerListAdapter() {
            super(MainActivity.this, android.R.layout.simple_spinner_item);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        @Override
        public View getDropDownView(int pos, View view, ViewGroup parent) {
            if ((pos == 0) && (spnListeners.getSelectedItemPosition() < 1)) {
                TextView tv = new TextView(getContext());
                tv.setVisibility(View.GONE);
                view = tv;
            } else
                view = super.getDropDownView(pos, null, parent);

            return view;
        }
    }

    // Bits for the listener status integer.
    // If bit at DEAD position is set, then the listener's ip doesn't respond to service calls.
    // If bit at FOREIGN_NW position is set, then the listener's ip is from a different network than current one.
    private static int LST_BIT_DEAD = 0;
    private static int LST_BIT_FOREIGN_NW = 1;
    private Map<String, Integer> listenerStatus = new HashMap<>();
    private List<String> listeners = new ArrayList<>();
    private Object _listener_lock = new Object();
    private Comparator<String> listenerComparator = new ListenerComparator();

    private void checkListeners() {
        // Lists the current stored listeners.
        String[] listeners;
        synchronized (_listener_lock) {
            int len = Math.max(0, this.listeners.size() - 1);
            listeners = new String[len];
            for (int i = 0; i < len; ++i)
                listeners[i] = this.listeners.get(i + 1);
        }

        // Gets current subnet.
        int ip, netmask = 0xFFFFFFFF, subnet;
        DhcpInfo dhcp = ((WifiManager) getSystemService(Context.WIFI_SERVICE)).getDhcpInfo();
        if (dhcp != null) {
            netmask = dhcp.netmask;
            ip = dhcp.ipAddress;
        } else
            ip = 0;
        subnet = ip & netmask;

        // Checks the status of each listener.
        for (String listener : listeners) {
            int status = 0;

            // Extracts listener data.
            String[] data = listener.split(":");
            int port = UOS_PORT;
            ip = 0xFFFFFFFF;
            try {
                ip = toInt(InetAddress.getByName(data[0]));
                if (data.length > 1)
                    port = Integer.parseInt(data[1]);
            } catch (Throwable t) {
            }

            // Is it in the same network?
            if (subnet == (ip ^ netmask)) {
                status |= (1 << LST_BIT_DEAD); // assumes it's dead
                // Does it respond to a basic service call?
                try {
                    UpDevice device = new UpDevice(data[0]);
                    device.addNetworkInterface(data[0] + ":" + port, "Ethernet:TCP");
                    Response r = uos.getGateway().callService(device, new Call("uos.DeviceDriver", "listDrivers"));
                    if ((r != null) && StringUtils.isBlank(r.getError()))
                        status &= (~(1 << LST_BIT_DEAD)); // it's alive!
                } catch (Throwable t) {
                }
            } else
                status |= (1 << LST_BIT_FOREIGN_NW);

            synchronized (_listener_lock) {
                listenerStatus.put(listener, status);
            }
        }
    }

    private static int toInt(InetAddress inetAddress) {
        byte[] addr = inetAddress.getAddress();
        int ip = 0;
        for (int i = 0; i < addr.length; ++i, ip <<= 8)
            ip |= addr[i];
        return ip;
    }

    private class ListenerComparator implements Comparator<String> {
        @Override
        public int compare(String a, String b) {
            int ret = 0;

            // First let's check the statuses, if available.
            Integer sta, stb;
            synchronized (_listener_lock) {
                sta = listenerStatus.get(a);
                stb = listenerStatus.get(b);
            }
            if (sta == null) {
                if (stb != null)
                    return -1; // 'a' wasn't added yet, 'b' has, so 'a' should be listed first
            } else {
                if (stb == null)
                    return 1;  // 'b' wasn't added yet, 'a' has, so 'b' should be listed first
                ret = sta.compareTo(stb); // both are not null, first compare status
            }

            // If both have the same status, then just sort alphabetically.
            if (ret == 0)
                ret = a.compareTo(b);

            return ret;
        }
    }
}

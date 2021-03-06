package demo.ethings.com.ethingsble.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.chimeraiot.android.ble.BleScanner;
import com.chimeraiot.android.ble.BleUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import demo.ethings.com.ethingsble.adapter.BleDevicesAdapter;
import demo.ethings.com.ethingsble.configure.AppConfig;
import demo.ethings.com.ethingsble.R;
import demo.ethings.com.ethingsble.activity.BLESQLiteHelper;
import demo.ethings.com.ethingsble.activity.FindDeviceActivity;
import demo.ethings.com.ethingsble.fusion.SensorFusionActivity;
import demo.ethings.com.ethingsble.model.TagDevice;

/**
 * Activity to scan and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends AppCompatActivity
        implements ErrorDialog.ErrorDialogListener,
        EnableBluetoothDialog.EnableBluetoothDialogListener, AdapterView.OnItemClickListener {
    /**
     * Request to enable Bluetooth.
     */
    private static final int REQUEST_ENABLE_BT = 1;

    /**
     * Scan delay period.
     */
    private static final long SCAN_PERIOD = 3000L;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final String TAG = DeviceScanActivity.class.getName();
    private BLESQLiteHelper helper;
    private ArrayList<TagDevice> tagDevices;


    /**
     * BLE devices demo.ethings.com.ethingsble.adapter.
     */
    private BleDevicesAdapter leDeviceListAdapter;
    /**
     * BLE scanner.
     */
    private BleScanner scanner;

    /**
     * FAB.
     */
    private FloatingActionButton fab;
    /**
     * Device list.
     */
    private ListView listView;
    /**
     * Empty list view.
     */
    private View emptyView;
    /**
     * Scan progress view.
     */
    private View progress;
    /**
     * Device list demo.ethings.com.ethingsble.adapter.
     */
    private BluetoothAdapter bluetoothAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_devices);

        setContentView(R.layout.device_scan_activity);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        emptyView = findViewById(android.R.id.empty);
        progress = findViewById(android.R.id.progress);
        listView = (ListView) findViewById(android.R.id.list);
        listView.setEmptyView(emptyView);
        listView.setOnItemClickListener(this);
        //set helper:
        helper = new BLESQLiteHelper(this);
        tagDevices = helper.getAllDevices();

        fab = (FloatingActionButton) findViewById(R.id.scan_fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (scanner == null) {
                    return;
                }
                setScanActive(!scanner.isScanning());
            }
        });

        final int bleStatus = BleUtils.getBleStatus(getBaseContext());
        switch (bleStatus) {
            case BleUtils.STATUS_BLE_NOT_AVAILABLE:
                ErrorDialog.newInstance(R.string.dialog_error_no_ble)
                        .show(getFragmentManager(), ErrorDialog.TAG);
                return;
            case BleUtils.STATUS_BLUETOOTH_NOT_AVAILABLE:
                ErrorDialog.newInstance(R.string.dialog_error_no_bluetooth)
                        .show(getFragmentManager(), ErrorDialog.TAG);
                return;
            default:
                bluetoothAdapter = BleUtils.getBluetoothAdapter(getBaseContext());
        }

        if (bluetoothAdapter == null) {
            return;
        }

        // initialize scanner
        scanner = new BleScanner(bluetoothAdapter, new ScanProcessor());
        scanner.setScanPeriod(SCAN_PERIOD);

        requestLocation();
    }

    private void requestLocation() {
        //request permision:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    @RequiresApi(api = Build.VERSION_CODES.M)
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }
    }

    private int count = 0;

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            if (count < 2)
                                requestLocation();
                            else return;
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }

    private void setScanActive(boolean active) {
        if (active) {
            scanner.start();
            progress.setVisibility(View.VISIBLE);
            fab.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            scanner.stop();
            progress.setVisibility(View.INVISIBLE);
            fab.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_scan, menu);
        //noinspection PointlessBooleanExpression
        if (!AppConfig.LOCAL_SENSOR_FUSION) {
            menu.findItem(R.id.menu_demo).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_demo:
                final Intent demoIntent = new Intent(
                        getBaseContext(), SensorFusionActivity.class);
                startActivity(demoIntent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (bluetoothAdapter == null) {
            return;
        }

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!bluetoothAdapter.isEnabled()) {
            final Fragment f = getFragmentManager().findFragmentByTag(EnableBluetoothDialog.TAG);
            if (f == null) {
                new EnableBluetoothDialog().show(getFragmentManager(), EnableBluetoothDialog.TAG);
            }
            return;
        }

        init();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                finish();
            } else {
                init();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (scanner != null) {
            setScanActive(false);
        }
    }

    private void init() {
        if (leDeviceListAdapter == null) {
            leDeviceListAdapter = new BleDevicesAdapter(getBaseContext());
            listView.setAdapter(leDeviceListAdapter);
        }

        setScanActive(true);
    }

    @Override
    public void onEnableBluetooth(EnableBluetoothDialog f) {
        bluetoothAdapter.enable();
        init();
    }

    @Override
    public void onCancel(EnableBluetoothDialog f) {
        finish();
    }

    @Override
    public void onDismiss(ErrorDialog f) {
        finish();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final BluetoothDevice device = leDeviceListAdapter.getDevice(position);
        if (device == null) {
            return;
        }

        final Intent intent = new Intent(this, FindDeviceActivity.class);
        intent.putExtra(FindDeviceActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
        intent.putExtra(FindDeviceActivity.EXTRAS_DEVICE_CONNECT, false);
        startActivity(intent);

    }

    private boolean isWhiteList(BluetoothDevice device) {
        for (TagDevice tag : tagDevices) {
            if (tag.getAddress().equals(device.getAddress())) return true;
        }
        return false;
    }

    private class ScanProcessor implements BleScanner.BleScannerListener {

        /**
         * Scan map. Holds device which was found on ever scan.
         */
        private final Map<BluetoothDevice, Integer> scanMap = new HashMap<>();

        @Override
        public void onScanStarted() {
            listView.post(new Runnable() {
                @Override
                public void run() {
                    emptyView.setVisibility(View.GONE);
                }
            });
        }

        @Override
        public void onScanRepeat() {
            if (scanMap.isEmpty()) {
                scanner.stop();
            }
            updateDevices();
        }

        @Override
        public void onScanStopped() {
            listView.post(new Runnable() {
                @Override
                public void run() {
                    updateDevices();
                    if (leDeviceListAdapter.isEmpty()) {
                        emptyView.setVisibility(View.VISIBLE);
                    }
                    setScanActive(false);
                }
            });
        }

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi,
                             byte[] bytes) {
//            if (!isWhiteList(device))
                scanMap.put(device, rssi);
        }

        private synchronized void updateDevices() {
            // look for lost devices
            final List<BluetoothDevice> toRemove = new ArrayList<>();
            final List<BluetoothDevice> devices = new ArrayList<>(leDeviceListAdapter.getDevices());
            for (BluetoothDevice device : devices) {
                if (!scanMap.containsKey(device)) {
                    toRemove.add(device);
                }
            }
            // remove missed devices
            for (BluetoothDevice device : toRemove) {
                devices.remove(device);
            }
            // update device rssi
            final Map<BluetoothDevice, Integer> rssiMap = new HashMap<>();
            for (BluetoothDevice device : scanMap.keySet()) {
                final int rssi = scanMap.get(device);
                rssiMap.put(device, rssi);
            }

            scanMap.clear();

            listView.post(new Runnable() {
                @Override
                public void run() {
                    leDeviceListAdapter.setDevices(rssiMap);
                    leDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    }

}
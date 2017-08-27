/*
 * Partially adapted from https://github.com/jfedor2/nxt-remote-control
 */

package fi.robotuprising.rdd.dancerobotremote;

import java.util.Set;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class DeviceConnectorActivity extends Activity {

    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    private ArrayAdapter<String> pairedDevicesArrayAdapter;
    private ArrayAdapter<String> newDevicesArrayAdapter;
    private BluetoothAdapter bluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_list);
        setResult(Activity.RESULT_CANCELED);
        Button scanButton = findViewById(R.id.button_scan);
        scanButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                doDiscovery();
                v.setVisibility(View.GONE);
            }
        });

        pairedDevicesArrayAdapter = new ArrayAdapter<>(this,
                R.layout.device_name);
        newDevicesArrayAdapter = new ArrayAdapter<>(this,
                R.layout.device_name);

        ListView pairedListView = findViewById(R.id.paired_devices);
        pairedListView.setAdapter(pairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListener);

        ListView newDevicesListView = findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(newDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(receiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(receiver, filter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        boolean empty = true;

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if ((device.getBluetoothClass() != null)
                        && (device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.TOY_ROBOT)) {
                    pairedDevicesArrayAdapter.add(device.getName() + "\n"
                            + device.getAddress());
                    empty = false;
                }
            }
        }
        if (!empty) {
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            findViewById(R.id.no_devices).setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (bluetoothAdapter != null) {
            bluetoothAdapter.cancelDiscovery();
        }

        this.unregisterReceiver(receiver);
    }

    private void doDiscovery() {
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        bluetoothAdapter.startDiscovery();

        newDevicesArrayAdapter.clear();
        findViewById(R.id.title_new_devices).setVisibility(View.GONE);
        if (pairedDevicesArrayAdapter.getCount() == 0) {
            findViewById(R.id.no_devices).setVisibility(View.VISIBLE);
        }
    }

    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            bluetoothAdapter.cancelDiscovery();

            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if ((device.getBondState() != BluetoothDevice.BOND_BONDED)
                        && (device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.TOY_ROBOT)) {
                    newDevicesArrayAdapter.add(device.getName() + "\n"
                            + device.getAddress());
                    findViewById(R.id.title_new_devices).setVisibility(
                            View.VISIBLE);
                    findViewById(R.id.no_devices).setVisibility(View.GONE);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED
                    .equals(action)) {
                findViewById(R.id.button_scan).setVisibility(View.VISIBLE);
            }
        }
    };
}

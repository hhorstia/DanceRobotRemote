/*
 * Dance robot remote controller.
 * 
 * Partially adapted from https://github.com/jfedor2/nxt-remote-control
 */
package fi.robotuprising.rdd.dancerobotremote;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	public static final int MESSAGE_TOAST = 1;
	public static final int MESSAGE_STATE_CHANGE = 2;

	private static final int REQUEST_ENABLE_BT = 1;
	private static final int REQUEST_CONNECT_DEVICE = 2;

	private BluetoothAdapter bluetoothAdapter;
	private String deviceAddress;
	private DeviceMessageSender messageSender;
	private int state = DeviceMessageSender.STATE_NONE;
	private int savedState = DeviceMessageSender.STATE_NONE;
	private TextView stateDisplay;
	private boolean bluetoothAvailable = false;
	private boolean firstLaunch = true;
    private Menu menu;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		View area1 = findViewById(R.id.area1);
		area1.setOnTouchListener(new AreaOnTouchListener());
		View area2 = findViewById(R.id.area2);
		area2.setOnTouchListener(new AreaOnTouchListener());
		View area3 = findViewById(R.id.area3);
		area3.setOnTouchListener(new AreaOnTouchListener());

		if (!bluetoothAvailable) {
			bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

			if (bluetoothAdapter == null) {
				Toast.makeText(this, "Bluetooth is not available",
						Toast.LENGTH_LONG).show();
				finish();
				return;
			}
		}

		this.messageSender = new DeviceMessageSender(handler);

		stateDisplay = (TextView) findViewById(R.id.state_display);
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (!bluetoothAvailable) {
			if (!bluetoothAdapter.isEnabled()) {
				Intent enableIntent = new Intent(
						BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			} else {
				if (savedState == DeviceMessageSender.STATE_CONNECTED) {
					BluetoothDevice device = bluetoothAdapter
							.getRemoteDevice(deviceAddress);
					messageSender.connect(device);
				} else {
					if (firstLaunch) {
						firstLaunch = false;
						findDevice();
					}
				}
			}
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		savedState = state;
		messageSender.stop();
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        this.menu = menu;
        return true;
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_connect) {
            if (!bluetoothAvailable) {
                findDevice();
            } else {
                state = DeviceMessageSender.STATE_CONNECTED;
                displayState();
            }
            return true;
		}
		else if (id == R.id.action_disconnect) {
            messageSender.stop();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE:
			if (resultCode == Activity.RESULT_OK) {
				String address = data.getExtras().getString(
						DeviceConnectorActivity.EXTRA_DEVICE_ADDRESS);
				BluetoothDevice device = bluetoothAdapter
						.getRemoteDevice(address);
				deviceAddress = address;
				messageSender.connect(device);
			}
			break;
		}
	}

	private void displayState() {
		String stateText = null;
		int color = 0;
		switch (state) {
		case DeviceMessageSender.STATE_NONE:
			stateText = "Not connected";
			color = 0xff000000;
			stateDisplay.setVisibility(View.GONE);

            if (this.menu != null) {
                menu.findItem(R.id.action_connect).setEnabled(true);
                menu.findItem(R.id.action_disconnect).setEnabled(false);
            }

			break;
		case DeviceMessageSender.STATE_CONNECTING:
			stateText = "Connecting...";
			color = 0xff220000;
			stateDisplay.setVisibility(View.VISIBLE);
			break;
		case DeviceMessageSender.STATE_CONNECTED:
			stateText = "Connected";
			color = 0xff002200;

            if (this.menu != null) {
                menu.findItem(R.id.action_connect).setEnabled(false);
                menu.findItem(R.id.action_disconnect).setEnabled(true);
            }
			break;
		}
		stateDisplay.setText(stateText);
		stateDisplay.setTextColor(color);
	}

	private void findDevice() {
		Intent intent = new Intent(this, DeviceConnectorActivity.class);
		startActivityForResult(intent, REQUEST_CONNECT_DEVICE);
	}

	private final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(""),
						Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_STATE_CHANGE:
				state = msg.arg1;
				displayState();
				break;
			}
		}
	};

	private class AreaOnTouchListener implements OnTouchListener {

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			ControlsView area = (ControlsView) v;
			String name = getResources().getResourceName(v.getId());
			int areaNumber = Integer.parseInt(name.substring(name.length() - 1));
			int motorNumber = getMotorNumber(areaNumber);

			float y;
			int action = event.getAction();
			if ((action == MotionEvent.ACTION_DOWN) || (action == MotionEvent.ACTION_MOVE)) {
				byte level = 0;
				for (int i = 0; i < event.getPointerCount(); i++) {
					y = (event.getY(i) - area.origo) / area.power;
					updateStickPosition(y, areaNumber);
					if (y > 1.0f) {
						y = 1.0f;
					} else if (y < -1.0f) {
						y = -1.0f;
					}

					level = (byte) (y * 100);
				}
				messageSender.driveMotor(motorNumber, level);
			}
			else if ((action == MotionEvent.ACTION_UP) || (action == MotionEvent.ACTION_CANCEL)) {
				updateStickPosition(0, areaNumber);
				messageSender.driveMotor(motorNumber, (byte) 0);
			}
			return true;
		}
		
		private int getMotorNumber(int areaNumber) {
			switch (areaNumber) {
				case 1:
                    return 2;
                case 2:
                    return 0;
                case 3:
                    return 1;
                default:
					return 0;				
			}
		}
		
		private void updateStickPosition(float pos, int area) {
			View a1 = findViewById(getResources().getIdentifier("nuppi" + area + "1", "id", getPackageName()));
			a1.setScrollY(-1 * (int) (pos * 480));
			
			View a2 = findViewById(getResources().getIdentifier("nuppi" + area + "2", "id", getPackageName()));
			a2.setScrollY(-1 * (int) (pos * 500));

			View a3 = findViewById(getResources().getIdentifier("nuppi" + area + "3", "id", getPackageName()));
			a3.setScrollY(-1 * (int) (pos * 500));
		}

	}
}

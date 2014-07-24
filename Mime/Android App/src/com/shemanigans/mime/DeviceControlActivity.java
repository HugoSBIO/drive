package com.shemanigans.mime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity implements NameTextFileFragment.NameTextFileListener {
	
	//	implements NameTextFileFragment.NameTextFileListener

	private final static String TAG = DeviceControlActivity.class.getSimpleName();

	public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
	public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

	public final static String EXTRA_MESSAGE = "Export_To_Text";

	private TextView mConnectionState;
	private TextView mDataField;
	private String mDeviceName;
	private String mDeviceAddress;
	private ExpandableListView mGattServicesList;
	private BluetoothLeService mBluetoothLeService;
	private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
			new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
	private boolean mConnected = false;
	private BluetoothGattCharacteristic mNotifyCharacteristic;

	// Variables created by me

	public ArrayList<String> textFile = new ArrayList<String>();	
	private String textFileName = "AccelData";	
	private Calendar c = Calendar.getInstance();
	private boolean checkNamedTextFile = false;
	
	// Fragment to set name
	NameTextFileFragment dialog;
	
	private final String LIST_NAME = "NAME";
	private final String LIST_UUID = "UUID";
	private final int ONGOING_NOTIFICATION_ID = 1;

	// Code to manage Service lifecycle.
	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
			// Automatically connects to the device upon successful start-up initialization.
			mBluetoothLeService.connect(mDeviceAddress);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	// Handles various events fired by the Service.
	// ACTION_GATT_CONNECTED: connected to a GATT server.
	// ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
	// ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
	// ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
	//                        or notification operations.
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
				mConnected = true;
				updateConnectionState(R.string.connected);
				invalidateOptionsMenu();
			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
				mConnected = false;
				updateConnectionState(R.string.disconnected);
				invalidateOptionsMenu();
				clearUI();
			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
				// Show all the supported services and characteristics on the user interface.
				displayGattServices(mBluetoothLeService.getSupportedGattServices());
			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
				displayData(
						fixedLengthString("X", 8) 
						+ fixedLengthString("|", 8)
						+ fixedLengthString("Y", 8)
						+ fixedLengthString("|", 8)
						+ fixedLengthString("Z", 8)
						+ fixedLengthString("|", 8)
						+ fixedLengthString("�C", 8)
						+ fixedLengthString("|", 8)
						+ "\n"
						+ intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
				textFile.add(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
			}
		}
	};

	// If a given GATT characteristic is selected, check for supported features.  This sample
	// demonstrates 'Read' and 'Notify' features.  See
	// http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
	// list of supported characteristic features.
	private final ExpandableListView.OnChildClickListener servicesListClickListner =
			new ExpandableListView.OnChildClickListener() {
		@Override
		public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
				int childPosition, long id) {

			// Hide export to text button

			Button exportToText = (Button) findViewById(R.id.export_to_text);
			exportToText.setVisibility(View.GONE);
			
			Button clearTextFile = (Button) findViewById(R.id.clear_text_file);
			clearTextFile.setVisibility(View.GONE);

			if (mGattCharacteristics != null) {
				final BluetoothGattCharacteristic characteristic =
						mGattCharacteristics.get(groupPosition).get(childPosition);
				final int charaProp = characteristic.getProperties();
				if ((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {

					// If there is an active notification on a characteristic, clear
					// it first so it doesn't update the data field on the user interface.

					//also uncheck notifications switch that may have been previously checked.

					Switch enableNotifications = (Switch) findViewById(R.id.enable_notifications);
					enableNotifications.setChecked(false);
					enableNotifications.setVisibility(View.GONE); //

					if (mNotifyCharacteristic != null) {
						mBluetoothLeService.removeCharacteristicNotification(mNotifyCharacteristic, false);
						mNotifyCharacteristic = null;
					}
					mBluetoothLeService.readCharacteristic(characteristic);
				}
				if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {

					Switch enableNotifications = (Switch) findViewById(R.id.enable_notifications);
					enableNotifications.setVisibility(View.VISIBLE);

					mNotifyCharacteristic = characteristic;
				}
				return true;
			}
			return false;
		}
	};

	public void enableNotifications(View view) {	

		Switch enableNotifications = (Switch) findViewById(R.id.enable_notifications);
		enableNotifications.setVisibility(View.VISIBLE);

		// Turn off notifications.
		if(enableNotifications.isChecked() == false) {
			Button exportToText = (Button) findViewById(R.id.export_to_text);
			exportToText.setVisibility(View.VISIBLE);
			Button clearTextFile = (Button) findViewById(R.id.clear_text_file);
			clearTextFile.setVisibility(View.VISIBLE);
			mBluetoothLeService.removeCharacteristicNotification(mNotifyCharacteristic, false);
			mBluetoothLeService.stopForeground(true);
		}
		// Turn on notifications.

		else {
			//ic_action_name.png
			Notification notification = new Notification(R.drawable.mime_notification_icon, getText(R.string.sampling_data),System.currentTimeMillis());
			Intent notificationIntent = new Intent(this, DeviceControlActivity.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
			notification.setLatestEventInfo(this,"Mime is sampling data","Service connected", pendingIntent);
			
			mBluetoothLeService.startForeground(ONGOING_NOTIFICATION_ID, notification);
			
			Button exportToText = (Button) findViewById(R.id.export_to_text);
			exportToText.setVisibility(View.VISIBLE);
			Button clearTextFile = (Button) findViewById(R.id.clear_text_file);
			clearTextFile.setVisibility(View.VISIBLE);
			mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, true);			
		}
	}

	public void clearTextFile(View view) {		
		textFile = new ArrayList<String>();
		Toast.makeText(getBaseContext(),
				"Database sucessfully purged",
				Toast.LENGTH_SHORT).show();
	}
	
	public void exportToText(View view) {
		// write on SD card file data in the text box
		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
			String strDate = sdf.format(c.getTime());

			// check if User has changed filename
			if (checkNamedTextFile == false) {
				textFileName = "AccelData" + strDate;
			}
			else {
				textFileName = dialog.getName();
			}
			// set checkNamedTextFile back to false to revert back to default naming scheme.
			checkNamedTextFile = false;

//			File accelData = new File(Environment.getExternalStorageDirectory() 
//					+ textFileName + ".txt");
			
			File accelDataDir = new File(Environment.getExternalStorageDirectory() + "/Mime/");	
			
			accelDataDir.mkdirs();			

			File accelData = new File(accelDataDir, textFileName + ".txt");			
			
//			File accelData = new File("/sdcard/" 
//					+ textFileName + ".txt");

			accelData.createNewFile();
			FileOutputStream fOut = new FileOutputStream(accelData);
			OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);

			myOutWriter.append(
					fixedLengthString("X", 8) 
					+ fixedLengthString("|", 8)
					+ fixedLengthString("Y", 8)
					+ fixedLengthString("|", 8)
					+ fixedLengthString("Z", 8)
					+ fixedLengthString("|", 8)
					+ fixedLengthString("�C", 8)
					+ fixedLengthString("|", 8)
					+ "\n");

			for (int i = 0; i < textFile.size(); i++) {
				myOutWriter.append(textFile.get(i));
				myOutWriter.append("\n");
			}

			myOutWriter.close();
			fOut.close();
			Toast.makeText(getBaseContext(),
					"Done writing to SD Card " + textFileName + ".txt",
					Toast.LENGTH_SHORT).show();
			
			textFile = new ArrayList<String>();
		} 
		catch (Exception e) {
			Toast.makeText(getBaseContext(), e.getMessage(),
					Toast.LENGTH_SHORT).show();
		}
	}

	private void clearUI() {
		mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
		mDataField.setText(R.string.no_data);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.gatt_services_characteristics);

		final Intent intent = getIntent();
		mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
		mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

		// Sets up UI references.
		((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
		mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
		mGattServicesList.setOnChildClickListener(servicesListClickListner);
		mConnectionState = (TextView) findViewById(R.id.connection_state);
		mDataField = (TextView) findViewById(R.id.data_value);

		getActionBar().setTitle(mDeviceName);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		if (mBluetoothLeService != null) {
			final boolean result = mBluetoothLeService.connect(mDeviceAddress);
			Log.d(TAG, "Connect request result=" + result);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(mGattUpdateReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mServiceConnection);
		mBluetoothLeService = null;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.gatt_services, menu);
		if (mConnected) {    	
			menu.findItem(R.id.menu_refresh).setVisible(false);
			menu.findItem(R.id.menu_connect).setVisible(false);
			menu.findItem(R.id.menu_disconnect).setVisible(true);
		} else {
			menu.findItem(R.id.menu_refresh).setVisible(false);
			menu.findItem(R.id.menu_connect).setVisible(true);
			menu.findItem(R.id.menu_disconnect).setVisible(false);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.menu_connect:
			mBluetoothLeService.connect(mDeviceAddress);
			return true;
		case R.id.menu_disconnect:
			mBluetoothLeService.disconnect();
			//mBluetoothLeService.close();
			return true;
		case android.R.id.home:
			onBackPressed();
			return true;
		case R.id.name_text_file:
			setTextFileName();
			return true;
		case R.id.set_time:
			setNotificationTimer();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void updateConnectionState(final int resourceId) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mConnectionState.setText(resourceId);
			}
		});
	}

	private void displayData(String data) {
		if (data != null) {
			mDataField.setText(data);
		}
	}


	// Demonstrates how to iterate through the supported GATT Services/Characteristics.
	// In this sample, we populate the data structure that is bound to the ExpandableListView
	// on the UI.

	private void displayGattServices(List<BluetoothGattService> gattServices) {
		if (gattServices == null) return;
		String uuid = null;
		String unknownServiceString = getResources().getString(R.string.unknown_service);
		String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
		ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
		ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
		= new ArrayList<ArrayList<HashMap<String, String>>>();
		mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

		// Loops through available GATT Services.
		for (BluetoothGattService gattService : gattServices) {
			HashMap<String, String> currentServiceData = new HashMap<String, String>();
			uuid = gattService.getUuid().toString();
			currentServiceData.put(
					LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
			currentServiceData.put(LIST_UUID, uuid);
			gattServiceData.add(currentServiceData);

			ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
					new ArrayList<HashMap<String, String>>();
			List<BluetoothGattCharacteristic> gattCharacteristics =
					gattService.getCharacteristics();
			ArrayList<BluetoothGattCharacteristic> charas =
					new ArrayList<BluetoothGattCharacteristic>();

			// Loops through available Characteristics.
			for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
				charas.add(gattCharacteristic);
				HashMap<String, String> currentCharaData = new HashMap<String, String>();
				uuid = gattCharacteristic.getUuid().toString();
				currentCharaData.put(
						LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
				currentCharaData.put(LIST_UUID, uuid);
				gattCharacteristicGroupData.add(currentCharaData);
			}
			mGattCharacteristics.add(charas);
			gattCharacteristicData.add(gattCharacteristicGroupData);
		}

		SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
				this,
				gattServiceData,
				android.R.layout.simple_expandable_list_item_2,
				new String[] {LIST_NAME, LIST_UUID},
				new int[] { android.R.id.text1, android.R.id.text2 },
				gattCharacteristicData,
				android.R.layout.simple_expandable_list_item_2,
				new String[] {LIST_NAME, LIST_UUID},
				new int[] { android.R.id.text1, android.R.id.text2 }
				);
		mGattServicesList.setAdapter(gattServiceAdapter);
	}

	public static String fixedLengthString(String string, int length) {
		return String.format("%1$"+length+ "s", string);
	}

	public void setTextFileName() {
		showTextFileDialog();
	}
	
	public void setNotificationTimer() {
	}
	
    public void showTextFileDialog() {
        // Create an instance of the dialog fragment and show it
        dialog = new NameTextFileFragment();
        dialog.show(getFragmentManager(), "NameTextFileFragment");
    }

    // The dialog fragment receives a reference to this Activity through the
    // Fragment.onAttach() callback, which it uses to call the following methods
    // defined by the NoticeDialogFragment.NoticeDialogListener interface
    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
        // User touched the dialog's positive button
    	//checkNamedTextFile
    	checkNamedTextFile = true;
    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {
        // User touched the dialog's negative button
    	checkNamedTextFile = false;
    }
    
    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		return intentFilter;
	}
}
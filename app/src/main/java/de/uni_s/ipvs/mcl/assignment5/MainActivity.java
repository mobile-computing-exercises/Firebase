package de.uni_s.ipvs.mcl.assignment5;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    // UI elements
    private TextView temperatureText;
    private TextView humidityText;
    private ProgressBar humidityBar;
    // Permission code
    private final int REQUEST_ENABLE_BT = Activity.RESULT_OK;
    // BT objects
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothScanner;
    private BluetoothGatt bluetoothGatt;
    List<BluetoothGattCharacteristic> bufferList = new ArrayList<BluetoothGattCharacteristic>();
    // Sensor values
    private float tempValue, humValue;
    // Handlers
    private static final long SCAN_DELAY = 5000;
    private Handler userInterfaceUpdateHandler;
    private Handler scanHandler = new Handler();
    // Context
    private Context context;
    // BT UUIDs
    UUID notificationUUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");
    UUID weatherUUID = UUID.fromString("00000002-0000-0000-FDFD-FDFDFDFDFDFD");
    UUID tempUUID = UUID.fromString("00002A1C-0000-1000-8000-00805F9B34FB");
    UUID humUUID = UUID.fromString("00002A6F-0000-1000-8000-00805F9B34FB");
    // Firebase references
    private DatabaseReference uuidSubtree;
    private DatabaseReference locationSubtree;
    private DatabaseReference tempSubtree;
    private DatabaseReference humSubtree;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        setContentView(R.layout.activity_main);

        // Init UI and BT-Adapter
        initUI();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        userInterfaceUpdateHandler = new Handler(Looper.getMainLooper());

        // If BT not supported by device
        if (bluetoothAdapter == null) {
            Log.e("BLUETOOTH ERROR", "Device has no bluetooth capabilities!");
        }

        // If BT disabled
        if (!bluetoothAdapter.isEnabled()) {
            // Request BT
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        // Setup BT
        setUpBluetooth();

        // Setup Firebase-References
        DatabaseReference mRef = FirebaseDatabase.getInstance().getReference().child("teams").child("10");
        uuidSubtree = mRef.child("uuid").child("00000002-0000-0000-FDFD-FDFDFDFDFDFD");
        tempSubtree = uuidSubtree.child("00002A1C-0000-1000-8000-00805F9B34FB");
        humSubtree = uuidSubtree.child("00002A6F-0000-1000-8000-00805F9B34FB");
        locationSubtree = mRef.child("location").child("Stuttgart");

        // Listener for Temp
        tempSubtree.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String outputText = getLastUpdate(dataSnapshot, "temp");
                temperatureText.setText(outputText);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w("Database", "Failed to read value.", error.toException());
            }
        });

        // Listener for Temp
        humSubtree.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String outputText = getLastUpdate(dataSnapshot, "hum");
                humidityText.setText(outputText);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w("Database", "Failed to read value.", error.toException());
            }
        });

        // Test-Value
        writeValueInDatabase(21.8f, "temp");
        writeValueInDatabase(74, "hum");
    }

    /**
     * This method initializes the UI.
     */
    private void initUI() {
        temperatureText = findViewById(R.id.temperatureText);
        humidityText = findViewById(R.id.humidityText);
        humidityBar = findViewById(R.id.humidityBar);
    }

    /**
     * This method updates the UI with current sensor values.
     */
    private void refreshUI() {
        temperatureText.setText("Current temperature: " + tempValue + "°C");
        humidityText.setText("Current humidity: " + humValue + "%");
        humidityBar.setProgress(((int) humValue));
    }

    /**
     * This method writes the new values from a sensor to appropriate nodes inside the database.
     * Currently only the temperature is stored inside the location node.
     *
     * @param value
     * @param sensor
     */
    private void writeValueInDatabase(float value, String sensor) {
        if (sensor.equals("temp")) {
            tempSubtree.child(String.valueOf(System.currentTimeMillis())).setValue(value);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String currentDate = sdf.format(new Date());
            locationSubtree.child(currentDate).child(String.valueOf(System.currentTimeMillis())).setValue(value);
        } else {
            humSubtree.child(String.valueOf(System.currentTimeMillis())).setValue(value);
        }
    }

    /**
     * This method returns the string that should be output to the gui.
     * This output includes the newest and previous values from any of the two sensors.
     * @param parentNode
     * @param sensorType
     * @return
     */
    private String getLastUpdate(DataSnapshot parentNode, String sensorType) {
        // Build output depending on sensor
        String output;
        output = sensorType.equals("temp") ? "Temperature:" : "Humidity:";
        output = output + System.lineSeparator();
        // Init date format
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

        // Init Database data
        List<DataSnapshot> children = new ArrayList<DataSnapshot>();
        DataSnapshot currentNode = null;
        DataSnapshot previousNode = null;
        DataSnapshot previousNodeDiffVal = null;

        // Order all Nodes correctly
        for(DataSnapshot childNode : parentNode.getChildren()) {
            children.add(0, childNode);
        }

        // Iterate children from newest to oldest
        for (DataSnapshot childNode: children) {
            // First node is always the newest
            if (currentNode == null) {
                currentNode = childNode;
                continue;
            }
            // Second node is always the previous
            if (previousNode == null) {
                previousNode = childNode;
                // If previous node has different value than the newest
                if (childNode.getValue(Float.class).compareTo(currentNode.getValue(Float.class)) != 0) {
                    // Stop searching
                    break;
                }
                continue;
            }
            // Search for previous node with different value
            if (previousNodeDiffVal == null){
                // If found
                if (childNode.getValue(Float.class).compareTo(currentNode.getValue(Float.class)) != 0) {
                    previousNodeDiffVal = childNode;
                    // Stop searching
                    break;
                }
            }
        }

        // Build string for output depending on the findings
        if (currentNode != null) {
            output = output + "Last update at " + sdf.format(new Date(Long.parseLong(currentNode.getKey()))) + " with " + currentNode.getValue(Float.class) + "°C" + System.lineSeparator();
        } else {
            return output + "No temperature data";
        }

        if (previousNode != null) {
            output = output + "Previous update at " + sdf.format(new Date(Long.parseLong(previousNode.getKey()))) + " with " + previousNode.getValue(Float.class) + "°C" + System.lineSeparator();
        } else {
            return output;
        }

        if (previousNodeDiffVal != null){
            output = output + "Previous update with different value at " + sdf.format(new Date(Long.parseLong(previousNodeDiffVal.getKey()))) + " with " + previousNodeDiffVal.getValue(Float.class) + "°C" + System.lineSeparator();
        } else {
            return output;
        }

        return output;
    }

    /**
     * This method sets up bluetooth.
     */
    private void setUpBluetooth() {
        // Init BT manager
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        // Init BT scanner
        bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();
        // Scan devices
        scanLeDevice(true);
    }

    /**
     * This method is used for scanning the BT devices.
     * @param enable (Used to activate/deactivate scanning)
     */
    private void scanLeDevice(boolean enable) {
        // If scan should be activated
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            scanHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    bluetoothScanner.stopScan(scanCallback);
                }
            }, SCAN_DELAY);

            // create filters for the scan
            ScanFilter weatherFilter = new ScanFilter.Builder()
                    .setDeviceName("IPVSWeather").build();

            List<ScanFilter> scanFilterList = new ArrayList<ScanFilter>();
            scanFilterList.add(weatherFilter);

            // create the settings for the scan
            ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
            settingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
            settingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);

            bluetoothScanner.startScan(scanFilterList, settingsBuilder.build(), scanCallback);
            // If scan should be deactivated
        } else {
            bluetoothScanner.stopScan(scanCallback);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Disconnect from devices
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public synchronized void onScanResult(int callbackType, ScanResult result) {
            if (bluetoothGatt == null && result.getDevice().getName().equals("IPVSWeather")) {
                bluetoothGatt = result.getDevice().connectGatt(context, false, gattCallback);
            }

            super.onScanResult(callbackType, result);

            //stop scanning
            if (bluetoothGatt != null)
                scanLeDevice(false);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            bufferList.add(characteristic);
            if (bufferList.size() == 1)
                gatt.readCharacteristic(bufferList.get(0));
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            // If data returned by temperature service
            if (characteristic.getUuid().equals(tempUUID)) {
                int tempSensorValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1);
                tempValue = tempSensorValue / 100;
                writeValueInDatabase(tempValue, "temp");
            }
            // If data returned by humidity service
            if (characteristic.getUuid().equals(humUUID)) {
                int humSensorValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
                humValue = humSensorValue / 100;
                writeValueInDatabase(humValue, "hum");
            }

            // Remove the element we just read from the buffer and read the next one (if present)
            if (bufferList.size() >= 1) {
                bufferList.remove(0);
                if (bufferList.size() >= 1)
                    gatt.readCharacteristic(bufferList.get(0));
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            BluetoothGattCharacteristic tempCharacteristic = gatt.getService(weatherUUID).getCharacteristic(tempUUID);

            BluetoothGattDescriptor tempDescriptor = tempCharacteristic.getDescriptor(notificationUUID);
            tempDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.setCharacteristicNotification(tempCharacteristic, true);
            gatt.writeDescriptor(tempDescriptor);
        }

        BluetoothGattCharacteristic humCharacteristic;

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

            if (humCharacteristic == null) {
                humCharacteristic = gatt.getService(weatherUUID).getCharacteristic(humUUID);

                BluetoothGattDescriptor humDescriptor = humCharacteristic.getDescriptor(notificationUUID);
                humDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.setCharacteristicNotification(humCharacteristic, true);
                gatt.writeDescriptor(humDescriptor);
            } else {
                userInterfaceUpdateHandler.post(new Runnable() {
                    public void run() {
                        Toast toast = Toast.makeText(context, "Found weather sensor", Toast.LENGTH_SHORT);
                        toast.show();
                    }
                });
            }
        }
    };
}

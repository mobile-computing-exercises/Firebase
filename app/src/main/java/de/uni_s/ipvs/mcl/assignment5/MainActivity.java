package de.uni_s.ipvs.mcl.assignment5;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.webkit.DateSorter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    // UI elements
    private TextView temperatureText;
    private TextView humidityText;
    private TextView averageText;
    private TextView sensorText;
    private Spinner locationPicker;
    private Spinner sensorPicker;
    // Permission code
    private final int REQUEST_ENABLE_BT = Activity.RESULT_OK;
    private static final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 99;
    // BT objects
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothScanner;
    private BluetoothGatt bluetoothGatt;
    List<BluetoothGattCharacteristic> bufferList = new ArrayList<BluetoothGattCharacteristic>();
    // Sensor values
    private float tempValue, humValue;
    private String location = "Stuttgart";
    private String uuId;
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
    DatabaseReference mRef;
    private DatabaseReference uuidSubtree;
    private DatabaseReference locationSubtree;
    private DatabaseReference tempSubtree;
    private DatabaseReference humSubtree;
    // Dateformats
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    //flags
    boolean isTemperature;
    boolean isHumidity;

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

        //check permission
        if (Build.VERSION.SDK_INT >= 23) {
            // Marshmallow+ Permission APIs
            permissionCheck();
        }

        // Setup BT
        setUpBluetooth();

        // Setup Firebase-References
        mRef = FirebaseDatabase.getInstance().getReference().child("teams").child("10");
        uuidSubtree = mRef.child("uuid").child("00000002-0000-0000-FDFD-FDFDFDFDFDFD");
        tempSubtree = uuidSubtree.child("00002A1C-0000-1000-8000-00805F9B34FB");
        humSubtree = uuidSubtree.child("00002A6F-0000-1000-8000-00805F9B34FB");
        locationSubtree = mRef.child("location");

        setupLocationSpinner();

        setUpUuIdSpinner();

        setUpListeners();




    }

    private void setUpListeners() {

        uuidSubtree.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                updateUuidPicker(dataSnapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        // Listener for Temp
        tempSubtree.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String outputText = getLastUpdate(dataSnapshot, "temp");
                temperatureText.setText(outputText);
                if(isTemperature)
                    sensorText.setText(getLatestUpdate(dataSnapshot, "temp"));

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
                if(isHumidity)
                    sensorText.setText(getLatestUpdate(dataSnapshot, "hum"));
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w("Database", "Failed to read value.", error.toException());
            }
        });

        locationSubtree.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                updateLocationPicker(dataSnapshot);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w("Database", "Failed to read value.", error.toException());
            }
        });

        // Listener for Temp
        locationSubtree.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String outputText = getAvarageForLocation(dataSnapshot);
                averageText.setText(outputText);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w("Database", "Failed to read value.", error.toException());
            }
        });
    }

    private void setUpUuIdSpinner() {

        sensorPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {


                if(parent.getSelectedItem().toString().equals("00002A1C-0000-1000-8000-00805F9B34FB"))
                {
                    Log.d("SpinnerText", "onItemSelected: "+parent.getSelectedItem().toString());
                    isTemperature=true;
                    isHumidity=false;
                    tempSubtree.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            sensorText.setText(getLatestUpdate(dataSnapshot, "temp"));

                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {

                        }
                    });
                }
                else
                {
                    isTemperature=false;
                    isHumidity=true;
                    humSubtree.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            sensorText.setText(getLatestUpdate(dataSnapshot, "hum"));
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {

                        }
                    });
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

    }

    private void setupLocationSpinner() {

        locationPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.i("PICKER", "ITEM SELECTED: " + parent.getSelectedItem().toString());
                location = parent.getSelectedItem().toString();

                locationSubtree.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        String outputText = getAvarageForLocation(dataSnapshot);
                        averageText.setText(outputText);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        // Failed to read value
                        Log.w("Database", "Failed to read value.", error.toException());
                    }
                });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }



    /**
     * This method initializes the UI.
     */
    private void initUI() {
        temperatureText = findViewById(R.id.temperatureText);
        humidityText = findViewById(R.id.humidityText);
        averageText = findViewById(R.id.averageText);
        locationPicker = findViewById(R.id.locationPicker);
        sensorPicker=findViewById(R.id.SensorPicker);
        sensorText=findViewById(R.id.sensorText);
    }

    /**
     * This method updates the UI with current sensor values.
     */
    private void refreshUI() {
        temperatureText.setText("Current temperature: " + tempValue + "°C");
        humidityText.setText("Current humidity: " + humValue + "%");
    }

    private void updateLocationPicker(DataSnapshot locationNode) {
        List<String> locations = new ArrayList<String>();
        for (DataSnapshot singleLocation : locationNode.getChildren()) {
            locations.add(singleLocation.getKey());
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, locations);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        locationPicker.setAdapter(spinnerAdapter);
    }


    private void updateUuidPicker(DataSnapshot uuIdNode) {
        List<String> uuId = new ArrayList<String>();
        for (DataSnapshot singleUuId : uuIdNode.getChildren()) {
            uuId.add(singleUuId.getKey());
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, uuId);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sensorPicker.setAdapter(spinnerAdapter);
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
            locationSubtree.child("Stuttgart").child(dateFormat.format(new Date())).child(String.valueOf(System.currentTimeMillis())).setValue(value);
        } else {
            humSubtree.child(String.valueOf(System.currentTimeMillis())).setValue(value);
        }
    }

    /**
     * This method returns the string that should be output to the gui.
     * This output includes the newest and previous values from any of the two sensors.
     *
     * @param parentNode
     * @param sensorType
     * @return
     */
    private String getLastUpdate(DataSnapshot parentNode, String sensorType) {
        // Build output depending on sensor
        String output = sensorType.equals("temp") ? "Temperature:" : "Humidity:";
        output = output + System.lineSeparator();
        String unit = sensorType.equals("temp") ? "°C" : "%";

        // Init Database data
        List<DataSnapshot> children = new ArrayList<DataSnapshot>();
        DataSnapshot currentNode = null;
        DataSnapshot previousNode = null;
        DataSnapshot previousNodeDiffVal = null;

        // Order all Nodes correctly
        for (DataSnapshot childNode : parentNode.getChildren()) {
            children.add(0, childNode);
        }

        // Iterate children from newest to oldest
        for (DataSnapshot childNode : children) {
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
            if (previousNodeDiffVal == null) {
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
            output = output + "Last update at " + timeFormat.format(new Date(Long.parseLong(currentNode.getKey()))) + " with " + currentNode.getValue(Float.class) + unit + System.lineSeparator();
        } else {
            return output + "No temperature data";
        }

        if (previousNode != null) {
            output = output + "Previous update at " + timeFormat.format(new Date(Long.parseLong(previousNode.getKey()))) + " with " + previousNode.getValue(Float.class) + unit + System.lineSeparator();
        } else {
            return output;
        }

        if (previousNodeDiffVal != null) {
            output = output + "Previous update wdv at " + timeFormat.format(new Date(Long.parseLong(previousNodeDiffVal.getKey()))) + " with " + previousNodeDiffVal.getValue(Float.class) + unit + System.lineSeparator();
        } else {
            return output;
        }

        return output;
    }


    /**
     * This method returns the string that should be output to the gui.
     * This output includes the newest values only from any of the two sensors.
     *
     * @param parentNode
     * @param sensorType
     * @return
     */

    private String getLatestUpdate(DataSnapshot parentNode, String sensorType) {
        // Build output depending on sensor
        String output = sensorType.equals("temp") ? "Temperature:" : "Humidity:";
        output = output + System.lineSeparator();
        String unit = sensorType.equals("temp") ? "°C" : "%";

        // Init Database data
        List<DataSnapshot> children = new ArrayList<DataSnapshot>();
        DataSnapshot currentNode = null;

        // Order all Nodes correctly
        for (DataSnapshot childNode : parentNode.getChildren()) {
            children.add(0, childNode);
        }

        // Iterate children from newest to oldest
        for (DataSnapshot childNode : children) {
            // First node is always the newest
            if (currentNode == null) {
                currentNode = childNode;
                continue;
            }
        }

        // Build string for output depending on the findings
        if (currentNode != null) {
            output = output + "Last update at " + timeFormat.format(new Date(Long.parseLong(currentNode.getKey()))) + " with " + currentNode.getValue(Float.class) + unit + System.lineSeparator();
        } else {
            return output + "No temperature data";
        }

        return output;
    }



    /**
     * This method calculates the average temperature of a given date at a given location.
     *
     * @param locationData
     * @return
     */
    private String getAvarageForLocation(DataSnapshot locationData) {
        String output = "Todays avarage temperature in '" + location + "' is ";
        float average = 0.0f;

        // Calculate avarage
        for (DataSnapshot locationNode : locationData.getChildren()) {
            if (locationNode.getKey().equals(location)) {
                for (DataSnapshot child : locationNode.getChildren()) {
                    if (child.getKey().equals(dateFormat.format(new Date()))) {
                        for (DataSnapshot dateChild : child.getChildren()) {
                            average += dateChild.getValue(Float.class);
                        }
                        average /= child.getChildrenCount();
                        break;
                    }
                }
            }
        }

        return output + average + "°C";
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
     *
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
                Log.i("BTSENSOR", "GOT TEMP VALUES");
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



    //code for runtime permission
    @TargetApi(Build.VERSION_CODES.M)
    private void permissionCheck() {
        List<String> permissionsNeeded = new ArrayList<String>();

        final List<String> permissionsList = new ArrayList<String>();
        if (!addPermission(permissionsList, Manifest.permission.ACCESS_FINE_LOCATION))
            permissionsNeeded.add("Show Location");

        if (permissionsList.size() > 0) {
            if (permissionsNeeded.size() > 0) {

                // Need Rationale
                String message = "App need access to " + permissionsNeeded.get(0);

                for (int i = 1; i < permissionsNeeded.size(); i++)
                    message = message + ", " + permissionsNeeded.get(i);

                showMessageOKCancel(message,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                requestPermissions(permissionsList.toArray(new String[permissionsList.size()]),
                                        REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
                            }
                        });
                return;
            }
            requestPermissions(permissionsList.toArray(new String[permissionsList.size()]),
                    REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
            return;
        }
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean addPermission(List<String> permissionsList, String permission) {

        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
            // Check for Rationale Option
            if (!shouldShowRequestPermissionRationale(permission))
                return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: {
                Map<String, Integer> perms = new HashMap<String, Integer>();
                // Initial
                perms.put(Manifest.permission.ACCESS_FINE_LOCATION, PackageManager.PERMISSION_GRANTED);


                // Fill with results
                for (int i = 0; i < permissions.length; i++)
                    perms.put(permissions[i], grantResults[i]);

                // Check for ACCESS_FINE_LOCATION
                if (perms.get(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                    Toast.makeText(this, "One or More Permissions are DENIED ", Toast.LENGTH_SHORT)
                            .show();


                }
            }
            break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}

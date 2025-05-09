package com.example.irrigationapp1;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.NonNull; /* Helps prevent null pointer errors */
import androidx.appcompat.app.AppCompatActivity; /* Base class for all modern Android activities using the support library (Material UI, themes, etc.) */

import com.github.mikephil.charting.charts.PieChart; /* Main view class for displaying a Pie Chart. */
import com.github.mikephil.charting.data.PieData; /* The actual data object given to the chart (contains PieDataSet) */
import com.github.mikephil.charting.data.PieDataSet; /* A collection of PieEntry values (the "dataset" of slices) */
import com.github.mikephil.charting.data.PieEntry; /* Represents a single slice in the Pie chart (e.g., 70% moisture) */

import com.google.firebase.database.DataSnapshot; /* Represents a single snapshot of the data at a given location. */
import com.google.firebase.database.DatabaseError; /* Returned in case of a read/write failure (e.g., permission denied). */
import com.google.firebase.database.DatabaseReference; /* A reference to a specific path in the database (e.g., moisture/sensor1). */
import com.google.firebase.database.FirebaseDatabase; /* The main entry point to your Firebase database. */
import com.google.firebase.database.ValueEventListener; /* Listener that triggers when data changes at a given path (for real-time updates). */
import com.google.android.material.bottomnavigation.BottomNavigationView; /* For using the Bottom Navigation Bar component. */

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    /* UI elements and Firebase reference declarations */
    private TextView weatherTemp, weatherHumidity, heatIndex, modeStatus;
    private PieChart moistureGauge1, moistureGauge2, moistureGauge3, moistureGauge4;
    private Button toggleModeBtn;
    private Button[] relayButtons = new Button[4];
    private DatabaseReference databaseReference;
    private String currentMode = "Manual"; /* Default mode */

    /* Expected atmospheric sensor value ranges */
    private static final float MIN_TEMP = 0.0f;
    private static final float MAX_TEMP = 50.0f;
    private static final float MIN_HUMIDITY = 20.0f;
    private static final float MAX_HUMIDITY = 90.0f;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* Enable offline persistence for Firebase */
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (RuntimeException e) {
            Log.d("Firebase", "Persistence already enabled");
        }

        /* Initialize views and Firebase database */
        initializeViews();
        initializeFirebase();

        /* Setup pie charts for moisture sensors */
        setupMoistureGauges();

        /* Start listening for Firebase data changes */
        startRealtimeUpdates();

        /* Set up button listeners (mode switch & relay control) */
        setupButtonLogic();

        /* Setup bottom navigation bar */
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_home); /* highlight Home */

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                /* Already on home */
                return true;
            } else if (itemId == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.nav_about) {
                startActivity(new Intent(this, AboutActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }

            return false;
        });

    }


    private void initializeViews() {
        /* Find all views by ID */
        weatherTemp = findViewById(R.id.weatherTemp);
        weatherHumidity = findViewById(R.id.weatherHumidity);
        heatIndex = findViewById(R.id.heatIndex);
        toggleModeBtn = findViewById(R.id.toggleModeBtn);

        moistureGauge1 = findViewById(R.id.moistureGauge1);
        moistureGauge2 = findViewById(R.id.moistureGauge2);
        moistureGauge3 = findViewById(R.id.moistureGauge3);
        moistureGauge4 = findViewById(R.id.moistureGauge4);

        relayButtons[0] = findViewById(R.id.relay1Btn);
        relayButtons[1] = findViewById(R.id.relay2Btn);
        relayButtons[2] = findViewById(R.id.relay3Btn);
        relayButtons[3] = findViewById(R.id.relay4Btn);
    }

    private void initializeFirebase() {
        databaseReference = FirebaseDatabase.getInstance().getReference(); /* Root Firebase reference */
        databaseReference.child("moisture").keepSynced(true); /* Sync moisture data offline */
        databaseReference.child("atmospheric").keepSynced(true); /* Sync atmospheric data offline */
    }

    private void setupMoistureGauges() {
        PieChart[] gauges = {moistureGauge1, moistureGauge2, moistureGauge3, moistureGauge4};

        /* Like a for loop count in C */
        for (PieChart gauge : gauges) {
            /* Basic chart setup */
            gauge.setDrawHoleEnabled(true);
            gauge.setHoleRadius(75f);
            gauge.setTransparentCircleRadius(80f);
            gauge.setTransparentCircleColor(Color.WHITE);
            gauge.setRotationAngle(180f);
            gauge.setRotationEnabled(false);
            gauge.getDescription().setEnabled(false);
            gauge.setCenterText("--%");
            gauge.setCenterTextSize(16f);
            gauge.setCenterTextColor(Color.BLACK);
            gauge.setDrawCenterText(true);
            gauge.getLegend().setEnabled(false);

            /* Data setup */
            List<PieEntry> entries = new ArrayList<>();
            entries.add(new PieEntry(0f)); /* Moisture value */
            entries.add(new PieEntry(100f)); /* Remainder (empty part) */

            PieDataSet dataSet = new PieDataSet(entries, ""); /* The dataset have no label in the chart legend */
            /* Blue for the moisture portion, light gray for the remaining part */
            dataSet.setColors(Color.rgb(0, 150, 255), Color.rgb(230, 230, 230));
            dataSet.setDrawValues(false); /* No need for labels */

            PieData data = new PieData(dataSet);
            gauge.setData(data);
            gauge.invalidate(); /* Refresh chart */
        }
    }

    private void startRealtimeUpdates() {
        /* Listen to the entire database */
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    updateAtmosphericReadings(dataSnapshot.child("atmospheric")); /* Update atmospheric readings */
                    updateMoistureReadings(dataSnapshot.child("moisture")); /* Update moisture levels */

                    /* Get mode (Manual or Auto) */
                    String mode = dataSnapshot.child("control").child("mode").getValue(String.class);
                    if (mode != null) {
                        currentMode = mode;
                        toggleModeBtn.setText(mode); /* Set button text to current mode */

                        /* Set color based on mode */
                        if (mode.equals("Auto")) {
                            toggleModeBtn.setBackgroundColor(Color.parseColor("#2E7D32")); /* Green */
                            toggleModeBtn.setTextColor(Color.WHITE);
                        } else {
                            toggleModeBtn.setBackgroundColor(Color.parseColor("#D32F2F")); /* Red */
                            toggleModeBtn.setTextColor(Color.WHITE);
                        }

                        /* Enable/disable manual pump buttons based on mode */
                        for (Button relayBtn : relayButtons) {
                            boolean isManual = mode.equals("Manual");
                            relayBtn.setEnabled(isManual);

                            /* Grey out when in Auto mode */
                            if (isManual) {
                                relayBtn.setAlpha(1.0f); /* Fully visible */
                            } else {
                                relayBtn.setAlpha(0.5f); /* Slightly transparent */
                            }
                        }

                    }

                    /* Update relay states and button colors */
                    for (int i = 0; i < 4; i++) {
                        String sensorKey = "sensor" + (i + 1);
                        String relayState = dataSnapshot.child("control")
                                .child("relayStates")
                                .child(sensorKey)
                                .getValue(String.class);
                        if (relayState != null) {
                            relayButtons[i].setText("PUMP " + (i + 1) + ": " + relayState);

                            if ("ON".equalsIgnoreCase(relayState)) {
                                relayButtons[i].setBackgroundColor(Color.parseColor("#4CAF50")); /* Green */
                                relayButtons[i].setTextColor(Color.WHITE);
                            } else {
                                relayButtons[i].setBackgroundColor(Color.parseColor("#D32F2F")); /* Red */
                                relayButtons[i].setTextColor(Color.WHITE);
                            }
                        }
                    }
                }
            }

            /* Show error message if Firebase fails */
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this,
                        "Failed to read sensor data: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupButtonLogic() {
        /* Toggle mode button click handler */
        toggleModeBtn.setOnClickListener(v -> {
            String newMode = currentMode.equals("Manual") ? "Auto" : "Manual";

            if (newMode.equals("Manual")) {
                /* Check relay states before switching to Manual */
                databaseReference.child("control").child("relayStates").addListenerForSingleValueEvent(
                        new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                boolean anyPumpOn = false;

                                for (int i = 1; i <= 4; i++) {
                                    String state = snapshot.child("sensor" + i).getValue(String.class);
                                    if ("ON".equalsIgnoreCase(state)) {
                                        anyPumpOn = true;
                                        break;
                                    }
                                }

                                if (anyPumpOn) {
                                    /* Show confirmation dialog */
                                    new android.app.AlertDialog.Builder(MainActivity.this)
                                            .setTitle("Pumps Running")
                                            .setMessage("Some pumps are still running. Do you want to turn them OFF?")
                                            .setPositiveButton("Yes", (dialog, which) -> {
                                                /* Turn off all pumps */
                                                for (int i = 1; i <= 4; i++) {
                                                    databaseReference.child("control")
                                                            .child("relayStates")
                                                            .child("sensor" + i)
                                                            .setValue("OFF");
                                                }
                                                /* Switch to Manual */
                                                databaseReference.child("control").child("mode").setValue(newMode);
                                            })
                                            .setNegativeButton("No", (dialog, which) -> {
                                                /* Just switch mode, leave pumps as is */
                                                databaseReference.child("control").child("mode").setValue(newMode);
                                            })
                                            .show();
                                } else {
                                    /* No pumps ON, just switch to Manual */
                                    databaseReference.child("control").child("mode").setValue(newMode);
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Toast.makeText(MainActivity.this, "Error checking relay states", Toast.LENGTH_SHORT).show();
                            }
                        }
                );
            } else {
                /* Switching to Auto — just update mode */
                databaseReference.child("control").child("mode").setValue(newMode);
            }
        });


        /* Relay button click logic */
        for (int i = 0; i < relayButtons.length; i++) {
            int index = i;
            /* Set click listener for each relay button */
            relayButtons[i].setOnClickListener(v -> {
                if (!currentMode.equals("Manual")) {
                    Toast.makeText(this, "Switch to Manual mode to control relays.", Toast.LENGTH_SHORT).show();
                    return;
                }

                String sensorKey = "sensor" + (index + 1);
                String currentText = relayButtons[index].getText().toString();
                String newState = currentText.contains("ON") ? "OFF" : "ON"; /* Determine the new state: toggle between ON and OFF */

                /* Update the new state in Firebase under the correct sensor path */
                FirebaseDatabase.getInstance()
                        .getReference("control/relayStates/" + sensorKey)
                        .setValue(newState);
            });
        }
    }

    private void updateAtmosphericReadings(DataSnapshot atmosphericData) {
        try {
            /* Get temperature, humidity, and heat index values from Firebase */
            double temperature = atmosphericData.child("temperature").getValue(Double.class);
            double humidity = atmosphericData.child("humidity").getValue(Double.class);
            double heatIndexValue = atmosphericData.child("heatIndex").getValue(Double.class);

            /* Only update the UI if readings are within valid range */
            if (isValidReading(temperature, MIN_TEMP, MAX_TEMP) &&
                    isValidReading(humidity, MIN_HUMIDITY, MAX_HUMIDITY)) {
                /* Set text on the weather display fields */
                weatherTemp.setText(String.format("Temperature: %.1f°C", temperature));
                weatherHumidity.setText(String.format("Humidity: %.1f%%", humidity));
                heatIndex.setText(String.format("Heat Index: %.1f°C", heatIndexValue));
            } else {
                Toast.makeText(this, "Invalid sensor readings detected", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error processing atmospheric data", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateMoistureReadings(DataSnapshot moistureData) {
        try {
            Float[] moistureValues = new Float[4];
            /* Create array of chart views for easier iteration */
            PieChart[] gauges = {moistureGauge1, moistureGauge2, moistureGauge3, moistureGauge4};

            for (int i = 0; i < 4; i++) {
                Float newValue = moistureData.child("sensor" + (i + 1)).getValue(Float.class);
                if (newValue != null) {
                    updateMoistureGauge(gauges[i], newValue);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error processing moisture data", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isValidReading(double value, double min, double max) {
        return value >= min && value <= max;
    }

    /* Update a specific PieChart gauge with a new moisture value */
    private void updateMoistureGauge(PieChart gauge, float value) {

        /* Create pie entries: actual value and remaining portion */
        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(value));
        entries.add(new PieEntry(100 - value));

        /* Set up the chart dataset with colors */
        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(Color.rgb(0, 150, 255), Color.rgb(230, 230, 230));
        dataSet.setDrawValues(false);

        /* Apply data to the chart */
        PieData data = new PieData(dataSet);
        gauge.setData(data);
        gauge.setCenterText(String.format("%.1f%%", value));
        gauge.invalidate();
    }
}

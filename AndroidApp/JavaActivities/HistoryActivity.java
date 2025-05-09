package com.example.irrigationapp1;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class HistoryActivity extends AppCompatActivity {
    private LineChart moistureChart, atmosphericChart;
    private Spinner dateRangeSpinner;
    private DatabaseReference databaseReference;

    private long timeFilterStart = 0L;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        /* Initialize views */
        moistureChart = findViewById(R.id.moistureChart);
        atmosphericChart = findViewById(R.id.atmosphericChart);
        dateRangeSpinner = findViewById(R.id.dateRangeSpinner);

        databaseReference = FirebaseDatabase.getInstance().getReference("logs");

        setupDateRangeFilter();

        /* Bottom Nav Setup */
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_history); /* highlight History */

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_history) {
                return true;
            } else if (itemId == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
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

    private void setupDateRangeFilter() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"All Time", "Last 24 Hours", "Last 7 Days"}
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dateRangeSpinner.setAdapter(adapter);

        dateRangeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                long currentTime = System.currentTimeMillis() / 1000;

                switch (position) {
                    case 1:
                        timeFilterStart = currentTime - 86400; /* Last 24h */
                        break;
                    case 2:
                        timeFilterStart = currentTime - 604800; /* Last 7 days */
                        break;
                    default:
                        timeFilterStart = 0; /* All time */
                        break;
                }

                loadMoistureData();
                loadAtmosphericData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        /* Initial load */
        loadMoistureData();
        loadAtmosphericData();
    }

    private void loadMoistureData() {
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<LineDataSet> dataSets = new ArrayList<>();

                for (int i = 1; i <= 4; i++) {
                    String sensorKey = "sensor" + i;
                    DataSnapshot sensorSnapshot = snapshot.child(sensorKey);

                    List<Entry> entries = new ArrayList<>();
                    for (DataSnapshot reading : sensorSnapshot.getChildren()) {
                        Long timestamp = reading.child("timestamp").getValue(Long.class);
                        Float value = reading.child("value").getValue(Float.class);

                        if (timestamp != null && value != null && timestamp >= timeFilterStart) {
                            entries.add(new Entry(timestamp, value));
                        }
                    }

                    if (!entries.isEmpty()) {
                        LineDataSet set = new LineDataSet(entries, "Sensor " + i);
                        set.setLineWidth(2f);
                        set.setDrawCircles(false);
                        set.setDrawValues(false);
                        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);

                        /* Assign unique color to each sensor */
                        switch (i) {
                            case 1:
                                set.setColor(Color.parseColor("#1E88E5")); /* Blue */
                                break;
                            case 2:
                                set.setColor(Color.parseColor("#43A047")); /* Green */
                                break;
                            case 3:
                                set.setColor(Color.parseColor("#FB8C00")); /* Orange */
                                break;
                            case 4:
                                set.setColor(Color.parseColor("#E53935")); /* Red */
                                break;
                        }
                        dataSets.add(set);
                    }
                }

                LineData lineData = new LineData(dataSets.toArray(new LineDataSet[0]));
                moistureChart.setData(lineData);
                configureChart(moistureChart);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadAtmosphericData() {
        databaseReference.child("atmospheric").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Entry> tempEntries = new ArrayList<>();
                List<Entry> humidityEntries = new ArrayList<>();

                for (DataSnapshot reading : snapshot.getChildren()) {
                    Long timestamp = reading.child("timestamp").getValue(Long.class);
                    Double temp = reading.child("temperature").getValue(Double.class);
                    Double humidity = reading.child("humidity").getValue(Double.class);

                    if (timestamp != null && timestamp >= timeFilterStart) {
                        if (temp != null)
                            tempEntries.add(new Entry(timestamp, temp.floatValue()));
                        if (humidity != null)
                            humidityEntries.add(new Entry(timestamp, humidity.floatValue()));
                    }
                }

                LineDataSet tempSet = new LineDataSet(tempEntries, "Temperature (°C)");
                tempSet.setColor(Color.RED);
                tempSet.setLineWidth(2f);
                tempSet.setDrawCircles(false);
                tempSet.setDrawValues(false);
                tempSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);


                LineDataSet humSet = new LineDataSet(humidityEntries, "Humidity (%)");
                humSet.setColor(Color.BLUE);
                humSet.setLineWidth(2f);
                humSet.setDrawCircles(false);
                humSet.setDrawValues(false);
                humSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

                LineData lineData = new LineData(tempSet, humSet);
                atmosphericChart.setData(lineData);
                configureChart(atmosphericChart);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void configureChart(LineChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(true);
        chart.setTouchEnabled(true);
        chart.setPinchZoom(true);
        chart.invalidate();

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return dateFormat.format(new Date((long) value * 1000));
            }
        });
        xAxis.setDrawGridLines(false);
        xAxis.setLabelRotationAngle(-45);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(0, 0);
        finish();
    }
}

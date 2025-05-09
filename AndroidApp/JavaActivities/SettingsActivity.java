package com.example.irrigationapp1;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.slider.Slider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import com.google.firebase.database.ValueEventListener;

public class SettingsActivity extends AppCompatActivity {
    private Slider[] sliders = new Slider[4];
    private TextView[] sliderLabels = new TextView[4];
    private Button saveThresholdsBtn;
    private Button resetThresholdsBtn;
    private DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize sliders
        sliders[0] = findViewById(R.id.slider1);
        sliders[1] = findViewById(R.id.slider2);
        sliders[2] = findViewById(R.id.slider3);
        sliders[3] = findViewById(R.id.slider4);

        // Initialize labels
        sliderLabels[0] = findViewById(R.id.sliderLabel1);
        sliderLabels[1] = findViewById(R.id.sliderLabel2);
        sliderLabels[2] = findViewById(R.id.sliderLabel3);
        sliderLabels[3] = findViewById(R.id.sliderLabel4);

        // Save and Reset button
        saveThresholdsBtn = findViewById(R.id.saveThresholdsBtn);
        resetThresholdsBtn = findViewById(R.id.resetThresholdsBtn);

        // Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference();

        // Load values from Firebase
        loadThresholdsFromFirebase();

        // Real-time listeners to sliders
        for (int i = 0; i < 4; i++) {
            int index = i;
            sliders[i].addOnChangeListener((slider, value, fromUser) -> {
                sliderLabels[index].setText("Plant " + (index + 1) + " Threshold: " + (int) value + "%");
            });
        }

        // Save button logic
        saveThresholdsBtn.setOnClickListener(v -> {
            for (int i = 0; i < 4; i++) {
                String sensorKey = "sensor" + (i + 1);
                float value = sliders[i].getValue();

                databaseReference
                        .child("control")
                        .child("thresholds")
                        .child(sensorKey)
                        .setValue(value);
            }
            Toast.makeText(this, "Thresholds saved", Toast.LENGTH_SHORT).show();
        });

        // Reset thresholds
        resetThresholdsBtn.setOnClickListener(v -> {
            for (int i = 0; i < 4; i++) {
                String sensorKey = "sensor" + (i + 1);
                databaseReference.child("control").child("thresholds").child(sensorKey).setValue(0);
                sliders[i].setValue(0);
                sliderLabels[i].setText("Plant " + (i + 1) + " Threshold: 0%");
            }
            Toast.makeText(this, "All thresholds reset to 0", Toast.LENGTH_SHORT).show();
        });

        // Bottom Nav Setup
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_settings); // highlight Settings

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_settings) {
                return true;
            } else if (itemId == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
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

    private void loadThresholdsFromFirebase() {
        databaseReference.child("control").child("thresholds").addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (int i = 0; i < 4; i++) {
                            String sensorKey = "sensor" + (i + 1);
                            Double threshold = snapshot.child(sensorKey).getValue(Double.class);
                            if (threshold != null) {
                                sliders[i].setValue(threshold.floatValue());
                                sliderLabels[i].setText("Plant " + (i + 1) + " Threshold: " + threshold.intValue() + "%");
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(SettingsActivity.this,
                                "Failed to load thresholds: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(0, 0);
        finish();
    }
}


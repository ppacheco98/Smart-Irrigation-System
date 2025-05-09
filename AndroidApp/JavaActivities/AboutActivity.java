package com.example.irrigationapp1;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView aboutText = findViewById(R.id.aboutText);
        aboutText.setText(
                "Irrigation App\n\n" +
                        "Version: 1.3\n\n" +
                        "Description: This app allows you to monitor and automate plant irrigation using real-time sensor data. " +
                        "It supports manual and automatic modes, moisture threshold control, and data visualization via charts.\n\n" +
                        "Developed by: Pablo Pacheco Ruiz\n\n" +
                        "Powered by:\n" +
                        "- Firebase Realtime Database\n" +
                        "- MPAndroidChart\n" +
                        "- Arduino / ESP32 Hardware\n\n" +
                        "Privacy: This app stores sensor data only in your linked Firebase account and does not share data externally."
        );

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_about);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_about) {
                return true;
            } else if (itemId == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    @Override
    public void onBackPressed() {
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(0, 0);
        finish();
    }
}

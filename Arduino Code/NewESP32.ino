/* Include necessary libraries */
#include <Arduino.h>
#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"
#include <DHT.h>
#include <time.h>

/* WiFi and Firebase credentials */
#define WIFI_SSID "VM7954961"
#define WIFI_PASSWORD "acpwzfkfJc2d"
#define API_KEY "AIzaSyC4uRXX2kCuK3BPI4d0YeHXYv8_5hnp44Y"
#define DATABASE_URL "https://smart-irrigation-system-79c3d-default-rtdb.europe-west1.firebasedatabase.app/"

/* Firebase objects for handling data and authentication */
FirebaseData fbdo_stream;   /* Stream object for listening to data changes */
FirebaseData fbdo_upload;   /* Object for uploading sensor data */
FirebaseAuth auth;          /* Authentication object */
FirebaseConfig config;      /* Configuration object */

/* Define pins for relays and sensors */
const int relayPins[4] = {5, 26, 27, 17};       /* Relay control pins */
const int sensorPins[4] = {33, 32, 35, 34};     /* Soil moisture sensor pins */
const String sensorNames[4] = {"sensor1", "sensor2", "sensor3", "sensor4"}; /* Sensor identifiers */

/* Moisture calibration data */
float minMoisture[4] = {2933.0, 2919.0, 2943.0, 2741.0};
float maxMoisture[4] = {1076.0, 1068.0, 1051.0, 1050.0};
float thresholds[4] = {0.0, 0.0, 0.0, 0.0};         /* Thresholds for auto-watering */
float lastMoistureValues[4] = {0.0, 0.0, 0.0, 0.0}; /* Last recorded moisture values */

/* Timing variables for sensor updates */
unsigned long lastSensorUpdateMillis = 0;
const unsigned long sensorUpdateInterval = 5000; /* Update every 5 seconds */

/* Relay control variables */
String mode = "Manual";                            /* Operation mode */
String relayStates[4] = {"OFF", "OFF", "OFF", "OFF"}; /* Relay states */
bool autoRelayActive[4] = {false, false, false, false}; /* Auto watering state */
unsigned long autoRelayStartTime[4] = {0, 0, 0, 0};     /* Start time for auto watering */
const unsigned long autoRelayDuration = 2000;          /* Duration of auto watering (2 seconds) */

/* DHT11 sensor setup */
#define DHTPIN 14
#define DHTTYPE DHT11
DHT dht(DHTPIN, DHTTYPE);
float tempC = 0.0, hum = 0.0, HIC = 0.0;
float lastTemp = 0.0, lastHum = 0.0, lastHIC = 0.0;
const float dhtChangeThreshold = 5.0; /* Minimum change for uploading DHT data */

/* Flag to indicate successful Firebase authentication */
bool signupOK = false;

/* Function to normalize moisture readings to percentage */
float normalize(float raw, int sensorIndex) {
  return constrain((raw - minMoisture[sensorIndex]) / (maxMoisture[sensorIndex] - minMoisture[sensorIndex]) * 100.0, 0.0, 100.0);
}

void setup() {
  Serial.begin(115200);

  /* Initialize pins */
  for (int i = 0; i < 4; i++) {
    pinMode(relayPins[i], OUTPUT);
    digitalWrite(relayPins[i], HIGH); /* Turn off relays */
    pinMode(sensorPins[i], INPUT);    /* Set sensor pins as input */
  }

  dht.begin(); /* Start DHT sensor */

  /* Connect to WiFi */
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(2000);
  }
  Serial.println("\n WiFi connected");
  Serial.println("IP address: " + WiFi.localIP().toString());

  configTime(0, 0, "pool.ntp.org"); /* Synchronize time using NTP */

  /* Setup Firebase configuration */
  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;
  config.token_status_callback = tokenStatusCallback;

  /* Set Firebase user credentials */
  auth.user.email = "testuser@example.com";
  auth.user.password = "test1234";

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  /* Sign up or sign in to Firebase */
  if (Firebase.signUp(&config, &auth, auth.user.email, auth.user.password)) {
    signupOK = true;
    Serial.println("Firebase sign-up/sign-in OK");
  } else {
    Serial.println("Firebase sign-up failed: " + String(config.signer.signupError.message.c_str()));
    if (config.signer.signupError.message == "EMAIL_EXISTS") {
      Serial.println("User already exists. Proceeding...");
      signupOK = true;
    }
  }

  /* Start Firebase stream for control commands */
  if (signupOK) {
    if (Firebase.RTDB.beginStream(&fbdo_stream, "/control/")) {
      Serial.println("Firebase control stream started.");
    } else {
      Serial.println("Failed to start stream: " + fbdo_upload.errorReason());
    }
  }
}

void loop() {
  /* Reconnect to WiFi if disconnected */
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi disconnected. Reconnecting...");
    WiFi.reconnect();
    delay(2000);
  }

  /* Handle incoming Firebase stream updates */
  if (Firebase.ready() && signupOK) {
    if (Firebase.RTDB.readStream(&fbdo_stream)) {
      if (fbdo_stream.streamAvailable()) {
        String path = fbdo_stream.dataPath();
        String valueStr = fbdo_stream.stringData();
        float valueFloat = fbdo_stream.floatData();
        Serial.println("Update! Path: " + path + " Value: " + valueStr);

        /* Update mode or relay states */
        if (path == "/mode") {
          mode = valueStr;
        } else if (path.startsWith("/relayStates/")) {
          String sensorName = path.substring(strlen("/relayStates/"));
          for (int i = 0; i < 4; i++) {
            if (sensorName == sensorNames[i]) {
              relayStates[i] = valueStr;
              digitalWrite(relayPins[i], (valueStr == "ON") ? LOW : HIGH);
            }
          }
        } else if (path.startsWith("/thresholds/")) {
          /* Update moisture thresholds */
          String sensorName = path.substring(strlen("/thresholds/"));
          for (int i = 0; i < 4; i++) {
            if (sensorName == sensorNames[i]) {
              thresholds[i] = valueFloat;
              Serial.printf("Threshold updated: %s = %.2f\n", sensorName.c_str(), thresholds[i]);

              /* Activate relay immediately if in Auto mode and moisture is low */
              if (mode == "Auto") {
                int raw = analogRead(sensorPins[i]);
                float moisture = normalize(raw, i);
                Serial.printf("Moisture: %.2f%% | Threshold: %.2f%% for %s\n", moisture, thresholds[i], sensorNames[i].c_str());
                if (moisture < thresholds[i] && !autoRelayActive[i]) {
                  digitalWrite(relayPins[i], LOW);
                  relayStates[i] = "ON";
                  autoRelayActive[i] = true;
                  autoRelayStartTime[i] = millis();
                  lastMoistureValues[i] = moisture;
                  Firebase.RTDB.setFloat(&fbdo_upload, "/moisture/" + sensorNames[i], moisture);
                  Firebase.RTDB.setString(&fbdo_upload, "/control/relayStates/" + sensorNames[i], "ON");
                  Serial.printf("%s relay activated immediately due to threshold update\n", sensorNames[i].c_str());
                }
              }
            }
          }
        }
      }
    }
  }

  /* Periodic sensor reading and data upload */
  if (millis() - lastSensorUpdateMillis > sensorUpdateInterval) {
    lastSensorUpdateMillis = millis();

    /* Read DHT11 data */
    tempC = dht.readTemperature();
    hum = dht.readHumidity();
    if (!isnan(tempC) && !isnan(hum)) {
      HIC = dht.computeHeatIndex(tempC, hum, false);
      float tempDiff = abs(tempC - lastTemp);
      float humDiff = abs(hum - lastHum);
      float hicDiff = abs(HIC - lastHIC);

      /* Upload only if there is significant change */
      if (tempDiff >= (lastTemp * dhtChangeThreshold / 100.0) || 
          humDiff >= (lastHum * dhtChangeThreshold / 100.0) ||
          hicDiff >= (lastHIC * dhtChangeThreshold / 100.0)) {

        FirebaseJson json;
        json.set("temperature", tempC);
        json.set("humidity", hum);
        json.set("heatIndex", HIC);

        time_t now = time(nullptr);
        json.set("timestamp", now);

        Firebase.RTDB.setJSON(&fbdo_upload, "/atmospheric", &json);
        Firebase.RTDB.setJSON(&fbdo_upload, "/logs/atmospheric/" + String(now), &json);

        Serial.printf("Temp: %.2f°C  Humidity: %.2f%%  Heat Index: %.2f°C\n", tempC, hum, HIC);

        lastTemp = tempC;
        lastHum = hum;
        lastHIC = HIC;
      }
    } else {
      Serial.println("Failed to read from DHT11 sensor");
    }

    /* Read and upload soil moisture data */
    for (int i = 0; i < 4; i++) {
      int raw = analogRead(sensorPins[i]);
      float moisture = normalize(raw, i);
      float diff = abs(moisture - lastMoistureValues[i]);

      /* Upload if moisture changed significantly */
      if (diff >= 1.0) {
        Serial.printf("Uploading %s moisture: %.2f%% (raw: %d)\n", sensorNames[i].c_str(), moisture, raw);

        Firebase.RTDB.setFloat(&fbdo_upload, "/moisture/" + sensorNames[i], moisture);

        FirebaseJson logEntry;
        time_t now = time(nullptr);
        logEntry.set("value", moisture);
        logEntry.set("timestamp", now);
        Firebase.RTDB.setJSON(&fbdo_upload, "/logs/" + sensorNames[i] + "/" + String(now), &logEntry);

        lastMoistureValues[i] = moisture;
        delay(50);
      }

      /* Auto mode watering logic */
      if (mode == "Auto") {
        if (moisture < thresholds[i]) {
          if (!autoRelayActive[i]) {
            digitalWrite(relayPins[i], LOW);
            relayStates[i] = "ON";
            autoRelayActive[i] = true;
            autoRelayStartTime[i] = millis();
            Firebase.RTDB.setString(&fbdo_upload, "/control/relayStates/" + sensorNames[i], "ON");
            Serial.printf("%s relay activated for timed watering\n", sensorNames[i].c_str());
            delay(50);
          }
        }
      }
    }
  }

  /* Stop auto watering after the duration expires */
  for (int i = 0; i < 4; i++) {
    if (autoRelayActive[i] && millis() - autoRelayStartTime[i] >= autoRelayDuration) {
      digitalWrite(relayPins[i], HIGH);
      relayStates[i] = "OFF";
      autoRelayActive[i] = false;
      Firebase.RTDB.setString(&fbdo_upload, "/control/relayStates/" + sensorNames[i], "OFF");
      Serial.printf("%s watering complete (relay OFF)\n", sensorNames[i].c_str());
      delay(50);
    }
  }
}

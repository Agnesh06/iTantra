# iTantra - First-Time Setup & Run Guide
**SIH26173 | ISRO | Offline Emergency Voice Communicator & Mesh Translator**

Welcome to **iTantra**! This document provides step-by-step instructions to open, build, test, and run this project in Android Studio with **zero configuration errors**.

---

## 📋 1. Prerequisites
Before opening the project, ensure you have installed:
1. **Android Studio**: Ladybug / Quail (2024.2.1+) or newer.
2. **Java Development Kit (JDK)**: **JDK 17** (Temurin or OpenJDK 17).
3. **Android SDK**: Compile SDK 35 (Android 15), Min SDK 26 (Android 8.0 Oreo).
4. **Gradle & AGP**: Gradle 8.11.1 & AGP 8.7.3 (Kotlin 2.0.21).

---

## 🛠️ 2. Opening the Project for the First Time in Android Studio

1. **Launch Android Studio**.
2. Click **Open** (or `File -> Open`).
3. Browse to and select the project root folder:
   ```
   C:\Users\Agnesh\Desktop\sih
   ```
4. Click **OK** and wait for Android Studio to import the project.

---

## ⚙️ 3. Configuring Gradle JDK (Critical)
To prevent build errors, ensure Android Studio uses **JDK 17**:
1. Go to `File -> Settings` (Windows/Linux) or `Android Studio -> Settings` (macOS).
2. Navigate to **Build, Execution, Deployment -> Build Tools -> Gradle**.
3. Under **Gradle JDK**, select **JDK 17** (e.g., `jbr-17` bundled JDK or OpenJDK 17).
4. Click **Apply** and **OK**.
5. Click the **Sync Project with Gradle Files** button (elephant icon with blue arrow) in the top toolbar.

---

## 🧪 4. Running Unit Tests
The project features a comprehensive JUnit test suite verifying protocol serialization, FEC error correction, retransmission timers, and AI safety fallback.

* **To run all tests in Android Studio**:
  1. In the **Project** panel on the left, navigate to: `app -> src -> test -> java -> com.itantra.app`.
  2. Right-click on `com.itantra.app` and select **Run 'Tests in 'com.itantra.app''**.
  3. Verify all test cases pass successfully with green checkmarks.

* **To run via command line (PowerShell/CMD)**:
  ```powershell
  .\gradlew.bat testDebugUnitTest
  ```

---

## 🚀 5. Building & Running on a Device / Emulator

1. **Connect an Android Device** via USB (with **USB Debugging** enabled in Developer Options) or start an Android Emulator in the Device Manager.
2. **Deploy the App**:
   - Click the green **Run** button (`Shift + F10`) in Android Studio.
   - Or run via command line:
     ```powershell
     .\gradlew.bat assembleDebug
     .\gradlew.bat installDebug
     ```

---

## 🛜 6. Connecting Two Devices for Mesh Demo (Wi-Fi Direct)
1. Install the APK on **two Android devices** (e.g., Device A and Device B).
2. Grant requested runtime permissions (Microphone, Nearby Wi-Fi Devices, Location).
3. Turn **Wi-Fi ON** on both phones and pair them via system **Wi-Fi Direct** settings.
4. Open **iTantra** on both phones $\to$ Tap the **Settings icon (⚙️)** $\to$ Enter the peer's Wi-Fi Direct IP address (e.g., `192.168.49.1`) under **Wi-Fi Direct Peer Connection** $\to$ Tap **Connect Peer**.
5. Test push-to-talk voice translation and emergency alert broadcasting peer-to-peer!

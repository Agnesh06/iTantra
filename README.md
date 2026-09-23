# iTantra (SIH26173)
> **Zero-Cloud Offline Multilingual Voice Communicator & Low-Bitrate Wi-Fi Direct Mesh Translator for Emergency & Disaster Relief**
> Developed for **Smart India Hackathon | ISRO**

---

## 🚀 Quick Start & Handover

For complete setup instructions, architecture diagrams, testing workflows, and future roadmap, refer to:
👉 **[HANDOVER_AND_TESTING_GUIDE.md](file:///c:/Users/Agnesh/Desktop/sih/HANDOVER_AND_TESTING_GUIDE.md)**

Detailed hardware, toolchain, and dependency specifications can be found in:
👉 **[REQUIREMENTS.md](file:///c:/Users/Agnesh/Desktop/sih/REQUIREMENTS.md)**

---

## 🛠️ Opening in Android Studio
1. Open Android Studio Quail / Ladybug (2024.2.1+) or newer.
2. Select **Open** and choose this project directory (`c:\Users\Agnesh\Desktop\sih`).
3. Set **Gradle JDK** to **JDK 17** in `Settings -> Build Tools -> Gradle`.
4. Click **Sync Project with Gradle Files**.

---

## 🧪 Testing Summary

### 1. Automated JVM Unit Tests
Right-click `app/src/test` in Android Studio and click **Run 'Tests in 'app.test''**:
- `PacketWireFormatTest.kt` (Wire framing, CRC32 corruption validation)
- `FecEngineTest.kt` (XOR Parity FEC error correction)
- `MessageAssemblerTest.kt` (Packet reassembly & 15s inactivity timeout)
- `AckRetryManagerTest.kt` (Sliding window ACK, RTT EMA, 3-retry limit)
- `LossSimulatorTest.kt` (0-30% packet drop simulation; HELLO/ACK immunity)
- `SafeTranslationFallbackTest.kt` (Translation fallback & TTS safety guardrail)

### 2. In-App Interactive Diagnostics
Open the app on an Android device, navigate to the **Diagnostics Screen** (Wrench icon in TopBar), and run interactive tests:
- **Test VAD**, **Test ASR**, **Test IndicTrans2**, **Test TTS**, **Test Playback**, and **Full Loop**.
- Toggle the **UDP Loss Simulator** to simulate network packet loss (0% to 30%).

---

## 📦 Python AI Model Export Tooling
Install Python dependencies and export models to ONNX:
```bash
pip install -r requirements.txt
python tools/export_indicconformer.py --lang hi --out ./exported_models
python tools/export_indictrans2.py --variant indic-indic --out ./exported_models
python tools/export_indictts.py --lang hi --out ./exported_models
```

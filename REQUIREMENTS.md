# iTantra - System & Build Requirements Specification
**SIH26173 | ISRO | Software**

This document details the exact requirements for developing, building, and deploying the iTantra native Android application and running the offline AI model conversion tools.

---

## 1. Android Development Toolchain Requirements

| Component | Pinned Version | Purpose / Notes |
| :--- | :--- | :--- |
| **Operating System** | Windows 10/11, macOS, or Linux | Host development environment |
| **Android Studio** | Ladybug / Quail (2024.2.1+) or newer | Official IDE |
| **Java Development Kit (JDK)**| **JDK 17** (Temurin / OpenJDK 17) | Required by Android Gradle Plugin |
| **Gradle** | **8.11.1** | Configured in `gradle/wrapper/gradle-wrapper.properties` |
| **Android Gradle Plugin (AGP)**| **8.7.3** | Android build system |
| **Kotlin** | **2.0.21** | Application programming language |
| **KSP (Kotlin Symbol Processing)**| **2.0.21-1.0.28** | Room compiler code generation |
| **Android Compile SDK** | **35** (Android 15) | Target compilation SDK |
| **Android Target SDK** | **35** | Target runtime SDK |
| **Android Minimum SDK** | **26** (Android 8.0 Oreo) | Baseline runtime compatibility |
| **Supported Native ABIs** | `arm64-v8a` (Primary), `x86_64` (AVD) | Target Android CPU architectures |

---

## 2. Android Hardware & Device Requirements

* **Physical Test Devices**: Minimum of two Android devices (one low-end device with 2–4 GB RAM, and one mid-range device with 6–8 GB RAM).
* **Direct Connectivity**: Wi-Fi Direct (P2P) hardware support.
* **Microphone**: Hardware microphone supporting 16,000 Hz 16-bit mono PCM recording.
* **Audio Output**: Dedicated audio speaker for alarm routing (`AudioAttributes.USAGE_ALARM`) and media routing (`AudioAttributes.USAGE_MEDIA`).
* **Android OS Runtime Permissions**:
  * `RECORD_AUDIO`: Microphone capture.
  * `NEARBY_WIFI_DEVICES`: Wi-Fi Direct peer discovery and group connection (Android 13+).
  * `ACCESS_FINE_LOCATION`: Required for Wi-Fi Direct discovery on Android $\le$ 12 (`maxSdkVersion="32"`).
  * `INTERNET`: UDP DatagramSocket binding on the Wi-Fi Direct P2P interface.

---

## 3. Python AI Model Export Tooling Requirements

To export AI4Bharat checkpoints (IndicConformer, IndicTrans2, Indic-TTS) to mobile `.onnx` models using the scripts in `tools/`:

* **Python Version**: Python 3.10 or 3.11 (64-bit).
* **Package File**: [requirements.txt](file:///c:/Users/Agnesh/Desktop/sih/requirements.txt)

### Installation Command:
```bash
pip install -r requirements.txt
```

### Key Python Dependencies:
* `torch` & `torchaudio` ($\ge 2.2.0$)
* `onnx` ($\ge 1.16.0$) & `onnxruntime` ($\ge 1.17.0$)
* `transformers` ($\ge 4.38.0$) & `tokenizers`
* `nemo-toolkit[asr]` (for IndicConformer NeMo checkpoints)
* `soundfile` & `scipy`
* `indic-nlp-library` & `sentencepiece`

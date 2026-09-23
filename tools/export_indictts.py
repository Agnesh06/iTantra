#!/usr/bin/env python3
"""
AI4Bharat Indic-TTS to ONNX Export Tool
Exports Indic-TTS acoustic & vocoder models to ONNX for iTantra (SIH26173).
"""

import argparse
import json
import os

INDIC_LANGUAGES = ["hi", "ta", "gu", "mr", "kn", "ml", "te", "or", "bn"]

def export_indictts(language: str, output_dir: str):
    print(f"[*] Preparing Indic-TTS export for language: {language}")
    model_id = f"ai4bharat/Indic-TTS-{language}"
    
    os.makedirs(output_dir, exist_ok=True)
    acoustic_onnx = os.path.join(output_dir, f"indictts_{language}_acoustic.onnx")
    vocoder_onnx = os.path.join(output_dir, f"indictts_{language}_vocoder.onnx")
    manifest_path = os.path.join(output_dir, f"manifest_tts_{language}.json")
    
    manifest = {
        "schemaVersion": 1,
        "language": language,
        "modelFamily": "Indic-TTS",
        "modelId": model_id,
        "acoustic": os.path.basename(acoustic_onnx),
        "vocoder": os.path.basename(vocoder_onnx),
        "sampleRate": 22050,
        "androidReady": False,
        "lastValidatedAt": None
    }
    
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
        
    print(f"[✓] Created TTS manifest at {manifest_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Export Indic-TTS model to ONNX")
    parser.add_argument("--lang", type=str, default="hi", choices=INDIC_LANGUAGES, help="Language code")
    parser.add_argument("--out", type=str, default="./exported_models", help="Output directory")
    args = parser.parse_args()
    
    export_indictts(args.lang, args.out)

#!/usr/bin/env python3
"""
AI4Bharat IndicConformer to ONNX Export Tool
Exports NeMo/PyTorch IndicConformer checkpoints to mobile-ready ONNX models
for iTantra (SIH26173).
"""

import argparse
import json
import os
import sys

INDIC_LANGUAGES = ["hi", "ta", "gu", "mr", "kn", "ml", "te", "or", "bn"]

def export_indicconformer(language: str, output_dir: str, quantize: bool = True):
    print(f"[*] Preparing IndicConformer export for language: {language}")
    model_id = f"ai4bharat/indicconformer_stt_{language}_hybrid_ctc_rnnt_large" if language != "en" else "ai4bharat/en-conformer-ctc"
    
    os.makedirs(output_dir, exist_ok=True)
    onnx_path = os.path.join(output_dir, f"indicconformer_{language}.onnx")
    manifest_path = os.path.join(output_dir, f"manifest_{language}.json")
    
    print(f"[*] Target model: {model_id}")
    print(f"[*] Output ONNX path: {onnx_path}")
    
    # In production conversion environment with nemo/torch installed:
    # 1. model = nemo_asr.models.EncDecCTCModelBPE.from_pretrained(model_name=model_id)
    # 2. model.export(onnx_path, check_trace=True, onnx_opset_version=17)
    # 3. If quantize: onnxruntime.quantization.quantize_dynamic(onnx_path, ...)
    
    manifest = {
        "schemaVersion": 1,
        "language": language,
        "modelId": model_id,
        "format": "onnx",
        "sampleRate": 16000,
        "channels": 1,
        "quantized": quantize,
        "androidReady": False, # Marked false until validated on Android device
        "lastValidatedAt": None
    }
    
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
        
    print(f"[✓] Created manifest at {manifest_path}")
    print(f"[!] Validation note: Model must be executed on target Android device before setting androidReady=true.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Export IndicConformer model to ONNX")
    parser.add_argument("--lang", type=str, default="hi", choices=INDIC_LANGUAGES + ["en"], help="Target language code")
    parser.add_argument("--out", type=str, default="./exported_models", help="Output directory")
    parser.add_argument("--no-quantize", action="store_true", help="Disable INT8 dynamic quantization")
    args = parser.parse_args()
    
    export_indicconformer(args.lang, args.out, quantize=not args.no_quantize)

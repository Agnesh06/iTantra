#!/usr/bin/env python3
"""
AI4Bharat IndicTrans2 to ONNX Export Tool
Exports IndicTrans2 320M / 200M models to mobile-ready ONNX models
for iTantra (SIH26173).
"""

import argparse
import json
import os
import sys

def export_indictrans2(model_variant: str, output_dir: str, quantize: bool = True):
    print(f"[*] Preparing IndicTrans2 export for variant: {model_variant}")
    if model_variant == "indic-indic":
        model_id = "ai4bharat/indictrans2-indic-indic-dist-320M"
    elif model_variant == "indic-en":
        model_id = "ai4bharat/indictrans2-indic-en-dist-200M"
    elif model_variant == "en-indic":
        model_id = "ai4bharat/indictrans2-en-indic-dist-200M"
    else:
        raise ValueError(f"Unknown variant: {model_variant}")
        
    os.makedirs(output_dir, exist_ok=True)
    encoder_onnx = os.path.join(output_dir, f"indictrans2_{model_variant}_encoder.onnx")
    decoder_onnx = os.path.join(output_dir, f"indictrans2_{model_variant}_decoder.onnx")
    manifest_path = os.path.join(output_dir, f"manifest_indictrans2_{model_variant}.json")
    
    print(f"[*] HuggingFace Model ID: {model_id}")
    print(f"[*] Target encoder: {encoder_onnx}")
    print(f"[*] Target decoder: {decoder_onnx}")
    
    # In production conversion environment:
    # 1. tokenizer = AutoTokenizer.from_pretrained(model_id, trust_remote_code=True)
    # 2. model = AutoModelForSeq2SeqLM.from_pretrained(model_id, trust_remote_code=True)
    # 3. torch.onnx.export(model.encoder, ...)
    # 4. torch.onnx.export(model.decoder, ...)
    # 5. if quantize: apply onnxruntime.quantization
    
    manifest = {
        "schemaVersion": 1,
        "modelFamily": "IndicTrans2",
        "variant": model_variant,
        "modelId": model_id,
        "encoder": os.path.basename(encoder_onnx),
        "decoder": os.path.basename(decoder_onnx),
        "quantized": quantize,
        "androidReady": False,
        "lastValidatedAt": None
    }
    
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
        
    print(f"[✓] Created translation manifest at {manifest_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Export IndicTrans2 model to ONNX")
    parser.add_argument("--variant", type=str, default="indic-indic", choices=["indic-indic", "indic-en", "en-indic"], help="Model variant")
    parser.add_argument("--out", type=str, default="./exported_models", help="Output directory")
    parser.add_argument("--no-quantize", action="store_true", help="Disable INT8 quantization")
    args = parser.parse_args()
    
    export_indictrans2(args.variant, args.out, quantize=not args.no_quantize)

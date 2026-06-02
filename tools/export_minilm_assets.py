#!/usr/bin/env python3
"""Export multilingual MiniLM voice-command assets for the Android app.

The app expects:
  app/src/main/assets/models/minilm/model_int8.onnx
  app/src/main/assets/models/minilm/tokenizer.json
"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

import torch
from onnxruntime.quantization import QuantType, quantize_dynamic
from transformers import AutoModel, AutoTokenizer


DEFAULT_MODEL_ID = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
DEFAULT_MAX_LENGTH = 128


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-id", default=DEFAULT_MODEL_ID)
    parser.add_argument("--max-length", type=int, default=DEFAULT_MAX_LENGTH)
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("app/src/main/assets/models/minilm"),
    )
    parser.add_argument(
        "--work-dir",
        type=Path,
        default=Path("build/minilm-export"),
    )
    return parser.parse_args()


class MiniLmOnnxWrapper(torch.nn.Module):
    def __init__(self, model: torch.nn.Module) -> None:
        super().__init__()
        self.model = model

    def forward(
        self,
        input_ids: torch.Tensor,
        attention_mask: torch.Tensor,
        token_type_ids: torch.Tensor,
    ) -> torch.Tensor:
        output = self.model(
            input_ids=input_ids,
            attention_mask=attention_mask,
            token_type_ids=token_type_ids,
            return_dict=True,
        )
        return output.last_hidden_state


def main() -> None:
    args = parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    args.work_dir.mkdir(parents=True, exist_ok=True)

    fp32_model = args.work_dir / "model_fp32.onnx"
    int8_model = args.output_dir / "model_int8.onnx"
    tokenizer_path = args.output_dir / "tokenizer.json"

    tokenizer = AutoTokenizer.from_pretrained(args.model_id)
    model = AutoModel.from_pretrained(args.model_id)
    model.eval()

    encoded = tokenizer(
        "mở camera đọc văn bản",
        return_tensors="pt",
        max_length=args.max_length,
        padding="max_length",
        truncation=True,
    )
    token_type_ids = encoded.get("token_type_ids")
    if token_type_ids is None:
        token_type_ids = torch.zeros_like(encoded["input_ids"])

    wrapper = MiniLmOnnxWrapper(model)
    with torch.no_grad():
        torch.onnx.export(
            wrapper,
            (encoded["input_ids"], encoded["attention_mask"], token_type_ids),
            fp32_model,
            input_names=["input_ids", "attention_mask", "token_type_ids"],
            output_names=["last_hidden_state"],
            dynamic_axes=None,
            dynamo=False,
            opset_version=17,
        )

    quantize_dynamic(
        model_input=fp32_model,
        model_output=int8_model,
        weight_type=QuantType.QInt8,
    )

    tokenizer.save_pretrained(args.work_dir / "tokenizer")
    shutil.copyfile(args.work_dir / "tokenizer" / "tokenizer.json", tokenizer_path)

    print(f"Wrote {int8_model} ({int8_model.stat().st_size / 1024 / 1024:.1f} MB)")
    print(f"Wrote {tokenizer_path} ({tokenizer_path.stat().st_size / 1024:.1f} KB)")


if __name__ == "__main__":
    main()

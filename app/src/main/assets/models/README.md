Place exported YOLO26n ExecuTorch model here before running object detection:

```bash
yolo export model=yolo26n.pt format=executorch imgsz=320
```

Expected file:

```text
app/src/main/assets/models/yolo26n.pte
```

Keep `metadata.yaml` beside it when postprocess phase starts.

MiniLM semantic voice-command fallback is generated from:

```bash
python tools/export_minilm_assets.py
```

Expected files:

```text
app/src/main/assets/models/minilm/model_int8.onnx
app/src/main/assets/models/minilm/tokenizer.json
```

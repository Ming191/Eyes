Place exported YOLO26n ExecuTorch model here before running object detection:

```bash
yolo export model=yolo26n.pt format=executorch imgsz=320
```

Expected file:

```text
app/src/main/assets/models/yolo26n.pte
```

Keep `metadata.yaml` beside it when postprocess phase starts.

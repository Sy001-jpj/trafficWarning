"""
Process traffic videos frame-by-frame through YOLOv8n,
burning detection boxes into each frame at 25% opacity.
Outputs annotated videos as *_ai.webm files.
"""
import os, sys, time
from pathlib import Path

import cv2
import numpy as np
from ultralytics import YOLO

# ── Config ──
MODEL_PATH = Path(__file__).parent / "yolov8n.pt"
VIDEOS_DIR = Path(__file__).parent / "videos"
OUTPUT_DIR = VIDEOS_DIR  # Save alongside originals
CONF_THRESHOLD = 0.35
FRAME_SKIP = 3  # Process every Nth frame for speed; 1 = every frame
BOX_ALPHA = 0.25  # Detection box opacity

# COCO classes we care about
CLASS_COLORS = {
    "car": (70, 200, 70),
    "bus": (200, 160, 40),
    "truck": (40, 140, 240),
    "bicycle": (240, 200, 40),
    "motorcycle": (240, 120, 40),
    "person": (200, 80, 200),
}
MOTOR = {2: "car", 5: "bus", 7: "truck"}
NON_MOTOR = {1: "bicycle", 3: "motorcycle"}
PERSON = 0

# Load model
print(f"Loading YOLO from {MODEL_PATH}...")
model = YOLO(str(MODEL_PATH))
print("Model loaded.\n")

# Find video files
video_files = sorted(VIDEOS_DIR.glob("C*.webm"))
# Filter out already-annotated ones
video_files = [f for f in video_files if not f.stem.endswith("_ai")]
print(f"Found {len(video_files)} videos to process: {[f.name for f in video_files]}\n")

for video_file in video_files:
    out_name = f"{video_file.stem}_ai.webm"
    out_path = VIDEOS_DIR / out_name

    if out_path.exists():
        print(f"SKIP {video_file.name} -> {out_name} (already exists)")
        continue

    cap = cv2.VideoCapture(str(video_file))
    if not cap.isOpened():
        print(f"FAIL {video_file.name}: cannot open")
        continue

    fps = cap.get(cv2.CAP_PROP_FPS)
    w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))

    fourcc = cv2.VideoWriter_fourcc(*'VP80')
    out = cv2.VideoWriter(str(out_path), fourcc, fps / FRAME_SKIP, (w, h))
    if not out.isOpened():
        print(f"FAIL {video_file.name}: VideoWriter error")
        cap.release()
        continue

    print(f"[{video_file.stem}] {w}x{h} @ {fps:.0f}fps, {total_frames} frames (skip={FRAME_SKIP})")

    frame_idx = 0
    written = 0
    t0 = time.time()

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        if frame_idx % FRAME_SKIP != 0:
            frame_idx += 1
            continue

        # YOLO inference
        results = model.predict(frame, device="cpu", verbose=False, conf=CONF_THRESHOLD)

        # Draw boxes directly on frame (full opacity outlines)
        motor_count = 0
        non_motor_count = 0

        for box in results[0].boxes:
            cls_id = int(box.cls[0])
            conf = float(box.conf[0])
            xyxy = box.xyxy[0].tolist()
            x1, y1, x2, y2 = [int(v) for v in xyxy]

            if cls_id in MOTOR:
                label = MOTOR[cls_id]
                motor_count += 1
            elif cls_id in NON_MOTOR:
                label = NON_MOTOR[cls_id]
                non_motor_count += 1
            elif cls_id == PERSON:
                label = "person"
            else:
                continue

            color = CLASS_COLORS.get(label, (200, 200, 200))
            # Draw outline border only (no fill), thicker for visibility
            cv2.rectangle(frame, (x1, y1), (x2, y2), color, 3)
            # Label with solid background
            label_text = f"{label} {conf:.0%}"
            (tw, th), _ = cv2.getTextSize(label_text, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 2)
            cv2.rectangle(frame, (x1, y1 - th - 8), (x1 + tw + 6, y1), color, -1)
            cv2.putText(frame, label_text, (x1 + 3, y1 - 5),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 2, cv2.LINE_AA)

        # (No alpha blending — boxes drawn directly on frame at full opacity)

        # Top info bar
        total = motor_count + non_motor_count
        info = f"Vehicles: {motor_count} motor | {non_motor_count} non-motor | Total: {total}"
        cv2.rectangle(frame, (0, 0), (w, 34), (0, 0, 0), -1)
        cv2.putText(frame, info, (8, 22), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (255, 255, 255), 1, cv2.LINE_AA)

        out.write(frame)
        written += 1
        frame_idx += 1

        if written % 300 == 0:
            elapsed = time.time() - t0
            fps_eff = written / elapsed
            eta = (total_frames / FRAME_SKIP - written) / fps_eff
            print(f"  frame {frame_idx}/{total_frames} | {written} written | {fps_eff:.1f} fps | ETA {eta:.0f}s")

    cap.release()
    out.release()
    elapsed = time.time() - t0
    size_mb = out_path.stat().st_size / (1024 * 1024)
    print(f"DONE {video_file.stem} -> {out_name} | {written} frames | {elapsed:.0f}s | {size_mb:.1f} MB\n")

print("All videos annotated!")

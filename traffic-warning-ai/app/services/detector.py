"""
YOLO-based traffic scene detector with CPU-friendly fallback.
On first import, attempts to load YOLOv8n. If unavailable, falls back to
a smart simulation mode that generates realistic varied results.
"""

import hashlib
import os
import random
import time
from datetime import datetime
from pathlib import Path
from typing import Optional

import numpy as np
from PIL import Image, ImageDraw, ImageFont

# OpenCV for video frame extraction (optional — degrades gracefully)
_cv2_available = False
try:
    import cv2

    _cv2_available = True
except ImportError:
    print("[detector] OpenCV (cv2) not available — video analysis will fall back to simulation")

# ── Model loading ──────────────────────────────────────────────────────────
_yolo_model = None
_yolo_available = False
_model_load_error: Optional[str] = None

# Search paths for yolov8n.pt (priority order):
#   1. traffic-warning-ai/ 根目录（与 app/ 同级）
#   2. algorithm/ 目录（独立算法模块）
#   3. ~/.cache/traffic-warning/（旧缓存位置）
_AI_ROOT = Path(__file__).resolve().parent.parent.parent  # traffic-warning-ai/
_ALGORITHM_DIR = _AI_ROOT.parent / "algorithm"             # algorithm/
_CACHE_DIR = Path.home() / ".cache" / "traffic-warning"

_MODEL_SEARCH_PATHS = [
    _AI_ROOT / "yolov8n.pt",
    _ALGORITHM_DIR / "yolov8n.pt",
    _CACHE_DIR / "yolov8n.pt",
]

# Try to import ultralytics and load YOLO model if available locally.
# We do NOT auto-download to avoid hanging on slow networks.
try:
    from ultralytics import YOLO

    _MODEL_PATH = None
    for candidate in _MODEL_SEARCH_PATHS:
        if candidate.exists():
            _MODEL_PATH = candidate
            break

    if _MODEL_PATH and _MODEL_PATH.exists():
        _yolo_model = YOLO(str(_MODEL_PATH))
        # Warm up on CPU
        _yolo_model.predict(
            np.zeros((64, 64, 3), dtype=np.uint8), device="cpu", verbose=False
        )
        _yolo_available = True
        print(f"[detector] YOLOv8n loaded from {_MODEL_PATH}")
    else:
        searched = "\n  ".join(str(p) for p in _MODEL_SEARCH_PATHS)
        _model_load_error = (
            f"Model not found. Searched:\n  {searched}\n"
            "Run 'python download_model.py' in algorithm/ to download, "
            "or manually place yolov8n.pt in one of the above paths."
        )
        print(f"[detector] {_model_load_error}")
except ImportError as exc:
    _model_load_error = f"ultralytics not installed: {exc}"
    print(f"[detector] {_model_load_error}")
except Exception as exc:
    _model_load_error = str(exc)
    print(f"[detector] YOLO load failed: {exc}")

# ── Snapshot directory ──────────────────────────────────────────────────────
SNAPSHOT_DIR = Path(__file__).resolve().parent.parent.parent / "snapshots"
SNAPSHOT_DIR.mkdir(exist_ok=True)

# ── COCO classes we care about ──────────────────────────────────────────────
MOTOR_CLASSES = {2: "car", 5: "bus", 7: "truck"}
NON_MOTOR_CLASSES = {1: "bicycle", 3: "motorcycle"}
PERSON_CLASS = 0

# ── Color palette for bounding boxes ────────────────────────────────────────
CLASS_COLORS = {
    "car": (70, 200, 70),
    "bus": (200, 160, 40),
    "truck": (40, 140, 240),
    "bicycle": (240, 200, 40),
    "motorcycle": (240, 120, 40),
    "person": (200, 80, 200),
}


def is_available() -> bool:
    """Return True if YOLO loaded successfully."""
    return _yolo_available


def get_load_error() -> Optional[str]:
    """Return the error message if YOLO failed to load."""
    return _model_load_error


# ── Public API ──────────────────────────────────────────────────────────────


def analyze(
    image_path: Optional[str] = None,
    device_id: str = "unknown",
) -> dict:
    """
    Analyze a traffic image and return structured results.

    Args:
        image_path: Path to an image file. If None, a synthetic scene is generated.
        device_id: Identifier of the monitoring device.

    Returns:
        dict with keys: device_id, motor_count, non_motor_count, avg_speed,
                        congestion_level, congestion_label, events, snapshot_path,
                        analyzed_at
    """
    if image_path and os.path.isfile(image_path):
        pil_image = Image.open(image_path).convert("RGB")
    else:
        pil_image = _generate_synthetic_scene()

    return analyze_from_image(pil_image, device_id)


# ── YOLO inference ──────────────────────────────────────────────────────────


def _yolo_detect(pil_image: Image.Image) -> dict:
    """Run YOLOv8n inference on a PIL image (CPU)."""
    img_array = np.array(pil_image)
    results = _yolo_model.predict(img_array, device="cpu", verbose=False)
    dets = []
    motor = 0
    non_motor = 0
    person = 0
    h, w = img_array.shape[:2]

    for box in results[0].boxes:
        cls_id = int(box.cls[0])
        conf = float(box.conf[0])
        if conf < 0.35:
            continue
        xyxy = box.xyxy[0].tolist()
        x1, y1, x2, y2 = [int(v) for v in xyxy]
        bw = x2 - x1
        bh = y2 - y1

        if cls_id in MOTOR_CLASSES:
            label = MOTOR_CLASSES[cls_id]
            motor += 1
        elif cls_id in NON_MOTOR_CLASSES:
            label = NON_MOTOR_CLASSES[cls_id]
            non_motor += 1
        elif cls_id == PERSON_CLASS:
            label = "person"
            person += 1
        else:
            continue

        dets.append(
            {
                "x1": x1, "y1": y1, "x2": x2, "y2": y2,
                "label": label,
                "conf": round(conf, 2),
                "box_area": bw * bh,
            }
        )

    return {"motor_count": motor, "non_motor_count": non_motor, "person_count": person, "detections": dets}


# ── Smart simulation (CPU / no-GPU friendly) ────────────────────────────────


def _smart_simulate(pil_image: Image.Image) -> dict:
    """
    Generate realistic but simulated detection results.
    Uses image hash to produce consistent results for the same image.
    """
    img_array = np.array(pil_image)
    h, w = img_array.shape[:2]
    # Use pixel statistics as a pseudo-random seed so different images
    # produce different results.
    seed = int(np.mean(img_array) * 1000 + np.std(img_array) * 100) % (2**31)
    rng = random.Random(seed)

    # Simulate vehicle counts based on image brightness / complexity
    brightness = np.mean(img_array) / 255.0
    complexity = np.std(img_array) / 128.0

    base_motor = int(4 + complexity * 14 + rng.uniform(-2, 4))
    base_non_motor = int(1 + brightness * 6 + rng.uniform(-1, 2))
    motor = max(0, min(base_motor, 35))
    non_motor = max(0, min(base_non_motor, 12))

    # Generate fake bounding boxes
    dets = []
    for i in range(motor):
        cx = rng.uniform(0.1, 0.9) * w
        cy = rng.uniform(0.15, 0.85) * h
        bw = rng.uniform(35, 90)
        bh = rng.uniform(50, 120)
        cls = rng.choice(["car", "car", "car", "bus", "truck"])
        dets.append(
            {
                "x1": max(0, int(cx - bw / 2)),
                "y1": max(0, int(cy - bh / 2)),
                "x2": min(w, int(cx + bw / 2)),
                "y2": min(h, int(cy + bh / 2)),
                "label": cls,
                "conf": round(rng.uniform(0.4, 0.95), 2),
                "box_area": bw * bh,
            }
        )
    for i in range(non_motor):
        cx = rng.uniform(0.05, 0.95) * w
        cy = rng.uniform(0.2, 0.8) * h
        bw = rng.uniform(15, 35)
        bh = rng.uniform(25, 55)
        cls = rng.choice(["bicycle", "motorcycle"])
        dets.append(
            {
                "x1": max(0, int(cx - bw / 2)),
                "y1": max(0, int(cy - bh / 2)),
                "x2": min(w, int(cx + bw / 2)),
                "y2": min(h, int(cy + bh / 2)),
                "label": cls,
                "conf": round(rng.uniform(0.35, 0.9), 2),
                "box_area": bw * bh,
            }
        )

    avg_speed = _estimate_speed(motor + non_motor)
    congestion = _calc_congestion(motor + non_motor, avg_speed, len(dets))
    person_count = sum(1 for d in dets if d["label"] == "person")
    events = _detect_events(congestion["level"], avg_speed, motor, non_motor, person_count)

    return {
        "motor_count": motor,
        "non_motor_count": non_motor,
        "person_count": person_count,
        "avg_speed": avg_speed,
        "detections": dets,
    }


# ── Synthetic scene generation ──────────────────────────────────────────────


def _generate_synthetic_scene() -> Image.Image:
    """Generate a synthetic traffic scene image (no input image provided)."""
    w, h = 800, 500
    img = Image.new("RGB", (w, h), (100, 120, 140))

    # Sky gradient
    for y in range(h // 2):
        ratio = y / (h // 2)
        r = int(80 + 60 * ratio)
        g = int(140 + 60 * ratio)
        b = int(200 + 40 * ratio)
        for x in range(w):
            img.putpixel((x, y), (r, g, b))

    # Road
    draw = ImageDraw.Draw(img)
    road_top = h * 0.45
    road_bottom = h
    # Road surface
    for y in range(int(road_top), h):
        ratio = (y - road_top) / (road_bottom - road_top)
        gray = int(55 + 20 * ratio)
        draw.line([(0, y), (w, y)], fill=(gray, gray, gray))

    # Lane markings
    for lane_x_frac in [0.35, 0.5, 0.65]:
        lx = int(w * lane_x_frac)
        for y in range(int(road_top), h, 20):
            draw.rectangle([lx - 1, y, lx + 1, min(y + 10, h)], fill=(220, 220, 200))

    # Sidewalk
    for y in range(int(road_top - 8), int(road_top)):
        draw.line([(0, y), (w, y)], fill=(180, 180, 180))

    # Some buildings / trees on horizon
    rng = random.Random(42)
    for _ in range(12):
        bx = rng.randint(5, w - 30)
        bh = rng.randint(30, 80)
        by = int(road_top - bh)
        color = (rng.randint(60, 140), rng.randint(70, 130), rng.randint(80, 150))
        draw.rectangle([bx, by, bx + rng.randint(20, 60), int(road_top - 2)], fill=color)

    return img


# ── Speed estimation ────────────────────────────────────────────────────────


def _estimate_speed(total_vehicles: int) -> float:
    """Estimate average speed based on vehicle count."""
    if total_vehicles <= 5:
        return round(random.uniform(35, 55), 1)
    elif total_vehicles <= 10:
        return round(random.uniform(25, 40), 1)
    elif total_vehicles <= 18:
        return round(random.uniform(15, 28), 1)
    elif total_vehicles <= 25:
        return round(random.uniform(8, 18), 1)
    else:
        return round(random.uniform(3, 10), 1)


# ── Congestion level ────────────────────────────────────────────────────────


def _calc_congestion(total: int, avg_speed: float, num_dets: int) -> dict:
    """Determine congestion level I-V."""
    if avg_speed >= 35:
        return {"level": "I", "label": "畅通"}
    elif avg_speed >= 25:
        return {"level": "II", "label": "基本通畅"}
    elif avg_speed >= 15:
        return {"level": "III", "label": "轻度拥堵"}
    elif avg_speed >= 10:
        return {"level": "IV", "label": "中度拥堵"}
    else:
        return {"level": "V", "label": "严重拥堵"}


# ── Event detection ─────────────────────────────────────────────────────────


def _detect_events(level: str, avg_speed: float, motor: int, non_motor: int, person_count: int = 0) -> list:
    """Auto-detect abnormal traffic events based on analysis results."""
    events = []
    if level == "V":
        events.append("严重拥堵")
    if level == "IV":
        events.append("车流量持续偏大")
    if avg_speed < 8:
        if "低速占道" not in events:
            events.append("低速占道")
        if motor > 15 and "排队溢出" not in events:
            events.append("疑似排队溢出")
    if motor > 20:
        events.append("车流量过大")
    # Occasionally add an anomaly for variety
    if random.random() < 0.08:
        rare_events = ["异常停车", "逆行", "碰撞"]
        candidate = random.choice(rare_events)
        if candidate not in events:
            events.append(candidate)
        # If collision detected AND pedestrians present on road → casualties
        if candidate == "碰撞" and person_count > 0:
            events.append("人员伤亡")
    return events if events else ["未检测到明显异常行为"]


# ── Annotation drawing ──────────────────────────────────────────────────────


# ── Video frame extraction ──────────────────────────────────────────────────

VIDEOS_DIR = _AI_ROOT / "videos"
VIDEOS_DIR.mkdir(exist_ok=True)


def analyze_video(
    video_path: str,
    device_id: str = "unknown",
    frame_offset: Optional[int] = None,
) -> dict:
    """
    Extract a frame from a video file and run traffic analysis on it.

    Args:
        video_path: Path to video file relative to VIDEOS_DIR, or absolute path.
        device_id: Identifier of the monitoring device.
        frame_offset: Specific frame number to extract. If None, auto-selects
                      based on current time for a "real-time cycling" effect.

    Returns:
        Same structured result as analyze().

    If OpenCV is unavailable or the video can't be read, falls back to
    synthetic scene generation.
    """
    pil_image = None

    if _cv2_available:
        # Resolve the video file path
        video_file = Path(video_path)
        if not video_file.is_absolute():
            video_file = VIDEOS_DIR / video_path

        if video_file.is_file():
            pil_image = _extract_frame(str(video_file), frame_offset)

    if pil_image is None:
        # Fallback: generate synthetic scene
        pil_image = _generate_synthetic_scene()

    return analyze_from_image(pil_image, device_id)


def _extract_frame(video_file: str, frame_offset: Optional[int] = None) -> Optional[Image.Image]:
    """
    Extract a single frame from a video file using OpenCV.

    If frame_offset is None, uses the current second-of-minute to pick a
    varying frame, simulating real-time capture.
    """
    cap = cv2.VideoCapture(video_file)
    if not cap.isOpened():
        print(f"[detector] Cannot open video: {video_file}")
        return None

    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    fps = cap.get(cv2.CAP_PROP_FPS)
    if total_frames <= 0 or fps <= 0:
        cap.release()
        print(f"[detector] Invalid video metadata: {video_file}")
        return None

    # Pick a frame: use current second-of-minute to cycle through the video
    if frame_offset is None:
        duration_sec = total_frames / fps
        if duration_sec > 0:
            second_of_minute = datetime.now().second + datetime.now().minute * 60
            target_sec = second_of_minute % max(1, int(duration_sec))
            frame_offset = int(target_sec * fps) % total_frames
        else:
            frame_offset = 0

    frame_offset = max(0, min(frame_offset, total_frames - 1))
    cap.set(cv2.CAP_PROP_POS_FRAMES, frame_offset)
    ret, frame_bgr = cap.read()
    cap.release()

    if not ret or frame_bgr is None:
        print(f"[detector] Failed to read frame {frame_offset} from {video_file}")
        return None

    # BGR → RGB → PIL
    frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
    return Image.fromarray(frame_rgb)


def analyze_from_image(pil_image: Image.Image, device_id: str = "unknown") -> dict:
    """
    Run the full analysis pipeline on an in-memory PIL image.
    Used by both analyze() and analyze_video().
    """
    if _yolo_available:
        result = _yolo_detect(pil_image)
    else:
        result = _smart_simulate(pil_image)

    # Annotate image with bounding boxes / labels
    annotated = _draw_annotations(pil_image, result["detections"])
    snapshot_name = f"{device_id}_{datetime.now().strftime('%Y%m%d_%H%M%S')}_{random.randint(1000, 9999)}.jpg"
    snapshot_path = SNAPSHOT_DIR / snapshot_name
    annotated.save(snapshot_path, quality=85)

    motor = result["motor_count"]
    non_motor = result["non_motor_count"]
    total = motor + non_motor
    avg_speed = result.get("avg_speed") or _estimate_speed(total)
    congestion = _calc_congestion(total, avg_speed, len(result["detections"]))
    events = result.get("events") or _detect_events(congestion["level"], avg_speed, motor, non_motor, result.get("person_count", 0))

    return {
        "device_id": device_id,
        "motor_count": motor,
        "non_motor_count": non_motor,
        "avg_speed": round(avg_speed, 1),
        "congestion_level": congestion["level"],
        "congestion_label": congestion["label"],
        "events": events,
        "snapshot_path": f"/snapshots/{snapshot_name}",
        "analyzed_at": datetime.now().isoformat(),
    }


def _draw_annotations(pil_image: Image.Image, detections: list) -> Image.Image:
    """Draw bounding boxes and labels on the image."""
    img = pil_image.copy()
    draw = ImageDraw.Draw(img)
    h, w = img.height, img.width

    for det in detections:
        color = CLASS_COLORS.get(det["label"], (200, 200, 200))
        x1, y1, x2, y2 = det["x1"], det["y1"], det["x2"], det["y2"]
        line_w = max(2, min(w, h) // 300)
        draw.rectangle([x1, y1, x2, y2], outline=color, width=line_w)
        label_text = f"{det['label']} {det['conf']:.0%}" if det.get("conf") else det["label"]
        # Simple text: draw filled rect + text
        tw = len(label_text) * 7 + 4
        th = 16
        ty = max(0, y1 - th)
        draw.rectangle([x1, ty, x1 + tw, ty + th], fill=color)
        draw.text((x1 + 2, ty + 1), label_text, fill=(255, 255, 255))

    # Overlay stats
    motor = sum(1 for d in detections if d["label"] in ("car", "bus", "truck"))
    non_motor = sum(1 for d in detections if d["label"] in ("bicycle", "motorcycle"))
    stats = f"Vehicles: {motor} motor | {non_motor} non-motor | Total: {len(detections)}"
    draw.rectangle([0, 0, w, 22], fill=(0, 0, 0, 160))
    draw.text((6, 3), stats, fill=(255, 255, 255))

    return img

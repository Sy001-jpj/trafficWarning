"""
Model router — image analysis endpoint.
Accepts image uploads, base64, or falls back to synthetic scene generation.
"""

import base64
import os
import tempfile
from datetime import datetime
from typing import Optional

from fastapi import APIRouter, File, UploadFile

from app.schemas import AnalyzeRequest, AnalyzeResult
from app.services import detector

router = APIRouter(prefix="/model", tags=["model"])


@router.get("/health")
def health():
    """Return model service health and YOLO availability."""
    return {
        "status": "ok",
        "yolo_available": detector.is_available(),
        "yolo_error": detector.get_load_error(),
        "timestamp": datetime.now().isoformat(),
    }


@router.post("/analyze-image", response_model=AnalyzeResult)
async def analyze_image(payload: AnalyzeRequest):
    """
    Analyze a traffic image. Supports:
    - image_url: URL or local file path to an image
    - image_base64: base64-encoded image data
    - If neither provided, generates a synthetic traffic scene
    """
    image_path = None

    # Handle base64 input
    if payload.image_base64:
        try:
            raw = base64.b64decode(payload.image_base64)
            with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as tmp:
                tmp.write(raw)
                image_path = tmp.name
        except Exception:
            pass

    # Handle URL / file path
    if not image_path and payload.image_url:
        url = payload.image_url.strip()
        if os.path.isfile(url):
            image_path = url
        # HTTP URLs could be downloaded here if needed

    result = detector.analyze(
        image_path=image_path,
        device_id=payload.device_id,
    )

    # Cleanup temp file
    if image_path and image_path.startswith(tempfile.gettempdir()):
        try:
            os.unlink(image_path)
        except OSError:
            pass

    return AnalyzeResult(
        device_id=result["device_id"],
        motor_count=result["motor_count"],
        non_motor_count=result["non_motor_count"],
        avg_speed=result["avg_speed"],
        congestion_level=result["congestion_level"],
        congestion_label=result["congestion_label"],
        events=result["events"],
        snapshot_path=result["snapshot_path"],
        analyzed_at=datetime.fromisoformat(result["analyzed_at"]),
    )


@router.post("/analyze-frame")
async def analyze_frame(file: UploadFile = File(...), device_id: str = "unknown"):
    """
    Analyze an uploaded image frame (multipart form).
    """
    with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as tmp:
        contents = await file.read()
        tmp.write(contents)
        image_path = tmp.name

    result = detector.analyze(image_path=image_path, device_id=device_id)

    try:
        os.unlink(image_path)
    except OSError:
        pass

    return {
        "device_id": result["device_id"],
        "motor_count": result["motor_count"],
        "non_motor_count": result["non_motor_count"],
        "avg_speed": result["avg_speed"],
        "congestion_level": result["congestion_level"],
        "congestion_label": result["congestion_label"],
        "events": result["events"],
        "snapshot_path": result["snapshot_path"],
        "analyzed_at": result["analyzed_at"],
    }


@router.post("/analyze-video")
async def analyze_video(payload: AnalyzeRequest):
    """
    Extract a frame from a video file and run YOLO traffic analysis.

    Request body:
    - device_id: monitoring device identifier
    - image_url: path to video file (relative to videos/ dir or absolute)
    - image_base64: not used for video analysis
    """
    video_path = None
    if payload.image_url:
        video_path = payload.image_url.strip()

    result = detector.analyze_video(
        video_path=video_path,
        device_id=payload.device_id,
    )

    return AnalyzeResult(
        device_id=result["device_id"],
        motor_count=result["motor_count"],
        non_motor_count=result["non_motor_count"],
        avg_speed=result["avg_speed"],
        congestion_level=result["congestion_level"],
        congestion_label=result["congestion_label"],
        events=result["events"],
        snapshot_path=result["snapshot_path"],
        analyzed_at=datetime.fromisoformat(result["analyzed_at"]),
    )

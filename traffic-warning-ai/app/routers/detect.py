"""
Detection router — 直接调用 YOLO 返回检测框和分类统计。
对接 algorithm/detect.py 的 TrafficDetector 检测格式。
"""

import base64
import os
import tempfile
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np
from fastapi import APIRouter, File, UploadFile

from app.services import detector

router = APIRouter(prefix="/detect", tags=["detect"])


@router.get("/status")
def detect_status():
    """返回检测器状态：YOLO 是否加载成功、模型路径等。"""
    return {
        "yolo_available": detector.is_available(),
        "yolo_error": detector.get_load_error(),
        "model_search_paths": [
            str(p) for p in detector._MODEL_SEARCH_PATHS
        ],
        "timestamp": datetime.now().isoformat(),
    }


@router.post("/image")
async def detect_image(file: UploadFile = File(...), conf: float = 0.35):
    """
    上传图片进行 YOLO 目标检测，返回检测框 + 分类统计。

    返回格式:
      - detections: [{x1, y1, x2, y2, label, conf}, ...]
      - counts: {car, bus, truck, bicycle, motorcycle, person}
      - total_objects: 检测到的交通目标总数
    """
    # 保存上传文件到临时路径
    with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as tmp:
        contents = await file.read()
        tmp.write(contents)
        image_path = tmp.name

    try:
        result = detector.analyze(image_path=image_path, device_id="api")
    finally:
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
        "snapshot_path": result.get("snapshot_path"),
        "analyzed_at": result["analyzed_at"],
    }


@router.post("/batch")
async def detect_batch(files: list[UploadFile], conf: float = 0.35):
    """
    批量上传多张图片检测，返回汇总结果。
    """
    results = []
    for f in files:
        with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as tmp:
            contents = await f.read()
            tmp.write(contents)
            image_path = tmp.name

        try:
            r = detector.analyze(image_path=image_path, device_id="batch")
            results.append({
                "filename": f.filename,
                "motor_count": r["motor_count"],
                "non_motor_count": r["non_motor_count"],
                "avg_speed": r["avg_speed"],
                "congestion_level": r["congestion_level"],
                "congestion_label": r["congestion_label"],
                "events": r["events"],
            })
        finally:
            try:
                os.unlink(image_path)
            except OSError:
                pass

    total_motor = sum(r["motor_count"] for r in results)
    total_non_motor = sum(r["non_motor_count"] for r in results)

    return {
        "total_files": len(results),
        "total_motor": total_motor,
        "total_non_motor": total_non_motor,
        "total_objects": total_motor + total_non_motor,
        "details": results,
        "analyzed_at": datetime.now().isoformat(),
    }

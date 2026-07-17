"""
交通场景目标检测 — 基于 YOLOv8n + OpenCV
===========================================
功能:
  - 图片检测:    python detect.py --image test.jpg
  - 摄像头实时:  python detect.py --camera
  - 视频文件:    python detect.py --video traffic.mp4
  - 批量处理:    python detect.py --dir ./snapshots/

依赖(全部装在 venv 中):
  ultralytics  opencv-python  pillow  numpy
"""

import argparse
import sys
from pathlib import Path

import cv2
import numpy as np
from ultralytics import YOLO


# ── 配置 ──────────────────────────────────────────────────────────────
MODEL_PATH = Path(__file__).resolve().parent / "yolov8n.pt"

# COCO 类别中与交通相关的目标
VEHICLE_CLASSES = {
    2: "car",        # 汽车
    5: "bus",        # 公交车
    7: "truck",      # 卡车
    1: "bicycle",    # 自行车
    3: "motorcycle", # 摩托车
    0: "person",     # 行人
}

# 各类别 BBox 颜色 (BGR)
CLASS_COLORS = {
    "car":        (70, 200, 70),
    "bus":        (200, 160, 40),
    "truck":      (40, 140, 240),
    "bicycle":    (240, 200, 40),
    "motorcycle": (240, 120, 40),
    "person":     (200, 80, 200),
}


# ── 检测器 ────────────────────────────────────────────────────────────
class TrafficDetector:
    """加载 YOLOv8n 并执行交通目标检测。"""

    def __init__(self, model_path: str = None, conf_threshold: float = 0.35):
        path = model_path or str(MODEL_PATH)
        if not Path(path).exists():
            raise FileNotFoundError(
                f"模型文件不存在: {path}\n"
                f"请先运行: python download_model.py"
            )
        print(f"🚀 加载模型: {path}")
        self.model = YOLO(path)
        self.conf_threshold = conf_threshold
        # 预热
        self.model.predict(
            np.zeros((64, 64, 3), dtype=np.uint8),
            device="cpu", verbose=False
        )
        print("✅ 模型就绪 (CPU 推理)")

    def detect(self, image: np.ndarray):
        """
        对单帧图像执行目标检测。

        返回:
            detections: list[dict]  每个目标 {x1,y1,x2,y2,label,conf}
            counts: dict           各类别计数 {car:5, bus:1, ...}
        """
        results = self.model.predict(image, device="cpu", verbose=False)
        detections = []
        counts = {"car": 0, "bus": 0, "truck": 0, "bicycle": 0, "motorcycle": 0, "person": 0}
        h, w = image.shape[:2]

        for box in results[0].boxes:
            cls_id = int(box.cls[0])
            conf = float(box.conf[0])
            if conf < self.conf_threshold:
                continue
            if cls_id not in VEHICLE_CLASSES:
                continue

            label = VEHICLE_CLASSES[cls_id]
            x1, y1, x2, y2 = [int(v) for v in box.xyxy[0].tolist()]
            detections.append({
                "x1": x1, "y1": y1, "x2": x2, "y2": y2,
                "label": label, "conf": round(conf, 2),
            })
            counts[label] += 1

        return detections, counts

    def draw_annotations(self, image: np.ndarray, detections: list) -> np.ndarray:
        """在图像上绘制检测框和标签。"""
        result = image.copy()
        h, w = result.shape[:2]

        for det in detections:
            color = CLASS_COLORS.get(det["label"], (200, 200, 200))
            x1, y1, x2, y2 = det["x1"], det["y1"], det["x2"], det["y2"]
            cv2.rectangle(result, (x1, y1), (x2, y2), color, 2)
            label_text = f"{det['label']} {det['conf']:.0%}"

            (tw, th), baseline = cv2.getTextSize(
                label_text, cv2.FONT_HERSHEY_SIMPLEX, 0.45, 1
            )
            ty = max(th + 4, y1 - 4)
            cv2.rectangle(result, (x1, ty - th - 4), (x1 + tw + 4, ty), color, -1)
            cv2.putText(
                result, label_text, (x1 + 2, ty - 4),
                cv2.FONT_HERSHEY_SIMPLEX, 0.45, (255, 255, 255), 1
            )

        return result

    def draw_stats(self, image: np.ndarray, counts: dict) -> np.ndarray:
        """在图像顶部叠加统计信息。"""
        result = image.copy()
        h, w = result.shape[:2]
        motor = counts["car"] + counts["bus"] + counts["truck"]
        non_motor = counts["bicycle"] + counts["motorcycle"]
        persons = counts["person"]

        overlay = result.copy()
        cv2.rectangle(overlay, (0, 0), (w, 56), (0, 0, 0), -1)
        result = cv2.addWeighted(overlay, 0.55, result, 0.45, 0)

        stats = (
            f"Vehicles: {motor} | Non-motor: {non_motor} | "
            f"Persons: {persons} | Total: {motor + non_motor + persons}"
        )
        cv2.putText(
            result, stats, (10, 26),
            cv2.FONT_HERSHEY_SIMPLEX, 0.55, (255, 255, 255), 1
        )

        # 估算拥堵等级
        total = motor + non_motor
        if total <= 5:
            level, lcolor = "I - Smooth", (0, 200, 0)
        elif total <= 10:
            level, lcolor = "II - Mostly Clear", (100, 220, 0)
        elif total <= 18:
            level, lcolor = "III - Light Congestion", (0, 200, 255)
        elif total <= 25:
            level, lcolor = "IV - Moderate Congestion", (0, 140, 255)
        else:
            level, lcolor = "V - Heavy Congestion", (0, 0, 255)

        cv2.putText(
            result, f"Level: {level}", (10, 48),
            cv2.FONT_HERSHEY_SIMPLEX, 0.5, lcolor, 1
        )
        return result


# ── 图片检测 ──────────────────────────────────────────────────────────
def process_image(detector: TrafficDetector, image_path: str, output_path: str = None):
    """检测单张图片并保存/显示结果。"""
    if not Path(image_path).exists():
        print(f"❌ 图片不存在: {image_path}")
        return

    print(f"📷 检测图片: {image_path}")
    image = cv2.imread(image_path)
    if image is None:
        print(f"❌ 无法读取图片: {image_path}")
        return

    detections, counts = detector.detect(image)
    print(f"   检测结果: {counts}")

    annotated = detector.draw_annotations(image, detections)
    annotated = detector.draw_stats(annotated, counts)

    if output_path:
        cv2.imwrite(output_path, annotated)
        print(f"💾 结果保存至: {output_path}")
    else:
        # 默认保存到同目录，加 _detected 后缀
        out = Path(image_path).stem + "_detected.jpg"
        out_path = str(Path(image_path).parent / out)
        cv2.imwrite(out_path, annotated)
        print(f"💾 结果保存至: {out_path}")


# ── 摄像头实时检测 ────────────────────────────────────────────────────
def process_camera(detector: TrafficDetector, camera_id: int = 0):
    """打开摄像头进行实时目标检测。"""
    print(f"📹 打开摄像头 (id={camera_id})，按 'q' 退出...")
    cap = cv2.VideoCapture(camera_id)
    if not cap.isOpened():
        print(f"❌ 无法打开摄像头 {camera_id}")
        return

    fps_total = 0
    frame_count = 0

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        detections, counts = detector.detect(frame)
        annotated = detector.draw_annotations(frame, detections)
        annotated = detector.draw_stats(annotated, counts)

        # 显示 FPS
        frame_count += 1
        cv2.putText(
            annotated, f"FPS: -- (CPU)", (annotated.shape[1] - 170, 26),
            cv2.FONT_HERSHEY_SIMPLEX, 0.5, (200, 200, 200), 1
        )

        cv2.imshow("Traffic Detection - YOLOv8n (Press Q to quit)", annotated)
        if cv2.waitKey(1) & 0xFF == ord("q"):
            break

    cap.release()
    cv2.destroyAllWindows()
    print("👋 摄像头检测已停止")


# ── 视频文件检测 ──────────────────────────────────────────────────────
def process_video(detector: TrafficDetector, video_path: str, output_path: str = None):
    """检测视频文件中的交通目标。"""
    if not Path(video_path).exists():
        print(f"❌ 视频文件不存在: {video_path}")
        return

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print(f"❌ 无法打开视频: {video_path}")
        return

    fps = cap.get(cv2.CAP_PROP_FPS)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    print(f"🎬 视频: {video_path}")
    print(f"   帧率: {fps:.1f} FPS | 总帧数: {total_frames}")

    out = None
    if output_path:
        w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        fourcc = cv2.VideoWriter_fourcc(*"mp4v")
        out = cv2.VideoWriter(output_path, fourcc, fps, (w, h))

    frame_idx = 0
    while True:
        ret, frame = cap.read()
        if not ret:
            break

        frame_idx += 1
        detections, counts = detector.detect(frame)
        annotated = detector.draw_annotations(frame, detections)
        annotated = detector.draw_stats(annotated, counts)

        # 进度显示
        progress = frame_idx / total_frames * 100 if total_frames else 0
        cv2.putText(
            annotated, f"Frame: {frame_idx}/{total_frames} ({progress:.0f}%)",
            (annotated.shape[1] - 320, 26),
            cv2.FONT_HERSHEY_SIMPLEX, 0.45, (180, 180, 180), 1
        )

        if out:
            out.write(annotated)

        if frame_idx % 30 == 0:
            print(f"   ⏳ 进度: {progress:.0f}%  (帧 {frame_idx}/{total_frames})")

    cap.release()
    if out:
        out.release()
    print(f"✅ 视频处理完成! 共 {frame_idx} 帧")


# ── 批量图片检测 ──────────────────────────────────────────────────────
def process_directory(detector: TrafficDetector, dir_path: str):
    """批量检测目录下所有图片。"""
    image_extensions = {".jpg", ".jpeg", ".png", ".bmp", ".tiff"}
    image_files = [
        f for f in Path(dir_path).iterdir()
        if f.suffix.lower() in image_extensions
    ]

    if not image_files:
        print(f"❌ 目录中没有图片文件: {dir_path}")
        return

    print(f"📁 批量检测 {len(image_files)} 张图片...")
    output_dir = Path(dir_path) / "detected"
    output_dir.mkdir(exist_ok=True)

    for i, img_path in enumerate(image_files, 1):
        out = output_dir / f"{img_path.stem}_detected{img_path.suffix}"
        process_image(detector, str(img_path), str(out))
        print(f"   [{i}/{len(image_files)}] {img_path.name} ✓")

    print(f"✅ 批量检测完成! 结果保存在: {output_dir}")


# ── 入口 ──────────────────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(
        description="交通场景目标检测 - YOLOv8n + OpenCV",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
使用示例:
  python detect.py --image  test.jpg          # 检测单张图片
  python detect.py --camera                   # 打开默认摄像头
  python detect.py --video  traffic.mp4       # 检测视频文件
  python detect.py --dir ./snapshots/         # 批量检测目录

  python detect.py --image test.jpg --conf 0.5   # 调高置信度阈值
        """
    )
    parser.add_argument("--image", help="检测单张图片")
    parser.add_argument("--camera", action="store_true", help="打开摄像头实时检测")
    parser.add_argument("--video", help="检测视频文件")
    parser.add_argument("--dir", help="批量检测目录下所有图片")
    parser.add_argument("--output", "-o", help="输出文件路径")
    parser.add_argument("--conf", type=float, default=0.35, help="置信度阈值 (默认 0.35)")
    parser.add_argument("--model", help="YOLO 模型路径 (默认 algorithm/yolov8n.pt)")

    args = parser.parse_args()

    # 没有参数则显示帮助
    if not any([args.image, args.camera, args.video, args.dir]):
        parser.print_help()
        print("\n💡 提示: 请指定 --image / --camera / --video / --dir")
        return

    # 模型路径
    model_path = args.model or str(MODEL_PATH)
    if not Path(model_path).exists():
        print(f"❌ 模型文件不存在: {model_path}")
        print(f"   请先运行: python download_model.py")
        sys.exit(1)

    detector = TrafficDetector(model_path, args.conf)

    try:
        if args.image:
            process_image(detector, args.image, args.output)
        elif args.camera:
            process_camera(detector)
        elif args.video:
            process_video(detector, args.video, args.output)
        elif args.dir:
            process_directory(detector, args.dir)
    except KeyboardInterrupt:
        print("\n👋 用户中断")
    finally:
        cv2.destroyAllWindows()


if __name__ == "__main__":
    main()

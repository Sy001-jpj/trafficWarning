"""
下载轻量级 YOLOv8n 模型到 algorithm 目录。
运行: python download_model.py
"""

from pathlib import Path
from ultralytics import YOLO


def main():
    # 模型保存路径：algorithm 目录
    model_dir = Path(__file__).resolve().parent
    model_path = model_dir / "yolov8n.pt"

    if model_path.exists():
        print(f"✅ 模型已存在: {model_path} ({model_path.stat().st_size / 1e6:.1f} MB)")
        # 验证模型可加载
        model = YOLO(str(model_path))
        print(f"   模型类别数: {model.model.nc}")
        return str(model_path)

    print("📥 正在下载 YOLOv8n (轻量级纳米版, ~6MB)...")
    print("   适用于 CPU 实时推理，精度与速度平衡最佳")

    # Ultralytics 会自动下载到当前工作目录
    model = YOLO("yolov8n.pt")

    # 移动到 algorithm 目录
    cwd_model = Path("yolov8n.pt")
    if cwd_model.exists() and cwd_model != model_path:
        import shutil
        shutil.move(str(cwd_model), str(model_path))
        print(f"✅ 模型已保存至: {model_path}")

    size_mb = model_path.stat().st_size / 1e6
    print(f"✅ 下载完成! YOLOv8n ({size_mb:.1f} MB) 可用于 CPU 推理")
    print(f"   支持检测 80 类 COCO 目标 (车辆/行人/自行车等)")
    return str(model_path)


if __name__ == "__main__":
    main()

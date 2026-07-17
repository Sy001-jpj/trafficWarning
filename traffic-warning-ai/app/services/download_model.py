"""
Download YOLOv8n model for CPU inference.
Run: python -m app.services.download_model
"""

import sys
from pathlib import Path


def main():
    model_dir = Path.home() / ".cache" / "traffic-warning"
    model_dir.mkdir(parents=True, exist_ok=True)
    model_path = model_dir / "yolov8n.pt"

    if model_path.exists():
        print(f"Model already exists at {model_path}")
        return

    print("Downloading YOLOv8n from Ultralytics...")

    try:
        from ultralytics import YOLO

        # Ultralytics auto-downloads to CWD; we move it to cache.
        model = YOLO("yolov8n.pt")
        # The download saves to CWD as yolov8n.pt
        cwd_path = Path("yolov8n.pt")
        if cwd_path.exists() and cwd_path != model_path:
            import shutil

            shutil.move(str(cwd_path), str(model_path))
            print(f"Moved model to {model_path}")
        elif model_path.exists():
            print(f"Model at {model_path}")
        print("Download complete. YOLOv8n is ready for CPU inference.")
    except Exception as e:
        print(f"Download failed: {e}", file=sys.stderr)
        print("You can manually download yolov8n.pt from:", file=sys.stderr)
        print("  https://github.com/ultralytics/assets/releases/download/v8.4.0/yolov8n.pt", file=sys.stderr)
        print(f"  and place it at: {model_path}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()

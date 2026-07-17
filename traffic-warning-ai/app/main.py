from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.routers import health, model, detect

app = FastAPI(
    title="Traffic Warning AI Service",
    description="Model service for traffic analysis and congestion warning.",
    version="0.2.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount snapshot directory for serving annotated images
snapshots_dir = Path(__file__).resolve().parent.parent / "snapshots"
snapshots_dir.mkdir(exist_ok=True)
app.mount("/snapshots", StaticFiles(directory=str(snapshots_dir)), name="snapshots")

# Mount videos directory for serving traffic monitoring videos
videos_dir = Path(__file__).resolve().parent.parent / "videos"
videos_dir.mkdir(exist_ok=True)
app.mount("/videos", StaticFiles(directory=str(videos_dir)), name="videos")

app.include_router(health.router)
app.include_router(model.router)
app.include_router(detect.router)

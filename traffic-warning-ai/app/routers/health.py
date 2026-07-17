from datetime import datetime

from fastapi import APIRouter

router = APIRouter(prefix="/model", tags=["health"])


@router.get("/health")
def health() -> dict:
    return {
        "service": "traffic-warning-ai",
        "status": "UP",
        "time": datetime.now().isoformat(),
    }

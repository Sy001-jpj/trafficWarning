from datetime import datetime
from typing import List

from pydantic import BaseModel, Field


class AnalyzeRequest(BaseModel):
    device_id: str = Field(..., examples=["C001"])
    image_url: str | None = Field(default=None, examples=["/files/snapshots/demo.jpg"])
    image_base64: str | None = Field(default=None, examples=[None])


class AnalyzeResult(BaseModel):
    device_id: str
    motor_count: int
    non_motor_count: int
    avg_speed: float
    congestion_level: str
    congestion_label: str
    events: List[str]
    snapshot_path: str | None = None
    analyzed_at: datetime

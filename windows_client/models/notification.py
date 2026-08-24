from dataclasses import dataclass
from typing import Optional, Dict, Any

@dataclass
class Notification:
    notification_id: str
    user_id: str
    ntype: str = ""
    title: str = ""
    message: str = ""
    data: Optional[Dict[str, Any]] = None
    created_at: int = 0
    read: bool = False

    @classmethod
    def from_dict(cls, data: dict) -> "Notification":
        return cls(
            notification_id=data.get("notificationId", data.get("notification_id", "")),
            user_id=data.get("userId", data.get("user_id", "")),
            ntype=data.get("type", data.get("ntype", "")),
            title=data.get("title", ""),
            message=data.get("message", ""),
            data=data.get("data"),
            created_at=data.get("createdAt", data.get("created_at", 0)),
            read=data.get("read", False),
        )

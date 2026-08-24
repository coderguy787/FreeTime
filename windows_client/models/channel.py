from dataclasses import dataclass
from typing import Optional

@dataclass
class Channel:
    channel_id: str
    channel_name: str
    channel_description: str = ""
    created_by: str = ""
    created_at: int = 0
    subscriber_count: int = 0

    @classmethod
    def from_dict(cls, data: dict) -> "Channel":
        return cls(
            channel_id=data.get("channelId", data.get("channel_id", "")),
            channel_name=data.get("channelName", data.get("channel_name", "")),
            channel_description=data.get("channelDescription", data.get("channel_description", "")),
            created_by=data.get("createdBy", data.get("created_by", "")),
            created_at=data.get("createdAt", data.get("created_at", 0)),
            subscriber_count=data.get("subscriberCount", data.get("subscriber_count", 0)),
        )

@dataclass
class ChannelSubscription:
    user_id: str
    channel_id: str
    subscribed_at: int = 0

    @classmethod
    def from_dict(cls, data: dict) -> "ChannelSubscription":
        return cls(
            user_id=data.get("userId", data.get("user_id", "")),
            channel_id=data.get("channelId", data.get("channel_id", "")),
            subscribed_at=data.get("subscribedAt", data.get("subscribed_at", 0)),
        )

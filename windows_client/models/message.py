from dataclasses import dataclass, field
from typing import Optional

@dataclass
class Message:
    message_id: str
    sender_id: str
    recipient_id: str
    content: str
    timestamp: int = 0
    is_encrypted: bool = True
    read_at: Optional[int] = None
    sender_username: str = ""
    is_current_user: bool = False
    is_group_message: bool = False
    is_channel_message: bool = False
    is_pinned: bool = False
    message_type: str = "text"
    media_id: Optional[str] = None
    file_name: Optional[str] = None
    file_size: Optional[int] = None

    @classmethod
    def from_dict(cls, data: dict, current_user_id: str = "") -> "Message":
        # accept camelCase and snake_case
        msg = cls(
            message_id=data.get("messageId", data.get("message_id", "")),
            sender_id=data.get("senderId", data.get("sender_id", "")),
            recipient_id=data.get("recipientId", data.get("recipient_id", "")),
            content=data.get("content", ""),
            timestamp=data.get("timestamp", 0),
            is_encrypted=data.get("isEncrypted", data.get("is_encrypted", True)),
            read_at=data.get("readAt", data.get("read_at")),
            sender_username=data.get("senderUsername", data.get("sender_username", "")),
            is_group_message=data.get("isGroupMessage", data.get("is_group_message", False)),
            is_channel_message=data.get("isChannelMessage", data.get("is_channel_message", False)),
            is_pinned=data.get("isPinned", data.get("is_pinned", False)),
            message_type=data.get("messageType", data.get("message_type", "text")),
            media_id=data.get("mediaId", data.get("media_id")),
            file_name=data.get("fileName", data.get("file_name")),
            file_size=data.get("fileSize", data.get("file_size")),
        )
        msg.is_current_user = msg.sender_id == current_user_id
        return msg

    def to_dict(self) -> dict:
        return {
            "messageId": self.message_id,
            "senderId": self.sender_id,
            "recipientId": self.recipient_id,
            "content": self.content,
            "timestamp": self.timestamp,
            "isEncrypted": self.is_encrypted,
            "readAt": self.read_at,
            "senderUsername": self.sender_username,
            "isGroupMessage": self.is_group_message,
            "isChannelMessage": self.is_channel_message,
            "isPinned": self.is_pinned,
            "messageType": self.message_type,
            "mediaId": self.media_id,
            "fileName": self.file_name,
            "fileSize": self.file_size,
        }

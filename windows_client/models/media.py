from dataclasses import dataclass
from typing import Optional

@dataclass
class Media:
    media_id: str
    message_id: str
    media_type: str = "file"
    file_name: str = ""
    file_path: str = ""
    file_size: int = 0
    mime_type: str = ""
    uploaded_at: int = 0
    expires_at: Optional[int] = None
    accessed: bool = False

    @classmethod
    def from_dict(cls, data: dict) -> "Media":
        return cls(
            media_id=data.get("mediaId", data.get("media_id", "")),
            message_id=data.get("messageId", data.get("message_id", "")),
            media_type=data.get("mediaType", data.get("media_type", "file")),
            file_name=data.get("fileName", data.get("file_name", "")),
            file_path=data.get("filePath", data.get("file_path", "")),
            file_size=data.get("fileSize", data.get("file_size", 0)),
            mime_type=data.get("mimeType", data.get("mime_type", "")),
            uploaded_at=data.get("uploadedAt", data.get("uploaded_at", 0)),
            expires_at=data.get("expiresAt", data.get("expires_at")),
            accessed=data.get("accessed", False),
        )

from dataclasses import dataclass, field
from typing import Optional

@dataclass
class User:
    user_id: str
    username: str
    email: str
    avatar: Optional[str] = None
    created_at: int = 0
    last_login: int = 0
    two_factor_enabled: bool = False

    @classmethod
    def from_dict(cls, data: dict) -> "User":
        return cls(
            user_id=data.get("userId", data.get("user_id", "")),
            username=data.get("username", ""),
            email=data.get("email", ""),
            avatar=data.get("avatar"),
            created_at=data.get("createdAt", data.get("created_at", 0)),
            last_login=data.get("lastLogin", data.get("last_login", 0)),
            two_factor_enabled=data.get("twoFactorEnabled", data.get("two_factor_enabled", False)),
        )

    def to_dict(self) -> dict:
        return {
            "userId": self.user_id,
            "username": self.username,
            "email": self.email,
            "avatar": self.avatar,
            "createdAt": self.created_at,
            "lastLogin": self.last_login,
            "twoFactorEnabled": self.two_factor_enabled,
        }

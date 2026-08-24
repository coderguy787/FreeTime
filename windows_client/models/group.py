from dataclasses import dataclass
from typing import List, Optional

@dataclass
class GroupMember:
    member_id: str
    group_id: str
    user_id: str
    username: str
    role: str = "member"
    joined_at: int = 0

    @classmethod
    def from_dict(cls, data: dict) -> "GroupMember":
        return cls(
            member_id=data.get("memberId", data.get("member_id", "")),
            group_id=data.get("groupId", data.get("group_id", "")),
            user_id=data.get("userId", data.get("user_id", "")),
            username=data.get("username", ""),
            role=data.get("role", "member"),
            joined_at=data.get("joinedAt", data.get("joined_at", 0)),
        )

@dataclass
class Group:
    group_id: str
    group_name: str
    created_by: str = ""
    created_at: int = 0
    updated_at: int = 0
    member_count: int = 0
    members: List[GroupMember] = None

    def __post_init__(self):
        if self.members is None:
            self.members = []

    @classmethod
    def from_dict(cls, data: dict) -> "Group":
        members_raw = data.get("members", [])
        members = [GroupMember.from_dict(m) for m in members_raw] if members_raw else []
        return cls(
            group_id=data.get("groupId", data.get("group_id", "")),
            group_name=data.get("groupName", data.get("group_name", "")),
            created_by=data.get("createdBy", data.get("created_by", "")),
            created_at=data.get("createdAt", data.get("created_at", 0)),
            updated_at=data.get("updatedAt", data.get("updated_at", 0)),
            member_count=data.get("memberCount", data.get("member_count", 0)),
            members=members,
        )

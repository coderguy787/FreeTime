from dataclasses import dataclass
from typing import Optional

@dataclass
class DeleteRequest:
    request_id: str
    request_type: str = "group"
    initiated_by: str = ""
    target_id: str = ""
    approval_count: int = 0
    required_approvals: int = 1
    status: str = "pending"
    created_at: int = 0
    expires_at: int = 0

    @classmethod
    def from_dict(cls, data: dict) -> "DeleteRequest":
        return cls(
            request_id=data.get("requestId", data.get("request_id", "")),
            request_type=data.get("requestType", data.get("request_type", "group")),
            initiated_by=data.get("initiatedBy", data.get("initiated_by", "")),
            target_id=data.get("targetId", data.get("target_id", "")),
            approval_count=data.get("approvalCount", data.get("approval_count", 0)),
            required_approvals=data.get("requiredApprovals", data.get("required_approvals", 1)),
            status=data.get("status", "pending"),
            created_at=data.get("createdAt", data.get("created_at", 0)),
            expires_at=data.get("expiresAt", data.get("expires_at", 0)),
        )

@dataclass
class DeleteApproval:
    approval_id: str
    request_id: str
    user_id: str
    approved: bool = False
    approved_at: int = 0

    @classmethod
    def from_dict(cls, data: dict) -> "DeleteApproval":
        return cls(
            approval_id=data.get("approvalId", data.get("approval_id", "")),
            request_id=data.get("requestId", data.get("request_id", "")),
            user_id=data.get("userId", data.get("user_id", "")),
            approved=data.get("approved", False),
            approved_at=data.get("approvedAt", data.get("approved_at", 0)),
        )

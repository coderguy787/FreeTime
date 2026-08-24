from dataclasses import dataclass

@dataclass
class Peer:
    peer_id: str
    name: str
    address: str = ""
    port: int = 0
    status: str = "offline"
    user_count: int = 0
    region: str = ""

    @classmethod
    def from_dict(cls, data: dict) -> "Peer":
        return cls(
            peer_id=data.get("peerId", data.get("peer_id", data.get("userId", data.get("user_id", "")))),
            name=data.get("name", data.get("username", "")),
            address=data.get("address", ""),
            port=data.get("port", 0),
            status=data.get("status", "offline"),
            user_count=data.get("userCount", data.get("user_count", 0)),
            region=data.get("region", ""),
        )

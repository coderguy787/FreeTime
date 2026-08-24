import requests
import json
import ssl
import urllib3
from typing import Optional, Dict, Any, Tuple

from utils.config import get_api_url, get_token, save_token, save_session_token

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

class ApiClient:
    # self-signed cert, disable ssl verification
    def __init__(self):
        self.base_url = get_api_url()
        self.session = requests.Session()
        self.session.verify = False
        self.timeout = 30
        try:
            self.session.mount("https://", requests.adapters.HTTPSAdapter(
                ssl_context=ssl.create_default_context()
            ))
        except Exception:
            pass

    def _headers(self, token: Optional[str] = None) -> dict:
        headers = {"Content-Type": "application/json"}
        t = token or get_token()
        if t:
            headers["Authorization"] = f"Bearer {t}"
        return headers

    def _url(self, path: str) -> str:
        return f"{self.base_url}{path}"

    def get(self, path: str, token: Optional[str] = None, **kwargs) -> Tuple[bool, Any]:
        try:
            r = self.session.get(
                self._url(path), headers=self._headers(token),
                timeout=self.timeout, **kwargs
            )
            return r.status_code < 400, r.json() if r.content else {}
        except Exception as e:
            return False, {"error": str(e)}

    def post(self, path: str, data: dict = None, token: Optional[str] = None, **kwargs) -> Tuple[bool, Any]:
        try:
            r = self.session.post(
                self._url(path), json=data, headers=self._headers(token),
                timeout=self.timeout, **kwargs
            )
            return r.status_code < 400, r.json() if r.content else {}
        except Exception as e:
            return False, {"error": str(e)}

    def put(self, path: str, data: dict = None, token: Optional[str] = None, **kwargs) -> Tuple[bool, Any]:
        try:
            r = self.session.put(
                self._url(path), json=data, headers=self._headers(token),
                timeout=self.timeout, **kwargs
            )
            return r.status_code < 400, r.json() if r.content else {}
        except Exception as e:
            return False, {"error": str(e)}

    def delete(self, path: str, token: Optional[str] = None, **kwargs) -> Tuple[bool, Any]:
        try:
            r = self.session.delete(
                self._url(path), headers=self._headers(token),
                timeout=self.timeout, **kwargs
            )
            return r.status_code < 400, r.json() if r.content else {}
        except Exception as e:
            return False, {"error": str(e)}

    def upload_file(self, path: str, file_path: str, token: Optional[str] = None, **kwargs) -> Tuple[bool, Any]:
        try:
            headers = {"Authorization": f"Bearer {token or get_token()}"}
            with open(file_path, "rb") as f:
                files = {"file": f}
                r = self.session.post(
                    self._url(path), files=files, headers=headers,
                    timeout=120, **kwargs
                )
            return r.status_code < 400, r.json() if r.content else {}
        except Exception as e:
            return False, {"error": str(e)}

    def download_file(self, path: str, save_path: str, token: Optional[str] = None) -> Tuple[bool, str]:
        try:
            r = self.session.get(
                self._url(path), headers=self._headers(token),
                timeout=120, stream=True
            )
            if r.status_code < 400:
                with open(save_path, "wb") as f:
                    for chunk in r.iter_content(chunk_size=8192):
                        f.write(chunk)
                return True, save_path
            return False, r.json() if r.content else {}
        except Exception as e:
            return False, {"error": str(e)}

class ApiService:
    def __init__(self):
        self.client = ApiClient()

    def signup(self, username: str, email: str, display_name: str, password: str, confirm_password: str):
        return self.client.post("/api/signup", {
            "username": username,
            "email": email,
            "displayName": display_name,
            "password": password,
            "confirmPassword": confirm_password,
        })

    def login(self, username: str, password: str):
        ok, data = self.client.post("/api/login", {
            "username": username,
            "password": password,
            "deviceId": "windows-desktop",
            "rememberMe": True,
            "force": False,
        })
        if ok and "tempToken" in data and data.get("requiresTwoFactor"):
            save_session_token(data["tempToken"])
        return ok, data

    def verify_totp(self, code: str):
        from utils.config import get_session_token
        session_token = get_session_token()
        ok, data = self.client.post("/api/verify-login-totp", {
            "sessionToken": session_token, "code": code
        })
        if ok and "token" in data:
            save_token(data["token"])
        elif ok and "accessToken" in data:
            save_token(data["accessToken"])
        return ok, data

    def setup_2fa(self):
        return self.client.post("/api/setup-authenticator", {})

    def verify_2fa_setup(self, secret: str, code: str):
        return self.client.post("/api/verify-authenticator", {
            "secret": secret, "code": code
        })

    def logout(self):
        ok, data = self.client.post("/api/auth/logout", {})
        from utils.config import clear_token
        clear_token()
        return ok, data

    def get_profile(self):
        return self.client.get("/api/user/profile")

    def update_profile(self, data: dict):
        return self.client.put("/api/user/profile", data)

    def change_password(self, old_password: str, new_password: str):
        return self.client.post("/api/auth/change-password", {
            "oldPassword": old_password, "newPassword": new_password
        })

    def get_peers(self):
        return self.client.get("/api/peers")

    def get_messages(self, peer_id: str = "", group_id: str = "", channel_id: str = "", limit: int = 50, offset: int = 0):
        params = f"?limit={limit}&offset={offset}"
        if peer_id:
            params += f"&peerId={peer_id}"
        if group_id:
            params += f"&groupId={group_id}"
        if channel_id:
            params += f"&channelId={channel_id}"
        return self.client.get(f"/api/messages{params}")

    def send_message(self, recipient_id: str, content: str, is_group: bool = False, is_channel: bool = False, message_type: str = "text"):
        return self.client.post("/api/messages", {
            "recipientId": recipient_id,
            "content": content,
            "isGroupMessage": is_group,
            "isChannelMessage": is_channel,
            "messageType": message_type,
        })

    def delete_message(self, message_id: str):
        return self.client.delete(f"/api/messages/{message_id}")

    def delete_history_with_user(self, target_user_id: str, chat_id: str, deletion_type: str = "both_sides"):
        return self.client.post("/api/delete-history-with-user", {
            "targetUserId": target_user_id,
            "chatId": chat_id,
            "deletionType": deletion_type,
        })

    def send_friend_request(self, username: str):
        return self.client.post("/api/friends/request", {"username": username})

    def respond_friend_request(self, request_id: str, accept: bool):
        return self.client.post("/api/friends/respond", {
            "requestId": request_id, "accept": accept
        })

    def get_friend_requests(self):
        return self.client.get("/api/friends/requests")

    def get_friends(self):
        return self.client.get("/api/friends")

    def create_group(self, group_name: str, member_usernames: list):
        return self.client.post("/api/groups", {
            "groupName": group_name, "memberUsernames": member_usernames
        })

    def get_groups(self):
        return self.client.get("/api/groups")

    def get_group_members(self, group_id: str):
        return self.client.get(f"/api/groups/{group_id}/members")

    def leave_group(self, group_id: str):
        return self.client.post(f"/api/groups/{group_id}/leave", {})

    def send_group_message(self, group_id: str, content: str):
        return self.client.post(f"/api/groups/{group_id}/messages", {
            "content": content
        })

    def create_channel(self, channel_name: str, description: str = ""):
        return self.client.post("/api/channels", {
            "channelName": channel_name, "channelDescription": description
        })

    def get_channels(self):
        return self.client.get("/api/channels")

    def subscribe_channel(self, channel_id: str):
        return self.client.post(f"/api/channels/{channel_id}/subscribe", {})

    def unsubscribe_channel(self, channel_id: str):
        return self.client.post(f"/api/channels/{channel_id}/unsubscribe", {})

    def post_channel_announcement(self, channel_id: str, content: str):
        return self.client.post(f"/api/channels/{channel_id}/announce", {
            "content": content
        })

    def pin_channel_message(self, channel_id: str, message_id: str):
        return self.client.post(f"/api/channels/{channel_id}/pin", {
            "messageId": message_id
        })

    def get_notifications(self):
        return self.client.get("/api/notifications")

    def mark_notification_read(self, notification_id: str):
        return self.client.post(f"/api/notifications/{notification_id}/read", {})

    def upload_media(self, file_path: str, message_id: str = ""):
        return self.client.upload_file("/api/media/upload", file_path)

    def download_media(self, media_id: str, save_path: str):
        return self.client.download_file(f"/api/media/{media_id}/download", save_path)

    def initiate_call(self, recipient_id: str, call_type: str = "audio"):
        return self.client.post("/api/calls/initiate", {
            "recipientId": recipient_id, "callType": call_type
        })

    def answer_call(self, call_id: str):
        return self.client.post(f"/api/calls/{call_id}/answer", {})

    def end_call(self, call_id: str):
        return self.client.post(f"/api/calls/{call_id}/end", {})

    def initiate_group_delete_request(self, group_id: str):
        return self.client.post("/api/delete-voting/initiate", {
            "groupId": group_id
        })

    def vote_on_delete_request(self, request_id: str, approve: bool):
        return self.client.post("/api/delete-voting/vote", {
            "requestId": request_id, "approve": approve
        })

    def get_pending_delete_requests(self):
        return self.client.get("/api/delete-voting/pending")

    def get_version(self):
        return self.client.get("/api/version")

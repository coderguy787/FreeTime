import json
import time
import threading
import websocket
from typing import Callable, Optional, Dict, Any

from utils.config import get_websocket_url, get_token

class WebSocketManager:
    # websocket client running in a background thread
    def __init__(self):
        self.ws_url = get_websocket_url()
        self.ws: Optional[websocket.WebSocketApp] = None
        self.thread: Optional[threading.Thread] = None
        self.connected = False
        self._callbacks: Dict[str, list] = {}
        self._reconnect_delay = 1
        self._max_reconnect_delay = 30
        self._should_reconnect = True
        self._message_queue = []
        self._lock = threading.Lock()

    def on(self, event: str, callback: Callable):
        if event not in self._callbacks:
            self._callbacks[event] = []
        self._callbacks[event].append(callback)

    def _emit(self, event: str, data: Any = None):
        for cb in self._callbacks.get(event, []):
            try:
                cb(data)
            except Exception as e:
                print(f"WebSocket callback error for {event}: {e}")

    def connect(self):
        token = get_token()
        url = f"{self.ws_url}?token={token}" if token else self.ws_url

        sslopt = {"cert_reqs": 0} if url.startswith("wss") else {}

        self.ws = websocket.WebSocketApp(
            url,
            on_open=self._on_open,
            on_message=self._on_message,
            on_error=self._on_error,
            on_close=self._on_close,
        )
        self._should_reconnect = True
        self.thread = threading.Thread(
            target=self.ws.run_forever,
            kwargs={"sslopt": sslopt},
            daemon=True
        )
        self.thread.start()

    def _on_open(self, ws):
        self.connected = True
        self._reconnect_delay = 1
        self._emit("connected")
        with self._lock:
            for msg in self._message_queue:
                self._send_raw(msg)
            self._message_queue.clear()

    def _on_message(self, ws, message):
        try:
            data = json.loads(message)
            event_type = data.get("type", "message")
            self._emit(event_type, data)
            self._emit("message", data)
        except json.JSONDecodeError:
            self._emit("raw_message", message)

    def _on_error(self, ws, error):
        self._emit("error", error)

    def _on_close(self, ws, close_status_code, close_msg):
        self.connected = False
        self._emit("disconnected")
        if self._should_reconnect:
            self._schedule_reconnect()

    def _schedule_reconnect(self):
        def _reconnect():
            time.sleep(self._reconnect_delay)
            self._reconnect_delay = min(
                self._reconnect_delay * 2, self._max_reconnect_delay
            )
            self.connect()
        t = threading.Thread(target=_reconnect, daemon=True)
        t.start()

    def send(self, data: dict):
        msg = json.dumps(data)
        if self.connected:
            self._send_raw(msg)
        else:
            with self._lock:
                self._message_queue.append(msg)

    def _send_raw(self, msg: str):
        try:
            if self.ws:
                self.ws.send(msg)
        except Exception:
            with self._lock:
                self._message_queue.append(msg)

    def disconnect(self):
        self._should_reconnect = False
        if self.ws:
            self.ws.close()
        self.connected = False

    def send_message(self, to: str, content: str, msg_type: str = "message"):
        self.send({
            "type": msg_type,
            "to": to,
            "payload": {
                "content": content,
                "timestamp": int(time.time() * 1000),
                "encrypted": True,
            }
        })

    def send_typing(self, to: str):
        self.send({"type": "typing", "to": to})

    def send_read_receipt(self, to: str, message_id: str):
        self.send({"type": "read_receipt", "to": to, "messageId": message_id})

    def initiate_call_ws(self, to: str, call_type: str = "audio"):
        self.send({
            "type": "call_initiate",
            "to": to,
            "payload": {"callType": call_type}
        })

    def answer_call_ws(self, to: str, call_id: str):
        self.send({
            "type": "call_answer",
            "to": to,
            "payload": {"callId": call_id}
        })

    def end_call_ws(self, to: str, call_id: str):
        self.send({
            "type": "call_end",
            "to": to,
            "payload": {"callId": call_id}
        })

    def send_ice_candidate(self, to: str, candidate: dict):
        self.send({
            "type": "ice_candidate",
            "to": to,
            "payload": candidate
        })

    def send_sdp_offer(self, to: str, sdp: dict):
        self.send({
            "type": "sdp_offer",
            "to": to,
            "payload": sdp
        })

    def send_sdp_answer(self, to: str, sdp: dict):
        self.send({
            "type": "sdp_answer",
            "to": to,
            "payload": sdp
        })

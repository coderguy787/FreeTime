import os
import json
import base64
from typing import Optional

DATA_DIR = os.path.join(os.path.expanduser("~"), ".freetime")
CONFIG_FILE = os.path.join(DATA_DIR, "config.json")

DEFAULTS = {
    "api_base_url": "https://chat.example.com",
    "websocket_url": "wss://chat.example.com",
    "jwt_token": "",
    "session_token": "",
    "user_data": {},
}

def _ensure_dir():
    os.makedirs(DATA_DIR, exist_ok=True)

def load_config() -> dict:
    _ensure_dir()
    if os.path.exists(CONFIG_FILE):
        with open(CONFIG_FILE, "r") as f:
            data = json.load(f)
            merged = {**DEFAULTS, **data}
            return merged
    return dict(DEFAULTS)

def save_config(config: dict):
    _ensure_dir()
    with open(CONFIG_FILE, "w") as f:
        json.dump(config, f, indent=2)

def get_token() -> str:
    cfg = load_config()
    return cfg.get("jwt_token", "")

def save_token(token: str):
    cfg = load_config()
    cfg["jwt_token"] = token
    save_config(cfg)

def get_session_token() -> str:
    cfg = load_config()
    return cfg.get("session_token", "")

def save_session_token(token: str):
    cfg = load_config()
    cfg["session_token"] = token
    save_config(cfg)

def clear_token():
    cfg = load_config()
    cfg["jwt_token"] = ""
    cfg["session_token"] = ""
    save_config(cfg)

def save_user_data(user_data: dict):
    cfg = load_config()
    cfg["user_data"] = user_data
    save_config(cfg)

def get_user_data() -> dict:
    cfg = load_config()
    return cfg.get("user_data", {})

def save_api_url(url: str):
    cfg = load_config()
    cfg["api_base_url"] = url
    save_config(cfg)

def get_api_url() -> str:
    cfg = load_config()
    return cfg.get("api_base_url", DEFAULTS["api_base_url"])

def save_websocket_url(url: str):
    cfg = load_config()
    cfg["websocket_url"] = url
    save_config(cfg)

def get_websocket_url() -> str:
    cfg = load_config()
    return cfg.get("websocket_url", DEFAULTS["websocket_url"])

import pyotp
import qrcode
import io
import base64
from typing import Tuple

def generate_secret() -> str:
    return pyotp.random_base32()

def generate_qr_code(secret: str, username: str, issuer: str = "FreeTime") -> str:
    # qr code helpers for 2fa
    totp = pyotp.TOTP(secret)
    uri = totp.provisioning_uri(name=username, issuer_name=issuer)
    img = qrcode.make(uri)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode()

def generate_current_code(secret: str) -> str:
    totp = pyotp.TOTP(secret)
    return totp.now()

def verify_code(secret: str, code: str) -> bool:
    totp = pyotp.TOTP(secret)
    return totp.verify(code, valid_window=1)

def generate_backup_codes(count: int = 8) -> list:
    codes = []
    for _ in range(count):
        code = base64.b32encode(__import__("os").urandom(5)).decode()[:8]
        codes.append(f"{code[:4]}-{code[4:]}")
    return codes

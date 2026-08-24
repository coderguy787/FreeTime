from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit,
    QPushButton, QStackedWidget, QMessageBox, QCheckBox
)
from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtGui import QPixmap, QFont

import base64
import os
import re
import sys

def resource_path(relative_path):
    if getattr(sys, 'frozen', False):
        base = getattr(sys, '_MEIPASS', os.path.dirname(sys.executable))
    else:
        base = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    return os.path.join(base, relative_path)

from network.api_service import ApiService
from utils.config import save_token, save_session_token, save_user_data
from utils.totp import generate_secret, generate_qr_code
from ui.workers import ApiWorker

class AuthScreen(QWidget):
    # login and register screen
    auth_success = pyqtSignal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self.api = ApiService()
        self._workers = []
        self._setup_ui()

    def _setup_ui(self):
        self.main_layout = QVBoxLayout(self)
        self.main_layout.setContentsMargins(0, 0, 0, 0)

        self.stack = QStackedWidget()
        self.stack.addWidget(self._create_login_page())
        self.stack.addWidget(self._create_register_page())
        self.stack.addWidget(self._create_totp_setup_page())
        self.stack.addWidget(self._create_totp_verify_page())

        self.main_layout.addWidget(self.stack)

    def _make_logo_label(self, size=120):
        logo_label = QLabel()
        logo_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        icon_path = resource_path("insta_logo.png")
        if os.path.exists(icon_path):
            pixmap = QPixmap(icon_path)
            scaled = pixmap.scaled(size, size, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
            logo_label.setPixmap(scaled)
        else:
            logo_label.setText("FreeTime")
            logo_label.setObjectName("titleLabel")
        return logo_label

    def _create_login_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.setContentsMargins(100, 40, 100, 60)

        layout.addWidget(self._make_logo_label(120))
        layout.addSpacing(12)

        title = QLabel("FreeTime")
        title.setObjectName("titleLabel")
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(title)

        subtitle = QLabel("Secure Encrypted Messaging")
        subtitle.setObjectName("subtitleLabel")
        subtitle.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(subtitle)
        layout.addSpacing(32)

        self.login_username = QLineEdit()
        self.login_username.setPlaceholderText("Username or Email")
        self.login_username.setMinimumHeight(44)
        layout.addWidget(self.login_username)

        layout.addSpacing(8)

        self.login_password = QLineEdit()
        self.login_password.setPlaceholderText("Password")
        self.login_password.setEchoMode(QLineEdit.EchoMode.Password)
        self.login_password.setMinimumHeight(44)
        self.login_password.returnPressed.connect(self._do_login)
        layout.addWidget(self.login_password)

        layout.addSpacing(4)

        self.login_error = QLabel("")
        self.login_error.setObjectName("errorLabel")
        self.login_error.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(self.login_error)

        layout.addSpacing(16)

        self.login_btn = QPushButton("Log In")
        self.login_btn.setMinimumHeight(44)
        self.login_btn.clicked.connect(self._do_login)
        layout.addWidget(self.login_btn)

        layout.addSpacing(12)

        register_btn = QPushButton("Create Account")
        register_btn.setObjectName("secondaryBtn")
        register_btn.setMinimumHeight(44)
        register_btn.clicked.connect(lambda: self.stack.setCurrentIndex(1))
        layout.addWidget(register_btn)

        layout.addStretch()
        return page

    def _create_register_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.setContentsMargins(100, 30, 100, 40)

        layout.addWidget(self._make_logo_label(80))
        layout.addSpacing(8)

        title = QLabel("Create Account")
        title.setObjectName("headingLabel")
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(title)
        layout.addSpacing(16)

        self.reg_username = QLineEdit()
        self.reg_username.setPlaceholderText("Username")
        self.reg_username.setMinimumHeight(40)
        layout.addWidget(self.reg_username)

        layout.addSpacing(6)

        self.reg_display_name = QLineEdit()
        self.reg_display_name.setPlaceholderText("Display Name (optional)")
        self.reg_display_name.setMinimumHeight(40)
        layout.addWidget(self.reg_display_name)

        layout.addSpacing(6)

        self.reg_email = QLineEdit()
        self.reg_email.setPlaceholderText("Email")
        self.reg_email.setMinimumHeight(40)
        layout.addWidget(self.reg_email)

        layout.addSpacing(6)

        self.reg_password = QLineEdit()
        self.reg_password.setPlaceholderText("Password (min 8 chars, letters + numbers + special)")
        self.reg_password.setEchoMode(QLineEdit.EchoMode.Password)
        self.reg_password.setMinimumHeight(40)
        layout.addWidget(self.reg_password)

        layout.addSpacing(6)

        self.reg_confirm_password = QLineEdit()
        self.reg_confirm_password.setPlaceholderText("Confirm Password")
        self.reg_confirm_password.setEchoMode(QLineEdit.EchoMode.Password)
        self.reg_confirm_password.setMinimumHeight(40)
        self.reg_confirm_password.returnPressed.connect(self._do_register)
        layout.addWidget(self.reg_confirm_password)

        layout.addSpacing(8)

        self.reg_terms = QCheckBox("I agree to the Terms & Conditions and Privacy Policy")
        self.reg_terms.setObjectName("termsCheckBox")
        self.reg_terms.stateChanged.connect(self._on_terms_changed)
        layout.addWidget(self.reg_terms)

        terms_link = QLabel('<a href="https://freetime-official.org/terms" style="color: #FF00FF;">Terms & Privacy Policy</a>')
        terms_link.setOpenExternalLinks(True)
        terms_link.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(terms_link)

        layout.addSpacing(4)

        self.reg_error = QLabel("")
        self.reg_error.setObjectName("errorLabel")
        self.reg_error.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(self.reg_error)

        layout.addSpacing(12)

        self.register_btn = QPushButton("Register")
        self.register_btn.setMinimumHeight(44)
        self.register_btn.setEnabled(False)
        self.register_btn.clicked.connect(self._do_register)
        layout.addWidget(self.register_btn)

        layout.addSpacing(12)

        back_btn = QPushButton("Back to Login")
        back_btn.setObjectName("secondaryBtn")
        back_btn.setMinimumHeight(44)
        back_btn.clicked.connect(lambda: self.stack.setCurrentIndex(0))
        layout.addWidget(back_btn)

        layout.addStretch()
        return page

    def _on_terms_changed(self, state):
        self.register_btn.setEnabled(state == Qt.CheckState.Checked.value)

    def _validate_register(self, username, email, password, confirm_password):
        if len(username) < 3:
            return "Username must be at least 3 characters"
        if not re.match(r'^[a-zA-Z0-9_]+$', username):
            return "Username can only contain letters, numbers, and underscores"
        if "@" not in email:
            return "Enter a valid email address"
        if len(password) < 8:
            return "Password must be at least 8 characters"
        if not re.search(r'[A-Za-z]', password):
            return "Password must contain at least one letter"
        if not re.search(r'[0-9]', password):
            return "Password must contain at least one number"
        if not re.search(r'[@$%^&*!]', password):
            return "Password must contain at least one special character (@$%^&*!)"
        if password != confirm_password:
            return "Passwords do not match"
        return None

    def _create_totp_setup_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.setContentsMargins(80, 40, 80, 40)

        title = QLabel("Two-Factor Authentication Setup")
        title.setObjectName("headingLabel")
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(title)

        desc = QLabel("Scan this QR code with your authenticator app\n(Google Authenticator, Authy, etc.)")
        desc.setObjectName("subtitleLabel")
        desc.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(desc)
        layout.addSpacing(16)

        self.qr_label = QLabel()
        self.qr_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.qr_label.setMinimumSize(220, 220)
        self.qr_label.setStyleSheet("background-color: #FFFFFF; border-radius: 12px; padding: 8px;")
        layout.addWidget(self.qr_label, alignment=Qt.AlignmentFlag.AlignCenter)

        layout.addSpacing(12)

        secret_label = QLabel("Manual entry code:")
        secret_label.setObjectName("subtitleLabel")
        secret_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(secret_label)

        self.secret_display = QLineEdit()
        self.secret_display.setReadOnly(True)
        self.secret_display.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.secret_display.setMinimumHeight(40)
        layout.addWidget(self.secret_display)

        layout.addSpacing(8)

        totp_input_label = QLabel("Enter the 6-digit code from your app:")
        totp_input_label.setObjectName("subtitleLabel")
        totp_input_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(totp_input_label)

        self.totp_code_input = QLineEdit()
        self.totp_code_input.setPlaceholderText("000000")
        self.totp_code_input.setMaxLength(6)
        self.totp_code_input.setMinimumHeight(44)
        self.totp_code_input.setAlignment(Qt.AlignmentFlag.AlignCenter)
        font = self.totp_code_input.font()
        font.setPointSize(18)
        self.totp_code_input.setFont(font)
        self.totp_code_input.returnPressed.connect(self._verify_totp_setup)
        layout.addWidget(self.totp_code_input)

        layout.addSpacing(4)

        self.totp_setup_error = QLabel("")
        self.totp_setup_error.setObjectName("errorLabel")
        self.totp_setup_error.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(self.totp_setup_error)

        layout.addSpacing(16)

        self.verify_setup_btn = QPushButton("Verify & Enable 2FA")
        self.verify_setup_btn.setMinimumHeight(44)
        self.verify_setup_btn.clicked.connect(self._verify_totp_setup)
        layout.addWidget(self.verify_setup_btn)

        layout.addStretch()
        return page

    def _create_totp_verify_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.setContentsMargins(100, 50, 100, 80)

        layout.addWidget(self._make_logo_label(80))
        layout.addSpacing(8)

        title = QLabel("Two-Factor Verification")
        title.setObjectName("headingLabel")
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(title)

        desc = QLabel("Enter the 6-digit code from your\nauthenticator app")
        desc.setObjectName("subtitleLabel")
        desc.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(desc)
        layout.addSpacing(24)

        self.verify_code_input = QLineEdit()
        self.verify_code_input.setPlaceholderText("000000")
        self.verify_code_input.setMaxLength(6)
        self.verify_code_input.setMinimumHeight(50)
        self.verify_code_input.setAlignment(Qt.AlignmentFlag.AlignCenter)
        font = self.verify_code_input.font()
        font.setPointSize(22)
        self.verify_code_input.setFont(font)
        self.verify_code_input.returnPressed.connect(self._do_totp_verify)
        layout.addWidget(self.verify_code_input)

        layout.addSpacing(4)

        self.verify_error = QLabel("")
        self.verify_error.setObjectName("errorLabel")
        self.verify_error.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(self.verify_error)

        layout.addSpacing(20)

        self.verify_btn = QPushButton("Verify")
        self.verify_btn.setMinimumHeight(44)
        self.verify_btn.clicked.connect(self._do_totp_verify)
        layout.addWidget(self.verify_btn)

        layout.addStretch()
        return page

    def _set_ui_enabled(self, enabled):
        self.login_username.setEnabled(enabled)
        self.login_password.setEnabled(enabled)
        self.login_btn.setEnabled(enabled)
        self.register_btn.setEnabled(enabled)

    def _do_login(self):
        username = self.login_username.text().strip()
        password = self.login_password.text().strip()
        if not username or not password:
            self.login_error.setText("Please fill in all fields")
            return
        self.login_error.setText("")
        self._set_ui_enabled(False)
        self.login_btn.setText("Logging in...")

        worker = ApiWorker(self.api.login, username, password)
        worker.finished.connect(self._on_login_done)
        self._workers.append(worker)
        worker.start()

    def _on_login_done(self, result):
        ok, data = result
        self._set_ui_enabled(True)
        self.login_btn.setText("Log In")

        if not ok:
            self.login_error.setText(data.get("error", data.get("message", "Login failed")))
            return

        if data.get("requiresTwoFactor", False):
            self.stack.setCurrentIndex(3)
        elif "token" in data:
            save_token(data["token"])
            self.auth_success.emit()
        elif "accessToken" in data:
            save_token(data["accessToken"])
            self.auth_success.emit()
        elif "tempToken" in data:
            save_session_token(data["tempToken"])
            self.stack.setCurrentIndex(3)
        else:
            self.login_error.setText("Unexpected server response")

    def _do_register(self):
        username = self.reg_username.text().strip()
        display_name = self.reg_display_name.text().strip()
        email = self.reg_email.text().strip()
        password = self.reg_password.text().strip()
        confirm_password = self.reg_confirm_password.text().strip()

        if not username or not email or not password or not confirm_password:
            self.reg_error.setText("Please fill in all required fields")
            return

        err = self._validate_register(username, email, password, confirm_password)
        if err:
            self.reg_error.setText(err)
            return

        if not self.reg_terms.isChecked():
            self.reg_error.setText("Accept terms to continue")
            return

        self.reg_error.setText("")
        self.register_btn.setEnabled(False)
        self.register_btn.setText("Registering...")

        worker = ApiWorker(self.api.signup, username, email, display_name, password, confirm_password)
        worker.finished.connect(self._on_register_done)
        self._workers.append(worker)
        worker.start()

    def _on_register_done(self, result):
        ok, data = result
        self.register_btn.setEnabled(self.reg_terms.isChecked())
        self.register_btn.setText("Register")

        if not ok:
            self.reg_error.setText(data.get("error", data.get("message", "Registration failed")))
            return

        if data.get("requiresTwoFactorSetup", False) or data.get("tempToken"):
            if "tempToken" in data:
                save_session_token(data["tempToken"])
            self._show_totp_setup(self.reg_username.text().strip())
        elif "token" in data:
            save_token(data["token"])
            self.auth_success.emit()
        else:
            QMessageBox.information(self, "Success", "Account created! You can now log in.")
            self.stack.setCurrentIndex(0)

    def _show_totp_setup(self, username: str):
        self.totp_secret = generate_secret()
        qr_b64 = generate_qr_code(self.totp_secret, username)
        pixmap = QPixmap()
        pixmap.loadFromData(base64.b64decode(qr_b64))
        scaled = pixmap.scaled(200, 200, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
        self.qr_label.setPixmap(scaled)
        self.secret_display.setText(self.totp_secret)
        self.totp_code_input.clear()
        self.totp_setup_error.setText("")
        self.stack.setCurrentIndex(2)

    def _verify_totp_setup(self):
        code = self.totp_code_input.text().strip()
        if len(code) != 6:
            self.totp_setup_error.setText("Enter a valid 6-digit code")
            return
        self.verify_setup_btn.setEnabled(False)

        worker = ApiWorker(self.api.verify_2fa_setup, self.totp_secret, code)
        worker.finished.connect(self._on_totp_setup_done)
        self._workers.append(worker)
        worker.start()

    def _on_totp_setup_done(self, result):
        ok, data = result
        self.verify_setup_btn.setEnabled(True)
        if ok:
            QMessageBox.information(self, "2FA Enabled", "Two-factor authentication has been enabled!")
            self.stack.setCurrentIndex(0)
        else:
            self.totp_setup_error.setText(data.get("error", "Invalid code. Try again."))

    def _do_totp_verify(self):
        code = self.verify_code_input.text().strip()
        if len(code) != 6:
            self.verify_error.setText("Enter a valid 6-digit code")
            return
        self.verify_error.setText("")
        self.verify_btn.setEnabled(False)
        self.verify_btn.setText("Verifying...")

        worker = ApiWorker(self.api.verify_totp, code)
        worker.finished.connect(self._on_totp_verify_done)
        self._workers.append(worker)
        worker.start()

    def _on_totp_verify_done(self, result):
        ok, data = result
        self.verify_btn.setEnabled(True)
        self.verify_btn.setText("Verify")

        if ok and "token" in data:
            save_token(data["token"])
            self.auth_success.emit()
        elif ok and "accessToken" in data:
            save_token(data["accessToken"])
            self.auth_success.emit()
        else:
            self.verify_error.setText(data.get("error", data.get("message", "Invalid code")))

    def reset(self):
        self.login_username.clear()
        self.login_password.clear()
        self.login_error.setText("")
        self.reg_username.clear()
        self.reg_display_name.clear()
        self.reg_email.clear()
        self.reg_password.clear()
        self.reg_confirm_password.clear()
        self.reg_terms.setChecked(False)
        self.reg_error.setText("")
        self.verify_code_input.clear()
        self.verify_error.setText("")
        self.stack.setCurrentIndex(0)

from PyQt6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit,
    QPushButton, QMessageBox, QFrame, QTabWidget, QWidget
)
from PyQt6.QtCore import Qt

from ui.theme import MAGENTA, LIGHT_GREY, TEXT_GREY
from network.api_service import ApiService
from utils.config import get_api_url, save_api_url, get_websocket_url, save_websocket_url, get_user_data

class SettingsScreen(QDialog):
    # settings dialog
    def __init__(self, parent=None):
        super().__init__(parent)
        self.api = ApiService()
        self.setWindowTitle("Settings")
        self.setMinimumSize(500, 500)
        self._setup_ui()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)

        tabs = QTabWidget()

        tabs.addTab(self._create_profile_tab(), "Profile")
        tabs.addTab(self._create_security_tab(), "Security")
        tabs.addTab(self._create_network_tab(), "Network")
        tabs.addTab(self._create_about_tab(), "About")

        layout.addWidget(tabs)

    def _create_profile_tab(self):
        widget = QWidget()
        layout = QVBoxLayout(widget)
        layout.setContentsMargins(24, 24, 24, 24)

        title = QLabel("Profile")
        title.setObjectName("headingLabel")
        layout.addWidget(title)
        layout.addSpacing(16)

        user = get_user_data()
        info = QLabel(f"Username: {user.get('username', 'N/A')}")
        info.setStyleSheet(f"font-size: 15px; color: {LIGHT_GREY};")
        layout.addWidget(info)

        info2 = QLabel(f"Email: {user.get('email', 'N/A')}")
        info2.setStyleSheet(f"font-size: 15px; color: {LIGHT_GREY};")
        layout.addWidget(info2)

        info3 = QLabel(f"2FA: {'Enabled' if user.get('twoFactorEnabled') else 'Disabled'}")
        info3.setStyleSheet(f"font-size: 15px; color: {LIGHT_GREY};")
        layout.addWidget(info3)

        layout.addSpacing(24)

        edit_btn = QPushButton("Edit Profile")
        edit_btn.setObjectName("secondaryBtn")
        edit_btn.setMinimumHeight(40)
        layout.addWidget(edit_btn)

        layout.addStretch()
        return widget

    def _create_security_tab(self):
        widget = QWidget()
        layout = QVBoxLayout(widget)
        layout.setContentsMargins(24, 24, 24, 24)

        title = QLabel("Security")
        title.setObjectName("headingLabel")
        layout.addWidget(title)
        layout.addSpacing(16)

        layout.addWidget(QLabel("Change Password"))
        layout.addSpacing(8)

        self.old_pass = QLineEdit()
        self.old_pass.setPlaceholderText("Current password")
        self.old_pass.setEchoMode(QLineEdit.EchoMode.Password)
        self.old_pass.setMinimumHeight(40)
        layout.addWidget(self.old_pass)

        layout.addSpacing(8)

        self.new_pass = QLineEdit()
        self.new_pass.setPlaceholderText("New password")
        self.new_pass.setEchoMode(QLineEdit.EchoMode.Password)
        self.new_pass.setMinimumHeight(40)
        layout.addWidget(self.new_pass)

        layout.addSpacing(8)

        self.confirm_pass = QLineEdit()
        self.confirm_pass.setPlaceholderText("Confirm new password")
        self.confirm_pass.setEchoMode(QLineEdit.EchoMode.Password)
        self.confirm_pass.setMinimumHeight(40)
        layout.addWidget(self.confirm_pass)

        layout.addSpacing(16)

        change_btn = QPushButton("Change Password")
        change_btn.setMinimumHeight(40)
        change_btn.clicked.connect(self._change_password)
        layout.addWidget(change_btn)

        layout.addStretch()
        return widget

    def _create_network_tab(self):
        widget = QWidget()
        layout = QVBoxLayout(widget)
        layout.setContentsMargins(24, 24, 24, 24)

        title = QLabel("Network")
        title.setObjectName("headingLabel")
        layout.addWidget(title)
        layout.addSpacing(16)

        layout.addWidget(QLabel("API Server URL"))
        layout.addSpacing(4)

        self.api_url_input = QLineEdit(get_api_url())
        self.api_url_input.setMinimumHeight(40)
        layout.addWidget(self.api_url_input)

        layout.addSpacing(12)

        layout.addWidget(QLabel("WebSocket URL"))
        layout.addSpacing(4)

        self.ws_url_input = QLineEdit(get_websocket_url())
        self.ws_url_input.setMinimumHeight(40)
        layout.addWidget(self.ws_url_input)

        layout.addSpacing(16)

        save_btn = QPushButton("Save Network Settings")
        save_btn.setMinimumHeight(40)
        save_btn.clicked.connect(self._save_network)
        layout.addWidget(save_btn)

        layout.addStretch()
        return widget

    def _create_about_tab(self):
        widget = QWidget()
        layout = QVBoxLayout(widget)
        layout.setContentsMargins(24, 24, 24, 24)
        layout.setAlignment(Qt.AlignmentFlag.AlignCenter)

        title = QLabel("FreeTime")
        title.setObjectName("titleLabel")
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(title)

        version = QLabel("Version 2.0 (Windows)")
        version.setObjectName("subtitleLabel")
        version.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(version)

        layout.addSpacing(20)

        desc = QLabel("Secure encrypted messaging application\nwith end-to-end encryption, voice/video calls,\ngroups, channels, and file sharing.")
        desc.setObjectName("subtitleLabel")
        desc.setAlignment(Qt.AlignmentFlag.AlignCenter)
        desc.setStyleSheet(f"color: {TEXT_GREY};")
        layout.addWidget(desc)

        layout.addStretch()
        return widget

    def _change_password(self):
        old = self.old_pass.text().strip()
        new = self.new_pass.text().strip()
        confirm = self.confirm_pass.text().strip()

        if not old or not new or not confirm:
            QMessageBox.warning(self, "Error", "Please fill in all fields")
            return

        if new != confirm:
            QMessageBox.warning(self, "Error", "New passwords do not match")
            return

        if len(new) < 8:
            QMessageBox.warning(self, "Error", "Password must be at least 8 characters")
            return

        ok, data = self.api.change_password(old, new)
        if ok:
            QMessageBox.information(self, "Success", "Password changed successfully")
            self.old_pass.clear()
            self.new_pass.clear()
            self.confirm_pass.clear()
        else:
            QMessageBox.warning(self, "Error", data.get("error", "Failed to change password"))

    def _save_network(self):
        api_url = self.api_url_input.text().strip()
        ws_url = self.ws_url_input.text().strip()

        if api_url:
            save_api_url(api_url)
        if ws_url:
            save_websocket_url(ws_url)

        QMessageBox.information(self, "Saved", "Network settings saved. Restart the app for changes to take effect.")

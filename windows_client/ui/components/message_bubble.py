from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QFrame
)
from PyQt6.QtCore import Qt
import time
import datetime

from ui.theme import BUBBLE_SENT, BUBBLE_RECEIVED, LIGHT_GREY, TEXT_GREY, MAGENTA
from models.message import Message

class MessageBubble(QWidget):
    # one chat message widget
    def __init__(self, message: Message, parent=None):
        super().__init__(parent)
        self.message = message
        self._setup_ui()

    def _setup_ui(self):
        layout = QHBoxLayout(self)
        layout.setContentsMargins(4, 2, 4, 2)

        is_sent = self.message.is_current_user

        if is_sent:
            layout.addStretch()

        bubble = QFrame()
        if is_sent:
            bubble.setObjectName("chatBubble")
            bubble.setStyleSheet(f"""
                background-color: {BUBBLE_SENT};
                border-radius: 12px;
                padding: 8px;
            """)
        else:
            bubble.setObjectName("chatBubbleReceived")
            bubble.setStyleSheet(f"""
                background-color: {BUBBLE_RECEIVED};
                border-radius: 12px;
                padding: 8px;
            """)

        bubble_layout = QVBoxLayout(bubble)
        bubble_layout.setContentsMargins(12, 8, 12, 8)
        bubble_layout.setSpacing(2)

        if self.message.is_group_message and not is_sent and self.message.sender_username:
            sender_label = QLabel(self.message.sender_username)
            sender_label.setStyleSheet(f"font-size: 11px; font-weight: bold; color: {MAGENTA}; background: transparent;")
            bubble_layout.addWidget(sender_label)

        content = self.message.content
        if self.message.message_type == "file":
            content = f"[File] {self.message.file_name or content}"
        elif self.message.message_type == "photo":
            content = "[Photo]"
        elif self.message.message_type == "video":
            content = "[Video]"

        content_label = QLabel(content)
        content_label.setWordWrap(True)
        content_label.setStyleSheet(f"font-size: 14px; color: {LIGHT_GREY}; background: transparent;")
        content_label.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        bubble_layout.addWidget(content_label)

        ts = self.message.timestamp
        if ts > 1e12:
            ts = ts / 1000
        time_str = datetime.datetime.fromtimestamp(ts).strftime("%H:%M") if ts else ""
        time_label = QLabel(time_str)
        time_label.setStyleSheet(f"font-size: 10px; color: {TEXT_GREY}; background: transparent;")
        if is_sent:
            time_label.setAlignment(Qt.AlignmentFlag.AlignRight)
        bubble_layout.addWidget(time_label)

        layout.addWidget(bubble)

        if not is_sent:
            layout.addStretch()

        self.setMinimumWidth(100)

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit,
    QPushButton, QListWidget, QListWidgetItem, QMessageBox,
    QFrame, QInputDialog
)
from PyQt6.QtCore import Qt, QSize
import time
import datetime

from ui.theme import MAGENTA, LIGHT_GREY, TEXT_GREY, BUBBLE_SENT, BUBBLE_RECEIVED
from ui.components.message_bubble import MessageBubble
from ui.workers import ApiWorker
from network.api_service import ApiService
from models.message import Message
from models.group import Group

class GroupScreen(QWidget):
    # group chat screen
    def __init__(self, parent=None):
        super().__init__(parent)
        self.api = ApiService()
        self.groups = []
        self.current_group = None
        self.messages = []
        self._workers = []
        self._setup_ui()

    def _setup_ui(self):
        layout = QHBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        left = QWidget()
        left.setObjectName("sidebar")
        left.setFixedWidth(300)
        ll = QVBoxLayout(left)
        ll.setContentsMargins(0, 0, 0, 0)

        header = QFrame()
        header.setObjectName("header")
        header.setFixedHeight(60)
        hl = QHBoxLayout(header)
        hl.setContentsMargins(16, 8, 16, 8)
        hl.addWidget(QLabel("Groups"))
        hl.addStretch()
        create_btn = QPushButton("+")
        create_btn.setObjectName("iconBtn")
        create_btn.setFixedSize(36, 36)
        create_btn.clicked.connect(self._create_group)
        hl.addWidget(create_btn)
        ll.addWidget(header)

        self.group_list = QListWidget()
        self.group_list.currentRowChanged.connect(self._on_group_selected)
        ll.addWidget(self.group_list)

        layout.addWidget(left)

        right = QWidget()
        rl = QVBoxLayout(right)
        rl.setContentsMargins(0, 0, 0, 0)

        self.chat_header = QFrame()
        self.chat_header.setObjectName("header")
        self.chat_header.setFixedHeight(60)
        chl = QHBoxLayout(self.chat_header)
        chl.setContentsMargins(16, 8, 16, 8)
        self.group_name_label = QLabel("Select a group")
        self.group_name_label.setObjectName("peerNameLabel")
        chl.addWidget(self.group_name_label)
        chl.addStretch()
        self.leave_btn = QPushButton("Leave")
        self.leave_btn.setObjectName("dangerBtn")
        self.leave_btn.setFixedSize(80, 32)
        self.leave_btn.clicked.connect(self._leave_group)
        self.leave_btn.hide()
        chl.addWidget(self.leave_btn)
        rl.addWidget(self.chat_header)

        self.message_area = QListWidget()
        self.message_area.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        rl.addWidget(self.message_area)

        input_frame = QFrame()
        input_frame.setObjectName("inputBar")
        il = QHBoxLayout(input_frame)
        il.setContentsMargins(12, 8, 12, 8)
        self.msg_input = QLineEdit()
        self.msg_input.setPlaceholderText("Message group...")
        self.msg_input.setMinimumHeight(40)
        self.msg_input.returnPressed.connect(self._send_message)
        il.addWidget(self.msg_input)
        send_btn = QPushButton("Send")
        send_btn.setMinimumHeight(40)
        send_btn.clicked.connect(self._send_message)
        il.addWidget(send_btn)
        rl.addWidget(input_frame)

        layout.addWidget(right)

    def load_groups(self):
        worker = ApiWorker(self.api.get_groups)
        worker.finished.connect(self._on_groups_loaded)
        self._workers.append(worker)
        worker.start()

    def _on_groups_loaded(self, result):
        ok, data = result
        if ok:
            groups_raw = data if isinstance(data, list) else data.get("groups", [])
            self.groups = [Group.from_dict(g) for g in groups_raw]
            self._update_group_list()

    def _update_group_list(self):
        self.group_list.clear()
        for g in self.groups:
            item = QListWidgetItem(g.group_name)
            item.setData(Qt.ItemDataRole.UserRole, g)
            self.group_list.addItem(item)

    def _on_group_selected(self, row):
        if row < 0:
            return
        item = self.group_list.item(row)
        if item:
            self.current_group = item.data(Qt.ItemDataRole.UserRole)
            self.group_name_label.setText(self.current_group.group_name)
            self.leave_btn.show()
            self._load_messages()

    def _load_messages(self):
        if not self.current_group:
            return
        group_id = self.current_group.group_id
        worker = ApiWorker(self.api.get_messages, group_id=group_id)
        worker.finished.connect(self._on_messages_loaded)
        self._workers.append(worker)
        worker.start()

    def _on_messages_loaded(self, result):
        ok, data = result
        if ok:
            messages_raw = data if isinstance(data, list) else data.get("messages", [])
            from utils.config import get_user_data
            uid = get_user_data().get("userId", "")
            self.messages = [Message.from_dict(m, uid) for m in messages_raw]
            self._render_messages()

    def _render_messages(self):
        self.message_area.clear()
        for msg in self.messages:
            widget = MessageBubble(msg)
            item = QListWidgetItem()
            item.setSizeHint(widget.sizeHint())
            self.message_area.addItem(item)
            self.message_area.setItemWidget(item, widget)
        self.message_area.scrollToBottom()

    def _send_message(self):
        text = self.msg_input.text().strip()
        if not text or not self.current_group:
            return
        self.msg_input.clear()
        group_id = self.current_group.group_id

        worker = ApiWorker(self.api.send_group_message, group_id, text)
        worker.finished.connect(self._on_message_sent)
        self._workers.append(worker)
        worker.start()

    def _on_message_sent(self, result):
        ok, _ = result
        if ok:
            self._load_messages()

    def _create_group(self):
        name, ok1 = QInputDialog.getText(self, "Create Group", "Group name:")
        if ok1 and name.strip():
            members, ok2 = QInputDialog.getText(self, "Add Members", "Member usernames (comma-separated):")
            if ok2:
                member_list = [m.strip() for m in members.split(",") if m.strip()]
                worker = ApiWorker(self.api.create_group, name.strip(), member_list)
                worker.finished.connect(lambda result, n=name.strip(): self._on_group_created(result, n))
                self._workers.append(worker)
                worker.start()

    def _on_group_created(self, result, name):
        ok, data = result
        if ok:
            self.load_groups()
            QMessageBox.information(self, "Created", f"Group '{name}' created!")
        else:
            QMessageBox.warning(self, "Error", data.get("error", "Failed to create group"))

    def _leave_group(self):
        if not self.current_group:
            return
        reply = QMessageBox.question(
            self, "Leave Group?",
            f"Leave '{self.current_group.group_name}'?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )
        if reply == QMessageBox.StandardButton.Yes:
            group_id = self.current_group.group_id
            worker = ApiWorker(self.api.leave_group, group_id)
            worker.finished.connect(lambda _: self.load_groups())
            self._workers.append(worker)
            worker.start()

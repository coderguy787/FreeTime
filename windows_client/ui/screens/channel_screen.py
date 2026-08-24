from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit,
    QPushButton, QListWidget, QListWidgetItem, QMessageBox,
    QFrame, QInputDialog
)
from PyQt6.QtCore import Qt
import time

from ui.theme import MAGENTA, LIGHT_GREY, TEXT_GREY
from ui.components.message_bubble import MessageBubble
from ui.workers import ApiWorker
from network.api_service import ApiService
from models.message import Message
from models.channel import Channel

class ChannelScreen(QWidget):
    # channel list and chat
    def __init__(self, parent=None):
        super().__init__(parent)
        self.api = ApiService()
        self.channels = []
        self.current_channel = None
        self.is_admin = False
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
        hl.addWidget(QLabel("Channels"))
        hl.addStretch()
        create_btn = QPushButton("+")
        create_btn.setObjectName("iconBtn")
        create_btn.setFixedSize(36, 36)
        create_btn.clicked.connect(self._create_channel)
        hl.addWidget(create_btn)
        ll.addWidget(header)

        self.channel_list = QListWidget()
        self.channel_list.currentRowChanged.connect(self._on_channel_selected)
        ll.addWidget(self.channel_list)

        layout.addWidget(left)

        right = QWidget()
        rl = QVBoxLayout(right)
        rl.setContentsMargins(0, 0, 0, 0)

        self.chat_header = QFrame()
        self.chat_header.setObjectName("header")
        self.chat_header.setFixedHeight(60)
        chl = QHBoxLayout(self.chat_header)
        chl.setContentsMargins(16, 8, 16, 8)
        self.channel_name_label = QLabel("Select a channel")
        self.channel_name_label.setObjectName("peerNameLabel")
        chl.addWidget(self.channel_name_label)
        chl.addStretch()
        self.subscribe_btn = QPushButton("Subscribe")
        self.subscribe_btn.setMinimumHeight(32)
        self.subscribe_btn.clicked.connect(self._toggle_subscribe)
        self.subscribe_btn.hide()
        chl.addWidget(self.subscribe_btn)
        rl.addWidget(self.chat_header)

        self.message_area = QListWidget()
        self.message_area.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        rl.addWidget(self.message_area)

        self.input_bar = QFrame()
        self.input_bar.setObjectName("inputBar")
        il = QHBoxLayout(self.input_bar)
        il.setContentsMargins(12, 8, 12, 8)
        self.msg_input = QLineEdit()
        self.msg_input.setPlaceholderText("Post announcement...")
        self.msg_input.setMinimumHeight(40)
        self.msg_input.returnPressed.connect(self._post_announcement)
        il.addWidget(self.msg_input)
        self.send_btn = QPushButton("Post")
        self.send_btn.setMinimumHeight(40)
        self.send_btn.clicked.connect(self._post_announcement)
        il.addWidget(self.send_btn)
        rl.addWidget(self.input_bar)

        layout.addWidget(right)

    def load_channels(self):
        worker = ApiWorker(self.api.get_channels)
        worker.finished.connect(self._on_channels_loaded)
        self._workers.append(worker)
        worker.start()

    def _on_channels_loaded(self, result):
        ok, data = result
        if ok:
            channels_raw = data if isinstance(data, list) else data.get("channels", [])
            self.channels = [Channel.from_dict(c) for c in channels_raw]
            self._update_channel_list()

    def _update_channel_list(self):
        self.channel_list.clear()
        for c in self.channels:
            item = QListWidgetItem(f"# {c.channel_name}")
            item.setData(Qt.ItemDataRole.UserRole, c)
            self.channel_list.addItem(item)

    def _on_channel_selected(self, row):
        if row < 0:
            return
        item = self.channel_list.item(row)
        if item:
            self.current_channel = item.data(Qt.ItemDataRole.UserRole)
            self.channel_name_label.setText(f"# {self.current_channel.channel_name}")
            self.subscribe_btn.show()
            from utils.config import get_user_data
            uid = get_user_data().get("userId", "")
            self.is_admin = self.current_channel.created_by == uid
            self._update_input_state()
            self._load_messages()

    def _update_input_state(self):
        if self.is_admin:
            self.input_bar.show()
            self.msg_input.setEnabled(True)
            self.send_btn.setEnabled(True)
        else:
            self.input_bar.show()
            self.msg_input.setEnabled(False)
            self.msg_input.setPlaceholderText("Only administrators can post")
            self.send_btn.setEnabled(False)

    def _load_messages(self):
        if not self.current_channel:
            return
        channel_id = self.current_channel.channel_id
        worker = ApiWorker(self.api.get_messages, channel_id=channel_id)
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

    def _post_announcement(self):
        text = self.msg_input.text().strip()
        if not text or not self.current_channel:
            return
        self.msg_input.clear()
        channel_id = self.current_channel.channel_id

        worker = ApiWorker(self.api.post_channel_announcement, channel_id, text)
        worker.finished.connect(self._on_announcement_posted)
        self._workers.append(worker)
        worker.start()

    def _on_announcement_posted(self, result):
        ok, data = result
        if ok:
            self._load_messages()
        else:
            QMessageBox.warning(self, "Error", data.get("error", "Failed to post"))

    def _toggle_subscribe(self):
        if not self.current_channel:
            return
        channel_id = self.current_channel.channel_id
        worker = ApiWorker(self.api.subscribe_channel, channel_id)
        worker.finished.connect(lambda _: self.load_channels())
        self._workers.append(worker)
        worker.start()

    def _create_channel(self):
        name, ok1 = QInputDialog.getText(self, "Create Channel", "Channel name:")
        if ok1 and name.strip():
            desc, ok2 = QInputDialog.getText(self, "Description", "Channel description:")
            if ok2:
                worker = ApiWorker(self.api.create_channel, name.strip(), desc.strip() if ok2 else "")
                worker.finished.connect(lambda result, n=name.strip(): self._on_channel_created(result, n))
                self._workers.append(worker)
                worker.start()

    def _on_channel_created(self, result, name):
        ok, data = result
        if ok:
            self.load_channels()
            QMessageBox.information(self, "Created", f"Channel '#{name}' created!")
        else:
            QMessageBox.warning(self, "Error", data.get("error", "Failed to create channel"))

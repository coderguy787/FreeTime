from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit,
    QPushButton, QListWidget, QListWidgetItem, QStackedWidget,
    QFrame, QScrollArea, QMessageBox, QFileDialog, QMenu,
    QInputDialog
)
from PyQt6.QtCore import Qt, pyqtSignal, QTimer, QSize
from PyQt6.QtGui import QFont, QColor, QAction
import time
import os

from ui.theme import MAGENTA, LIGHT_GREY, BLACK, DARK_GREY, BUBBLE_SENT, BUBBLE_RECEIVED, TEXT_GREY, HOVER_BG
from ui.components.message_bubble import MessageBubble
from ui.workers import ApiWorker
from network.api_service import ApiService
from network.websocket_manager import WebSocketManager
from models.message import Message
from models.peer import Peer

class ChatScreen(QWidget):
    # main chat screen (peer list + conversation)
    logout_signal = pyqtSignal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self.api = ApiService()
        self.ws = WebSocketManager()
        self.peers = []
        self.current_peer = None
        self.messages = []
        self._workers = []
        self._setup_ui()
        self._connect_websocket()
        self._start_refresh_timer()

    def _setup_ui(self):
        main_layout = QHBoxLayout(self)
        main_layout.setContentsMargins(0, 0, 0, 0)
        main_layout.setSpacing(0)

        left_panel = QWidget()
        left_panel.setObjectName("sidebar")
        left_panel.setFixedWidth(300)
        left_layout = QVBoxLayout(left_panel)
        left_layout.setContentsMargins(0, 0, 0, 0)
        left_layout.setSpacing(0)

        left_header = QFrame()
        left_header.setObjectName("header")
        left_header.setFixedHeight(60)
        lh_layout = QHBoxLayout(left_header)
        lh_layout.setContentsMargins(16, 8, 16, 8)

        logo = QLabel("FreeTime")
        logo.setObjectName("titleLabel")
        logo.setStyleSheet(f"font-size: 18px; font-weight: bold; color: {MAGENTA};")
        lh_layout.addWidget(logo)
        lh_layout.addStretch()

        menu_btn = QPushButton("...")
        menu_btn.setObjectName("iconBtn")
        menu_btn.setFixedSize(36, 36)
        menu_btn.clicked.connect(self._show_menu)
        lh_layout.addWidget(menu_btn)

        left_layout.addWidget(left_header)

        search_layout = QHBoxLayout()
        search_layout.setContentsMargins(12, 8, 12, 8)
        self.search_input = QLineEdit()
        self.search_input.setPlaceholderText("Search peers...")
        self.search_input.setMinimumHeight(36)
        self.search_input.textChanged.connect(self._filter_peers)
        search_layout.addWidget(self.search_input)
        left_layout.addLayout(search_layout)

        tabs_layout = QHBoxLayout()
        tabs_layout.setContentsMargins(12, 0, 12, 0)
        self.peers_tab = QPushButton("Chats")
        self.peers_tab.setMinimumHeight(32)
        self.peers_tab.clicked.connect(lambda: self._switch_tab("peers"))
        self.groups_tab = QPushButton("Groups")
        self.groups_tab.setObjectName("secondaryBtn")
        self.groups_tab.setMinimumHeight(32)
        self.groups_tab.clicked.connect(lambda: self._switch_tab("groups"))
        self.channels_tab = QPushButton("Channels")
        self.channels_tab.setObjectName("secondaryBtn")
        self.channels_tab.setMinimumHeight(32)
        self.channels_tab.clicked.connect(lambda: self._switch_tab("channels"))
        tabs_layout.addWidget(self.peers_tab)
        tabs_layout.addWidget(self.groups_tab)
        tabs_layout.addWidget(self.channels_tab)
        left_layout.addLayout(tabs_layout)

        self.peer_list = QListWidget()
        self.peer_list.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.peer_list.currentRowChanged.connect(self._on_peer_selected)
        left_layout.addWidget(self.peer_list)

        add_btn = QPushButton("+ New Chat")
        add_btn.setMinimumHeight(40)
        add_btn.clicked.connect(self._new_chat)
        left_layout.addWidget(add_btn)

        main_layout.addWidget(left_panel)

        right_panel = QWidget()
        right_layout = QVBoxLayout(right_panel)
        right_layout.setContentsMargins(0, 0, 0, 0)
        right_layout.setSpacing(0)

        self.chat_header = QFrame()
        self.chat_header.setObjectName("header")
        self.chat_header.setFixedHeight(60)
        ch_layout = QHBoxLayout(self.chat_header)
        ch_layout.setContentsMargins(16, 8, 16, 8)

        self.peer_name_label = QLabel("Select a chat")
        self.peer_name_label.setObjectName("peerNameLabel")
        ch_layout.addWidget(self.peer_name_label)

        self.peer_status_label = QLabel("")
        self.peer_status_label.setObjectName("peerStatusLabel")
        ch_layout.addWidget(self.peer_status_label)

        ch_layout.addStretch()

        self.call_btn = QPushButton("Call")
        self.call_btn.setObjectName("iconBtn")
        self.call_btn.setFixedSize(36, 36)
        self.call_btn.clicked.connect(self._start_call)
        self.call_btn.hide()
        ch_layout.addWidget(self.call_btn)

        self.video_call_btn = QPushButton("Video")
        self.video_call_btn.setObjectName("iconBtn")
        self.video_call_btn.setFixedSize(36, 36)
        self.video_call_btn.clicked.connect(lambda: self._start_call("video"))
        self.video_call_btn.hide()
        ch_layout.addWidget(self.video_call_btn)

        self.file_btn = QPushButton("File")
        self.file_btn.setObjectName("iconBtn")
        self.file_btn.setFixedSize(36, 36)
        self.file_btn.clicked.connect(self._send_file)
        self.file_btn.hide()
        ch_layout.addWidget(self.file_btn)

        self.more_btn = QPushButton("...")
        self.more_btn.setObjectName("iconBtn")
        self.more_btn.setFixedSize(36, 36)
        self.more_btn.clicked.connect(self._chat_options)
        self.more_btn.hide()
        ch_layout.addWidget(self.more_btn)

        right_layout.addWidget(self.chat_header)

        self.chat_stack = QStackedWidget()

        placeholder = QWidget()
        pl = QVBoxLayout(placeholder)
        pl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        ph_label = QLabel("Select a conversation to start messaging")
        ph_label.setObjectName("subtitleLabel")
        ph_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        pl.addWidget(ph_label)
        self.chat_stack.addWidget(placeholder)

        chat_area = QWidget()
        chat_layout = QVBoxLayout(chat_area)
        chat_layout.setContentsMargins(0, 0, 0, 0)
        self.message_area = QScrollArea()
        self.message_area.setWidgetResizable(True)
        self.message_container = QWidget()
        self.message_container_layout = QVBoxLayout(self.message_container)
        self.message_container_layout.setContentsMargins(16, 8, 16, 8)
        self.message_container_layout.setSpacing(4)
        self.message_container_layout.addStretch()
        self.message_area.setWidget(self.message_container)
        chat_layout.addWidget(self.message_area)

        input_bar = QFrame()
        input_bar.setObjectName("inputBar")
        ib_layout = QHBoxLayout(input_bar)
        ib_layout.setContentsMargins(12, 8, 12, 8)

        self.msg_input = QLineEdit()
        self.msg_input.setPlaceholderText("Type a message...")
        self.msg_input.setMinimumHeight(40)
        self.msg_input.returnPressed.connect(self._send_message)
        ib_layout.addWidget(self.msg_input)

        send_btn = QPushButton("Send")
        send_btn.setMinimumHeight(40)
        send_btn.setMinimumWidth(80)
        send_btn.clicked.connect(self._send_message)
        ib_layout.addWidget(send_btn)

        chat_layout.addWidget(input_bar)
        self.chat_stack.addWidget(chat_area)

        right_layout.addWidget(self.chat_stack)
        main_layout.addWidget(right_panel)

    def _connect_websocket(self):
        self.ws.on("message", self._on_ws_message)
        self.ws.on("connected", lambda: print("WebSocket connected"))
        self.ws.on("disconnected", lambda: print("WebSocket disconnected"))
        self.ws.on("typing", self._on_typing)
        self.ws.connect()

    def _start_refresh_timer(self):
        self.refresh_timer = QTimer(self)
        self.refresh_timer.timeout.connect(self.load_peers)
        self.refresh_timer.start(15000)
        self.load_peers()

    def load_peers(self):
        worker = ApiWorker(self.api.get_peers)
        worker.finished.connect(self._on_peers_loaded)
        self._workers.append(worker)
        worker.start()

    def _on_peers_loaded(self, result):
        ok, data = result
        if ok and isinstance(data, list):
            self.peers = [Peer.from_dict(p) for p in data]
        elif ok and isinstance(data, dict):
            peers_list = data.get("peers", data.get("friends", []))
            self.peers = [Peer.from_dict(p) for p in peers_list]
        self._update_peer_list()

    def _update_peer_list(self, filter_text: str = ""):
        self.peer_list.clear()
        for peer in self.peers:
            if filter_text and filter_text.lower() not in peer.name.lower():
                continue
            item = QListWidgetItem()
            item.setSizeHint(QSize(0, 60))
            widget = QWidget()
            wl = QHBoxLayout(widget)
            wl.setContentsMargins(8, 4, 8, 4)

            indicator = QLabel()
            indicator.setObjectName("onlineIndicator" if peer.status == "online" else "offlineIndicator")
            indicator.setFixedSize(10, 10)
            wl.addWidget(indicator, alignment=Qt.AlignmentFlag.AlignCenter)

            text_layout = QVBoxLayout()
            name = QLabel(peer.name)
            name.setObjectName("peerNameLabel")
            text_layout.addWidget(name)
            status = QLabel(peer.status.capitalize())
            status.setObjectName("peerStatusLabel")
            text_layout.addWidget(status)
            wl.addLayout(text_layout)
            wl.addStretch()

            item.setSizeHint(QSize(0, 64))
            item.setData(Qt.ItemDataRole.UserRole, peer)
            self.peer_list.addItem(item)
            self.peer_list.setItemWidget(item, widget)

    def _filter_peers(self, text):
        self._update_peer_list(text)

    def _on_peer_selected(self, row):
        if row < 0:
            return
        item = self.peer_list.item(row)
        if not item:
            return
        peer = item.data(Qt.ItemDataRole.UserRole)
        if peer:
            self.current_peer = peer
            self.peer_name_label.setText(peer.name)
            self.peer_status_label.setText(peer.status.capitalize())
            self.call_btn.show()
            self.video_call_btn.show()
            self.file_btn.show()
            self.more_btn.show()
            self.chat_stack.setCurrentIndex(1)
            self._load_messages()

    def _load_messages(self):
        if not self.current_peer:
            return
        peer = self.current_peer
        worker = ApiWorker(self.api.get_messages, peer_id=peer.peer_id)
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
        while self.message_container_layout.count() > 1:
            child = self.message_container_layout.takeAt(0)
            if child.widget():
                child.widget().deleteLater()

        for msg in self.messages:
            bubble = MessageBubble(msg)
            self.message_container_layout.insertWidget(self.message_container_layout.count() - 1, bubble)

        sb = self.message_area.verticalScrollBar()
        sb.setValue(sb.maximum())

    def _send_message(self):
        text = self.msg_input.text().strip()
        if not text or not self.current_peer:
            return
        self.msg_input.clear()
        peer_id = self.current_peer.peer_id

        from utils.config import get_user_data
        uid = get_user_data().get("userId", "")
        msg = Message(
            message_id=f"temp_{int(time.time()*1000)}",
            sender_id=uid,
            recipient_id=peer_id,
            content=text,
            timestamp=int(time.time() * 1000),
            is_current_user=True,
        )
        self.messages.append(msg)
        self._render_messages()
        self.ws.send_message(peer_id, text)

        worker = ApiWorker(self.api.send_message, peer_id, text)
        worker.finished.connect(self._on_message_sent)
        self._workers.append(worker)
        worker.start()

    def _on_message_sent(self, result):
        pass

    def _send_file(self):
        if not self.current_peer:
            return
        path, _ = QFileDialog.getOpenFileName(self, "Select File")
        if path:
            peer_id = self.current_peer.peer_id
            file_name = os.path.basename(path)

            worker = ApiWorker(self.api.upload_media, path)
            worker.finished.connect(lambda result, pid=peer_id, fn=file_name: self._on_file_uploaded(result, pid, fn))
            self._workers.append(worker)
            worker.start()

    def _on_file_uploaded(self, result, peer_id, file_name):
        ok, data = result
        if ok:
            worker = ApiWorker(self.api.send_message, peer_id, f"[File: {file_name}]", message_type="file")
            worker.finished.connect(lambda _: self._load_messages())
            self._workers.append(worker)
            worker.start()
        else:
            QMessageBox.warning(self, "Error", "Failed to upload file")

    def _start_call(self, call_type="audio"):
        if not self.current_peer:
            return
        peer_id = self.current_peer.peer_id
        peer_name = self.current_peer.name

        worker = ApiWorker(self.api.initiate_call, peer_id, call_type)
        worker.finished.connect(lambda result, ct=call_type, pn=peer_name: self._on_call_initiated(result, ct, pn))
        self._workers.append(worker)
        worker.start()

    def _on_call_initiated(self, result, call_type, peer_name):
        ok, data = result
        if ok:
            self.ws.initiate_call_ws(self.current_peer.peer_id, call_type)
            QMessageBox.information(self, "Calling", f"Initiating {call_type} call to {peer_name}...")

    def _chat_options(self):
        if not self.current_peer:
            return
        menu = QMenu(self)
        delete_history = menu.addAction("Delete History")
        delete_history.triggered.connect(self._delete_history)
        menu.exec(self.more_btn.mapToGlobal(self.more_btn.rect().bottomLeft()))

    def _delete_history(self):
        if not self.current_peer:
            return
        reply = QMessageBox.question(
            self, "Delete History?",
            f"Delete all messages and calls with @{self.current_peer.name}?\n"
            "This action cannot be undone and will delete the history from both sides.",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            QMessageBox.StandardButton.No
        )
        if reply == QMessageBox.StandardButton.Yes:
            peer_id = self.current_peer.peer_id
            worker = ApiWorker(self.api.delete_history_with_user, peer_id, "")
            worker.finished.connect(self._on_history_deleted)
            self._workers.append(worker)
            worker.start()

    def _on_history_deleted(self, result):
        ok, _ = result
        if ok:
            self.messages.clear()
            self._render_messages()
            QMessageBox.information(self, "Done", "History deleted")
        else:
            QMessageBox.warning(self, "Error", "Failed to delete history")

    def _new_chat(self):
        username, ok_pressed = QInputDialog.getText(self, "New Chat", "Enter username:")
        if ok_pressed and username.strip():
            worker = ApiWorker(self.api.send_friend_request, username.strip())
            worker.finished.connect(lambda _: self._on_friend_request_sent(username.strip()))
            self._workers.append(worker)
            worker.start()

    def _on_friend_request_sent(self, username):
        QMessageBox.information(self, "Sent", f"Friend request sent to @{username}")
        self.load_peers()

    def _show_menu(self):
        menu = QMenu(self)
        profile_action = menu.addAction("Profile")
        profile_action.triggered.connect(self._show_profile)
        settings_action = menu.addAction("Settings")
        settings_action.triggered.connect(self._show_settings)
        menu.addSeparator()
        logout_action = menu.addAction("Logout")
        logout_action.triggered.connect(self._logout)
        menu.exec(self.sender().mapToGlobal(self.sender().rect().bottomLeft()))

    def _show_profile(self):
        worker = ApiWorker(self.api.get_profile)
        worker.finished.connect(self._on_profile_loaded)
        self._workers.append(worker)
        worker.start()

    def _on_profile_loaded(self, result):
        ok, data = result
        if ok:
            user = data if isinstance(data, dict) else {}
            msg = f"Username: {user.get('username', 'N/A')}\nEmail: {user.get('email', 'N/A')}\n2FA: {'Enabled' if user.get('twoFactorEnabled') else 'Disabled'}"
            QMessageBox.information(self, "Profile", msg)

    def _show_settings(self):
        from ui.screens.settings_screen import SettingsScreen
        self.settings_dialog = SettingsScreen(self)
        self.settings_dialog.exec()

    def _logout(self):
        self.ws.disconnect()
        self.logout_signal.emit()

    def _on_ws_message(self, data):
        msg_type = data.get("type", "")
        if msg_type == "message":
            sender = data.get("from", "")
            if self.current_peer and sender == self.current_peer.peer_id:
                content = data.get("payload", {}).get("content", "")
                from utils.config import get_user_data
                uid = get_user_data().get("userId", "")
                msg = Message(
                    message_id=data.get("messageId", f"ws_{int(time.time()*1000)}"),
                    sender_id=sender,
                    recipient_id=uid,
                    content=content,
                    timestamp=data.get("payload", {}).get("timestamp", int(time.time()*1000)),
                    is_current_user=False,
                )
                self.messages.append(msg)
                self._render_messages()

    def _on_typing(self, data):
        if self.current_peer and data.get("from") == self.current_peer.peer_id:
            self.peer_status_label.setText("Typing...")

    def _switch_tab(self, tab):
        if tab == "peers":
            self.peers_tab.setStyleSheet(f"font-weight: bold; color: {MAGENTA}; border-bottom: 2px solid {MAGENTA};")
            self.groups_tab.setStyleSheet("")
            self.channels_tab.setStyleSheet("")
        elif tab == "groups":
            self.peers_tab.setStyleSheet("")
            self.groups_tab.setStyleSheet(f"font-weight: bold; color: {MAGENTA}; border-bottom: 2px solid {MAGENTA};")
            self.channels_tab.setStyleSheet("")
        elif tab == "channels":
            self.peers_tab.setStyleSheet("")
            self.groups_tab.setStyleSheet("")
            self.channels_tab.setStyleSheet(f"font-weight: bold; color: {MAGENTA}; border-bottom: 2px solid {MAGENTA};")

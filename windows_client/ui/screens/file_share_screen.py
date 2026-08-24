from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QLineEdit, QFileDialog, QProgressBar, QListWidget, QListWidgetItem,
    QMessageBox, QFrame
)
from PyQt6.QtCore import Qt, QThread, pyqtSignal
import os

from ui.theme import MAGENTA, LIGHT_GREY, TEXT_GREY
from network.api_service import ApiService

class UploadWorker(QThread):
    # file upload/download screen
    finished = pyqtSignal(bool, str, str)

    def __init__(self, api, file_path, message_id=""):
        super().__init__()
        self.api = api
        self.file_path = file_path
        self.message_id = message_id

    def run(self):
        ok, data = self.api.upload_media(self.file_path, self.message_id)
        self.finished.emit(ok, str(data), self.file_path)

class FileShareScreen(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.api = ApiService()
        self.uploads = []
        self._setup_ui()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 24, 24, 24)

        title = QLabel("File Sharing")
        title.setObjectName("headingLabel")
        layout.addWidget(title)

        layout.addSpacing(16)

        btn_row = QHBoxLayout()
        upload_btn = QPushButton("Upload File")
        upload_btn.setMinimumHeight(40)
        upload_btn.clicked.connect(self._upload_file)
        btn_row.addWidget(upload_btn)
        btn_row.addStretch()
        layout.addLayout(btn_row)

        layout.addSpacing(16)

        self.progress_bar = QProgressBar()
        self.progress_bar.hide()
        layout.addWidget(self.progress_bar)

        self.file_list = QListWidget()
        layout.addWidget(self.file_list)

    def _upload_file(self):
        path, _ = QFileDialog.getOpenFileName(self, "Select File to Upload")
        if not path:
            return
        self.progress_bar.show()
        self.progress_bar.setValue(0)

        self.worker = UploadWorker(self.api, path)
        self.worker.finished.connect(self._on_upload_done)
        self.worker.start()

    def _on_upload_done(self, ok, data, file_path):
        self.progress_bar.hide()
        if ok:
            name = os.path.basename(file_path)
            item = QListWidgetItem(f"Sent: {name}")
            self.file_list.insertItem(0, item)
            QMessageBox.information(self, "Success", f"File '{name}' uploaded successfully")
        else:
            QMessageBox.warning(self, "Error", "Upload failed")

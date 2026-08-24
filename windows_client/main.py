import sys
import os

if getattr(sys, 'frozen', False):
    _base = os.path.dirname(sys.executable)
else:
    _base = os.path.dirname(os.path.abspath(__file__))

if _base not in sys.path:
    sys.path.insert(0, _base)

def resource_path(relative_path):
    # resource paths work both in dev and frozen with pyinstaller
    if getattr(sys, 'frozen', False):
        base = getattr(sys, '_MEIPASS', os.path.dirname(sys.executable))
    else:
        base = os.path.dirname(os.path.abspath(__file__))
    return os.path.join(base, relative_path)

from PyQt6.QtWidgets import QApplication, QMainWindow, QStackedWidget
from PyQt6.QtCore import Qt
from PyQt6.QtGui import QIcon
from ui.theme import apply_theme
from ui.screens.auth_screen import AuthScreen
from ui.screens.chat_screen import ChatScreen
from ui.screens.group_screen import GroupScreen
from ui.screens.channel_screen import ChannelScreen
from utils.config import get_token

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("FreeTime - Secure Chat")
        self.setMinimumSize(1000, 700)
        self.resize(1200, 800)

        icon_path = resource_path("insta_logo.png")
        if os.path.exists(icon_path):
            self.setWindowIcon(QIcon(icon_path))

        self.stack = QStackedWidget()
        self.setCentralWidget(self.stack)

        self.auth_screen = AuthScreen()
        self.auth_screen.auth_success.connect(self._on_auth_success)
        self.stack.addWidget(self.auth_screen)

        self.chat_screen = ChatScreen()
        self.chat_screen.logout_signal.connect(self._on_logout)
        self.stack.addWidget(self.chat_screen)

        self.group_screen = GroupScreen()
        self.stack.addWidget(self.group_screen)

        self.channel_screen = ChannelScreen()
        self.stack.addWidget(self.channel_screen)

        if get_token():
            self.stack.setCurrentWidget(self.chat_screen)
            self._load_main_data()
        else:
            self.stack.setCurrentWidget(self.auth_screen)

    def _on_auth_success(self):
        self.stack.setCurrentWidget(self.chat_screen)
        self._load_main_data()

    def _load_main_data(self):
        self.chat_screen.load_peers()
        self.group_screen.load_groups()
        self.channel_screen.load_channels()

    def _on_logout(self):
        self.auth_screen.reset()
        self.stack.setCurrentWidget(self.auth_screen)

    def closeEvent(self, event):
        if hasattr(self.chat_screen, 'ws'):
            self.chat_screen.ws.disconnect()
        event.accept()

def main():
    os.environ["QT_AUTO_SCREEN_SCALE_FACTOR"] = "1"
    app = QApplication(sys.argv)
    app.setApplicationName("FreeTime")
    app.setOrganizationName("FreeTime")

    icon_path = resource_path("insta_logo.png")
    if os.path.exists(icon_path):
        app.setWindowIcon(QIcon(icon_path))

    apply_theme(app)
    window = MainWindow()
    window.show()
    sys.exit(app.exec())

if __name__ == "__main__":
    main()

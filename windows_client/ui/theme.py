from PyQt6.QtWidgets import QApplication
from PyQt6.QtGui import QPalette, QColor, QFont
from PyQt6.QtCore import Qt

BLACK = "#000000"
MAGENTA = "#FF00FF"
LIGHT_GREY = "#F0F0F0"
DARK_GREY = "#1A1A1A"
MID_GREY = "#2A2A2A"
TEXT_WHITE = "#FFFFFF"
TEXT_GREY = "#999999"
ERROR_RED = "#FF4444"
SUCCESS_GREEN = "#44FF44"
BUBBLE_SENT = "#3D0047"
BUBBLE_RECEIVED = "#1A1A1A"
INPUT_BG = "#1A1A1A"
HOVER_BG = "#2D0035"
ONLINE_GREEN = "#00FF00"
OFFLINE_GREY = "#666666"

def get_stylesheet() -> str:
    # colors and stylesheet
    return f"""
    * {{
        font-family: 'Segoe UI', 'Arial', sans-serif;
    }}
    QMainWindow {{
        background-color: {BLACK};
    }}
    QWidget {{
        background-color: {BLACK};
        color: {LIGHT_GREY};
    }}
    QLineEdit {{
        background-color: {INPUT_BG};
        color: {LIGHT_GREY};
        border: 1px solid #444444;
        border-radius: 8px;
        padding: 10px 14px;
        font-size: 14px;
        selection-background-color: {MAGENTA};
    }}
    QLineEdit:focus {{
        border: 1px solid {MAGENTA};
    }}
    QLineEdit:disabled {{
        background-color: #111111;
        color: #555555;
    }}
    QTextEdit, QPlainTextEdit {{
        background-color: {INPUT_BG};
        color: {LIGHT_GREY};
        border: 1px solid #444444;
        border-radius: 8px;
        padding: 8px;
        font-size: 14px;
    }}
    QTextEdit:focus, QPlainTextEdit:focus {{
        border: 1px solid {MAGENTA};
    }}
    QPushButton {{
        background-color: {MAGENTA};
        color: {BLACK};
        border: none;
        border-radius: 8px;
        padding: 10px 24px;
        font-size: 14px;
        font-weight: bold;
    }}
    QPushButton:hover {{
        background-color: #CC00CC;
    }}
    QPushButton:pressed {{
        background-color: #AA00AA;
    }}
    QPushButton:disabled {{
        background-color: #333333;
        color: #666666;
    }}
    QPushButton#secondaryBtn {{
        background-color: transparent;
        color: {MAGENTA};
        border: 1px solid {MAGENTA};
    }}
    QPushButton#secondaryBtn:hover {{
        background-color: {HOVER_BG};
    }}
    QPushButton#dangerBtn {{
        background-color: {ERROR_RED};
        color: white;
    }}
    QPushButton#dangerBtn:hover {{
        background-color: #CC0000;
    }}
    QPushButton#iconBtn {{
        background-color: transparent;
        color: {LIGHT_GREY};
        padding: 6px;
        border-radius: 20px;
        min-width: 40px;
        max-width: 40px;
        min-height: 40px;
        max-height: 40px;
    }}
    QPushButton#iconBtn:hover {{
        background-color: {MID_GREY};
    }}
    QLabel {{
        color: {LIGHT_GREY};
        background: transparent;
    }}
    QLabel#titleLabel {{
        font-size: 28px;
        font-weight: bold;
        color: {MAGENTA};
    }}
    QLabel#subtitleLabel {{
        font-size: 14px;
        color: {TEXT_GREY};
    }}
    QLabel#errorLabel {{
        color: {ERROR_RED};
        font-size: 13px;
    }}
    QLabel#successLabel {{
        color: {SUCCESS_GREEN};
        font-size: 13px;
    }}
    QLabel#headingLabel {{
        font-size: 20px;
        font-weight: bold;
        color: {MAGENTA};
    }}
    QLabel#peerNameLabel {{
        font-size: 15px;
        font-weight: bold;
        color: {LIGHT_GREY};
    }}
    QLabel#peerStatusLabel {{
        font-size: 12px;
        color: {TEXT_GREY};
    }}
    QLabel#messageLabel {{
        font-size: 14px;
        color: {LIGHT_GREY};
    }}
    QLabel#timeLabel {{
        font-size: 10px;
        color: {TEXT_GREY};
    }}
    QLabel#onlineIndicator {{
        background-color: {ONLINE_GREEN};
        border-radius: 5px;
        min-width: 10px;
        max-width: 10px;
        min-height: 10px;
        max-height: 10px;
    }}
    QLabel#offlineIndicator {{
        background-color: {OFFLINE_GREY};
        border-radius: 5px;
        min-width: 10px;
        max-width: 10px;
        min-height: 10px;
        max-height: 10px;
    }}
    QListWidget {{
        background-color: {BLACK};
        color: {LIGHT_GREY};
        border: none;
        outline: none;
        font-size: 14px;
    }}
    QListWidget::item {{
        padding: 12px 16px;
        border-bottom: 1px solid #1A1A1A;
    }}
    QListWidget::item:selected {{
        background-color: {HOVER_BG};
        color: {MAGENTA};
    }}
    QListWidget::item:hover {{
        background-color: #1A1A1A;
    }}
    QScrollArea {{
        border: none;
        background-color: {BLACK};
    }}
    QScrollBar:vertical {{
        background-color: {BLACK};
        width: 8px;
        border-radius: 4px;
    }}
    QScrollBar::handle:vertical {{
        background-color: #444444;
        border-radius: 4px;
        min-height: 30px;
    }}
    QScrollBar::handle:vertical:hover {{
        background-color: {MAGENTA};
    }}
    QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
        height: 0px;
    }}
    QFrame#chatBubble {{
        background-color: {BUBBLE_SENT};
        border-radius: 12px;
        padding: 8px;
    }}
    QFrame#chatBubbleReceived {{
        background-color: {BUBBLE_RECEIVED};
        border-radius: 12px;
        padding: 8px;
    }}
    QFrame#sidebar {{
        background-color: {DARK_GREY};
        border-right: 1px solid #1A1A1A;
    }}
    QFrame#header {{
        background-color: {DARK_GREY};
        border-bottom: 1px solid #1A1A1A;
        padding: 8px;
    }}
    QFrame#inputBar {{
        background-color: {DARK_GREY};
        border-top: 1px solid #1A1A1A;
        padding: 8px;
    }}
    QComboBox {{
        background-color: {INPUT_BG};
        color: {LIGHT_GREY};
        border: 1px solid #444444;
        border-radius: 8px;
        padding: 8px;
        font-size: 14px;
    }}
    QComboBox:focus {{
        border: 1px solid {MAGENTA};
    }}
    QComboBox::drop-down {{
        border: none;
        width: 30px;
    }}
    QComboBox QAbstractItemView {{
        background-color: {DARK_GREY};
        color: {LIGHT_GREY};
        selection-background-color: {HOVER_BG};
        border: 1px solid #444444;
    }}
    QProgressBar {{
        background-color: {INPUT_BG};
        border: none;
        border-radius: 4px;
        text-align: center;
        color: {LIGHT_GREY};
        height: 8px;
    }}
    QProgressBar::chunk {{
        background-color: {MAGENTA};
        border-radius: 4px;
    }}
    QCheckBox {{
        color: {LIGHT_GREY};
        spacing: 8px;
        font-size: 14px;
    }}
    QCheckBox::indicator {{
        width: 18px;
        height: 18px;
        border-radius: 4px;
        border: 2px solid #444444;
        background-color: transparent;
    }}
    QCheckBox::indicator:checked {{
        background-color: {MAGENTA};
        border-color: {MAGENTA};
    }}
    QSpinBox {{
        background-color: {INPUT_BG};
        color: {LIGHT_GREY};
        border: 1px solid #444444;
        border-radius: 6px;
        padding: 6px;
    }}
    QTabWidget::pane {{
        border: none;
        background-color: {BLACK};
    }}
    QTabBar::tab {{
        background-color: {DARK_GREY};
        color: {TEXT_GREY};
        padding: 10px 20px;
        border: none;
        border-bottom: 2px solid transparent;
    }}
    QTabBar::tab:selected {{
        color: {MAGENTA};
        border-bottom: 2px solid {MAGENTA};
    }}
    QTabBar::tab:hover {{
        color: {LIGHT_GREY};
    }}
    QMessageBox {{
        background-color: {DARK_GREY};
    }}
    QDialog {{
        background-color: {DARK_GREY};
    }}
    """

def apply_theme(app: QApplication):
    app.setStyleSheet(get_stylesheet())
    font = QFont("Segoe UI", 10)
    app.setFont(font)

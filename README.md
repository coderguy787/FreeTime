# FreeTime - Secure Messaging & VoIP

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

FreeTime is a privacy-first, end-to-end encrypted communication platform featuring real-time messaging, voice/video calls, group chats, and channels. Built with a modern Android client (Jetpack Compose), a Node.js master server, and a distributed peer network.

## Features

- **End-to-End Encrypted Messaging** — AES-256-GCM encryption with hardware-backed keystore
- **Voice & Video Calls** — WebRTC-based peer-to-peer calls with STUN/TURN
- **Group Chats** — Create, manage, and moderate group conversations
- **Channels** — Broadcast-style channels with subscriber management
- **Two-Factor Authentication** — TOTP-based 2FA via Speakeasy
- **Media Sharing** — Encrypted image, video, and file transfers
- **Distributed Peer Network** — Decentralized message relay and VoIP signaling
- **Cross-Platform** — Android client + Node.js server infrastructure

## Project Structure

```
FreeTime/
├── app/                    # Android client (Kotlin, Jetpack Compose)
│   ├── src/main/java/      # App source code
│   └── build.gradle        # Android build configuration
├── master-server/          # Node.js API, WebSocket, and admin servers
│   ├── api/                # REST API endpoints
│   ├── websocket/          # Socket.IO and WebSocket servers
│   ├── database/           # MongoDB schemas and setup
│   └── utils/              # Server utilities
├── peer/                   # Distributed peer server (Node.js)
├── k8s/                    # Kubernetes deployment manifests
├── docs/                   # Architecture and API documentation
└── docker-compose.yml      # Containerized deployment
```

## Quick Start

### Prerequisites

- Android Studio (for building the client)
- Node.js 18+ (for servers)
- MongoDB 6+
- Redis 7+

### Building the Android App

```bash
./gradlew :app:assembleDevDebug
```

The APK will be at `app/build/outputs/apk/dev/debug/freetime.apk`.

### Running the Master Server

```bash
cd master-server
cp config/.env.example config/.env   # Edit with your settings
npm install
npm start
```

### Running the Peer Server

```bash
cd peer
cp config/.env.example config/.env   # Edit with your settings
npm install
npm start
```

### Docker Deployment

```bash
docker-compose up -d
```

## Configuration

1. Copy `.env.example` files to `.env` in both `master-server/config/` and `peer/config/`
2. Set your own `JWT_SECRET`, `MONGODB_URI`, and `ADMIN_PASSWORD`
3. Configure your domain/IP in `app/gradle.properties`
4. (Optional) Set up Firebase Cloud Messaging for push notifications

## Security

- All messages are encrypted with AES-256-GCM before storage
- Media files are encrypted with per-file keys
- Authentication requires JWT + optional TOTP 2FA
- Network traffic should use HTTPS/WSS in production
- See [docs/SECURITY.md](docs/SECURITY.md) for detailed security architecture

## Documentation

- [API Reference](docs/API.md)
- [Architecture Overview](docs/ARCHITECTURE.md)
- [Security Architecture](docs/SECURITY.md)
- [Database Schema](docs/DATABASE.md)
- [Deployment Guide](docs/DEPLOYMENT.md)
- [Development Setup](docs/DEVELOPMENT.md)

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

## Disclaimer

This software is provided for educational and research purposes. Users are responsible for complying with applicable laws and regulations regarding encryption and communication services in their jurisdiction.

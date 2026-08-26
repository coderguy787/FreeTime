# FreeTime

FreeTime is a private messaging platform built around a simple idea: your
conversations belong to you, not to the server. Messages are encrypted on
your device before they ever leave it, using AES-256-GCM with keys that
never touch the network. The server handles routing and storage, but it
never sees what you actually said.

Beyond one-to-one chat, FreeTime supports group conversations, broadcast
channels, file and image sharing, and TOTP-based two-factor authentication.
Voice and video calling are not included in this version of the project.


## How It Works

The system is made up of three independent pieces that talk to each other
over HTTPS and WebSocket connections.

**Android Client** is a Kotlin application built with Jetpack Compose. It
handles everything the user sees and does: composing messages, managing
contacts and groups, encrypting content before sending, and decrypting it
on arrival. Local storage uses Room with Tink AES-256-GCM encryption, so
even data at rest on the device is protected.

**Master Server** is a Node.js application that does the heavy lifting on
the backend. It exposes a REST API for authentication, user management,
message routing, media handling, and group administration. A Socket.IO
layer provides real-time delivery of messages and presence updates. An
admin panel offers a web-based dashboard for monitoring the system. Data
is stored in MongoDB.

**Peer Server** is a lightweight Node.js service designed for peer-to-peer
discovery and synchronization between FreeTime instances. It handles
signaling for direct connections and coordinates status updates across
distributed nodes.

A fourth component, the Windows client, exists as a legacy PyQt6 desktop
application. It is no longer maintained but remains in the repository for
reference.


## Project Structure

```
app/                Android client (Kotlin, Jetpack Compose)
master-server/      REST API, WebSocket gateway, admin panel, database scripts
peer/               Peer signaling and discovery server
docs/               Architecture notes and design documents
windows_client/     Legacy PyQt6 desktop client (not maintained)
```


## Setting Up the Backend

The backend requires Node.js 18 or later and a running MongoDB instance.
The instructions below assume a Debian-based Linux server, but any system
with Node and MongoDB will work.

### Configuration

Environment variables live in two files:

- `master-server/config/.env` controls the API server, database connection,
  JWT secrets, SMTP credentials, and feature flags.
- `peer/config/.env` controls the peer server, its connection back to the
  master API, and its own JWT secret.

Both files ship with placeholder values. You must generate real secrets
before deploying. A quick way to produce a 32-byte hex string:

```bash
openssl rand -hex 32
```

Use the output for `JWT_SECRET`, `ADMIN_PASSWORD`, and `MASTER_API_KEY`.
Keep these values confidential and never commit them to version control.

### Database

The MongoDB database is called `freetime`. Connection details come from the
`MONGODB_URI` variable in `master-server/config/.env`. The setup scripts
in `master-server/database/` create the initial collections and indexes
when the server starts for the first time.

### Certificates

For development, generate a self-signed certificate pair:

```bash
cd master-server/certs
./create-self-signed-cert.sh
```

This produces `freetime_self_signed.pem` and a matching private key. The
Android app includes this certificate in its network security configuration
so that development builds trust your local server without warnings.

For production, replace the self-signed certificate with a real one from a
trusted authority such as Let's Encrypt. Place the full chain and private
key in the `master-server/certs/` directory and update the paths in
`.env` accordingly.

### Starting the Services

```bash
cd master-server
npm install
./start-all.sh
```

This launches four processes: the API on port 443, the WebSocket gateway
on port 8080, the admin panel on port 3001, and the peer server on
port 9080. To stop everything:

```bash
./stop-all.sh
```

The peer server can also run independently:

```bash
cd peer
npm install && npm start
```

Full deployment instructions, including firewall rules, systemd services,
log rotation, and SSL certificate renewal, are in
`DEBIAN_DEPLOYMENT_GUIDE.md`.


## Building the Android App

### Prerequisites

- Android Studio or a standalone SDK installation
- JDK 17 or later
- An Android device or emulator running API 24 or later

### Server Address

The app connects to your server using addresses defined in
`app/gradle.properties`. The key properties are:

```properties
SERVER_HOST=freetime.publicvm.com
SERVER_PORT=443
PEER_HOST=freetime.publicvm.com
PEER_PORT=9080
```

Change these to point at your own server. The API and WebSocket URLs are
constructed automatically from these values during the build.

### Firebase

The app uses Firebase Cloud Messaging for push notifications. You need a
`google-services.json` file from your own Firebase project. Place it in
the `app/` directory. This file is excluded from version control.

### Build Flavors

There are two flavors: `dev` for development and `prod` for production.
Both produce debug-signed APKs by default.

```bash
./gradlew assembleProdDebug          # build a debug APK
./gradlew installProdDebug           # build and install on a connected device
./gradlew :app:compileDevDebugKotlin :app:compileProdDebugKotlin   # quick syntax check
```

To build a release APK, you need a signing key. Configure the signing
block in `app/build.gradle` and set the keystore path in
`gradle.properties`.


## Further Reading

- `DEBIAN_DEPLOYMENT_GUIDE.md` covers server setup from scratch
- `docs/ARCHITECTURE.md` describes the system design in more detail
- `master-server/README.md` documents the API endpoints
- `SECURITY.md` outlines the encryption model and security practices

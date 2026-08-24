# FreeTime

encrypted chat app. android client + node backend (api, websocket, peer relay) + admin panel.
messages are e2e encrypted with aes-256-gcm, keys stay in the android keystore. groups, channels,
file/image sharing, totp 2fa.

no voice/video calling in this version.

## layout

```
app/            android client (kotlin, jetpack compose)
master-server/  main server: rest api + websocket + admin panel + mongo scripts
peer/           peer signaling server
docs/           architecture notes
windows_client/ old pyqt6 desktop client (not maintained)
```

## running the backend

needs node 18+ and mongodb on the same box (debian assumed).

```bash
cd master-server
npm install
./start-all.sh        # api :443, websocket :8080, admin :3001, peer :9080
./stop-all.sh
```

mongo db name is `freetime`, connection string comes from `.env` (see DEBIAN_DEPLOYMENT_GUIDE.md).
dev certs live in `master-server/certs/` (`create-self-signed-cert.sh` makes them), swap for real
ones if you put this on a public domain.

peer server runs separately:

```bash
cd peer
npm install && npm start
```

## building the app

server addresses are gradle properties, defaults point at example.com. set your own in
`gradle.properties` (SERVER_HOST, SERVER_PORT, PEER_HOST, PEER_PORT).

two flavors: `dev` and `prod`.

```bash
./gradlew assembleProdDebug     # debug apk
./gradlew installProdDebug      # straight to device
./gradlew :app:compileDevDebugKotlin :app:compileProdDebugKotlin   # quick compile check
```

## deployment

see DEBIAN_DEPLOYMENT_GUIDE.md for the full server setup (firewall, certs, mongo, logs).

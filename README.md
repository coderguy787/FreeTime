# FreeTime

encrypted chat for android. messages are end-to-end encrypted with aes-256-gcm — keys are
generated on-device and live in the android keystore, so the server only ever handles
ciphertext. direct chats, groups, channels, file/image sharing and totp two-factor auth are
in; voice/video calling is not part of this version.

under the hood it's three pieces: a kotlin/jetpack-compose app, a node backend (rest api,
websocket, admin panel, mongo) and a small peer relay for p2p traffic.

## layout

```
app/            android client (kotlin, jetpack compose)
master-server/  rest api, websocket, admin panel, mongo scripts
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

the database is `freetime`; connection string and secrets come from `.env`
(DEBIAN_DEPLOYMENT_GUIDE.md walks through it). dev certs live in `master-server/certs/` —
`create-self-signed-cert.sh` generates a pair, swap in real ones before going public.

the peer server runs separately:

```bash
cd peer
npm install && npm start
```

## building the app

server addresses are gradle properties and default to example.com — point them at your own
host in `gradle.properties` (SERVER_HOST, SERVER_PORT, PEER_HOST, PEER_PORT). there are two
build flavors, `dev` and `prod`.

```bash
./gradlew assembleProdDebug     # debug apk
./gradlew installProdDebug      # straight to device
./gradlew :app:compileDevDebugKotlin :app:compileProdDebugKotlin   # quick compile check
```

## deployment

DEBIAN_DEPLOYMENT_GUIDE.md covers the full server setup: firewall, certs, mongo, logs.

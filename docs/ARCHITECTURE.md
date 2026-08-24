# architecture

three moving parts, all talk over tls.

```
android app ──443──> master-server api (express, rest)
     │  └─8080──────> websocket server (socket.io, realtime messages + presence)
     └─9080─────────> peer server (relay/signaling between peers)

master-server api ──> mongodb (db "freetime", localhost:27017)
admin panel ──3001──> talks to the api (lan only)
```

- rest handles accounts, auth (device-bound sessions), friends, groups, channels, file upload,
  history. everything routes through `master-server/api/master-server-api.js`.
- websocket pushes messages/presence/reactions as they happen. rooms are `user:<id>` and
  `chat:<id>`. falls back to polling if the socket drops.
- peer server is the relay for direct client-to-client traffic.
- encryption is end to end: server stores ciphertext only, keys never leave the keystore.
- the app keeps a room cache locally (room + mongo-style sync), so history loads offline.

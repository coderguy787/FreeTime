# master server

node/express api + websocket + admin panel. entry point `api/master-server-api.js`.

```bash
npm start          # api
npm run ws         # websocket server
npm run admin      # admin panel
./start-all.sh     # everything at once
```

ports: api 443, websocket 8080, admin 3001 (lan only), peer 9080 lives in ../peer.

mongo: db `freetime`, configured via MONGODB_URI in .env or the defaults at the top of
`api/master-server-api.js`. schema setup scripts in `database/`.

certs in `certs/` (fullchain.pem + privkey.pem). logs in `logs/`.

api routes are all in one file on purpose (`api/master-server-api.js`), search for `app.` / `router.`
to find them. admin endpoints live in `admin-panel/`.

# peer server

signaling/relay between clients so two phones can talk directly without everything going through
the main api. plain node, no db.

```bash
npm start          # dev
npm run cluster    # cluster mode, one worker per core, port + worker id
npm test
```

port comes from config (default 9080). in cluster mode workers grab PEER_PORT+1, +2, ... so open a
range in the firewall if you use it.

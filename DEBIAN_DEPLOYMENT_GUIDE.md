# deploying to debian 13

all services on one box. tested on a clean debian 13 install.

## server side

```bash
# packages
sudo apt update
sudo apt install -y nodejs npm mongodb-org nginx ufw   # mongo needs the official repo, see mongodb docs

# firewall
sudo ufw allow 443/tcp    # api
sudo ufw allow 8080/tcp   # websocket
sudo ufw allow 9080/tcp   # peer network
sudo ufw allow 3001/tcp   # admin panel, lan only
sudo ufw enable

# certs (self-signed for lan, certbot for public domain)
cd master-server && ./create-self-signed-cert.sh
# or: sudo certbot certonly --standalone -d your-domain.com
#     cp /etc/letsencrypt/live/your-domain.com/{fullchain,privkey}.pem master-server/certs/
```

`.env` next to master-server:

```
MONGODB_URI=mongodb://localhost:27017/freetime
DOMAIN=your-domain.com
PORT_API=443
```

start/stop:

```bash
./start-all.sh
./stop-all.sh
# check it's listening: netstat -tlnp | grep node
# logs: tail -f logs/api-service.log
```

mongo quick check: `mongosh mongodb://localhost:27017/freetime`, service is `systemctl status mongod`.

## app side

point gradle at your server in `gradle.properties`:

```
SERVER_HOST=192.168.1.50      # or freetime.example.com
SERVER_PORT=443
PEER_HOST=192.168.1.50
PEER_PORT=9080
```

then `./gradlew assembleProdDebug` and adb install the apk. first thing to test after login:
friend search (`/api/users/search`) and sending a message, that exercises api + websocket in one go.

## when the app can't connect

- is the server actually up? `netstat -tlnp | grep node`
- self-signed cert on a public domain won't fly, android rejects it. use certbot.
- lan ip changed after reboot? dhcp lease expired. set a static ip or update gradle.properties.

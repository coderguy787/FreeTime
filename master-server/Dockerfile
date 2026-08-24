FROM node:20-alpine AS builder

WORKDIR /app

RUN apk add --no-cache python3 make g++

COPY package*.json ./

RUN npm ci --only=production && npm cache clean --force

FROM node:20-alpine

WORKDIR /app

RUN apk add --no-cache dumb-init curl

COPY --from=builder /app/node_modules ./node_modules

COPY package*.json ./
COPY index.js ./
COPY api/ ./api/
COPY utils/ ./utils/
COPY config/ ./config/
COPY websocket/ ./websocket/
COPY peer-manager/ ./peer-manager/
COPY peer-network/ ./peer-network/
COPY admin-panel/ ./admin-panel/
COPY database/ ./database/
COPY certs/ ./certs/
COPY logs/ ./logs/

RUN addgroup -g 1001 -S nodejs && \
    adduser -S nodejs -u 1001

RUN chown -R nodejs:nodejs /app && \
    chmod -R 755 /app/logs && \
    chmod -R 755 /app/certs

USER nodejs

ENV NODE_ENV=production
ENV PORT=443
ENV PORT_API=443
ENV PORT_ADMIN_PANEL=3001
ENV LOG_LEVEL=info

HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
  CMD curl -k -f https://localhost:443/api/health || exit 1

EXPOSE 443 3001 8080 9080

ENTRYPOINT ["/usr/sbin/dumb-init", "--"]

CMD ["node", "index.js"]

# Stage 1: Builder
FROM node:20-alpine AS builder

WORKDIR /app

# Install build dependencies
RUN apk add --no-cache python3 make g++

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci --only=production && npm cache clean --force

# Stage 2: Runtime
FROM node:20-alpine

WORKDIR /app

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init curl

# Copy node_modules from builder
COPY --from=builder /app/node_modules ./node_modules

# Copy application files
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

# Create non-root user
RUN addgroup -g 1001 -S nodejs && \
    adduser -S nodejs -u 1001

# Change ownership
RUN chown -R nodejs:nodejs /app && \
    chmod -R 755 /app/logs && \
    chmod -R 755 /app/certs

USER nodejs

# Environment variables
ENV NODE_ENV=production
ENV PORT=443
ENV PORT_API=443
ENV PORT_ADMIN_PANEL=3001
ENV LOG_LEVEL=info

# Health check (API is on 443 HTTPS)
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
  CMD curl -k -f https://localhost:443/api/health || exit 1

# Expose ports
EXPOSE 443 3001 8080 9080

# Use dumb-init to handle signals properly
ENTRYPOINT ["/usr/sbin/dumb-init", "--"]

# Start application
CMD ["node", "index.js"]

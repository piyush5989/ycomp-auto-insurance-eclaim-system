# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /app

# Keycloak coordinates are baked into the static bundle at build time.
# Override these via --build-arg or docker-compose build.args for each environment.
ARG VITE_KEYCLOAK_URL=http://localhost:8080
ARG VITE_KEYCLOAK_REALM=eclaims
ARG VITE_KEYCLOAK_CLIENT_ID=eclaims-web

# Vite reads VITE_* env vars at build time (not runtime)
ENV VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL
ENV VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM
ENV VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID

# Install dependencies first — cached unless package-lock.json changes
COPY package.json package-lock.json ./
RUN npm ci

# Copy source and build
COPY . .
RUN npm run build

# ─── Stage 2: Serve ──────────────────────────────────────────────────────────
# Minimal nginx image — serves the Vite dist and proxies /api to the backend.
FROM nginx:1.27-alpine

# Replace the default nginx vhost with our SPA + proxy config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy the Vite build output
COPY --from=build /app/dist /usr/share/nginx/html

# nginx runs as root by default; for production harden to a non-root user
# by using the nginx:1.27-alpine-slim image with USER directive.

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost/index.html | grep -q "<!DOCTYPE html" || exit 1

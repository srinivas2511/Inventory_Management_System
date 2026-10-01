# syntax=docker/dockerfile:1
# Build context is the repository root (see docker/docker-compose.yml) so nginx.conf can be shared.
# ---- build ----
FROM node:20-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build:prod

# ---- runtime ----
FROM nginx:1.27-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/ims-frontend/browser /usr/share/nginx/html
EXPOSE 80

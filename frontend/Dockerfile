# --- Build stage ---
FROM node:22-alpine AS build
WORKDIR /build

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

# --- Runtime stage ---
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
# nginx worker radi kao korisnik "nginx" - fajlovi (npr. iz public/) s pravima
# 600 bi mu bili nečitljivi (403). Svima dopusti čitanje.
RUN chmod -R a+rX /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost/ || exit 1

FROM node:22-alpine AS build
WORKDIR /app
ARG AMAP_JS_KEY
ENV VITE_AMAP_JS_KEY=$AMAP_JS_KEY
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY default.conf.template /etc/nginx/templates/default.conf.template

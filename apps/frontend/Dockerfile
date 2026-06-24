# Build stage
FROM node:20-alpine AS builder

WORKDIR /app

ARG VITE_MANAGEMENT_API_URL=http://localhost:8080
ARG VITE_COLLECTOR_API_URL=http://localhost:8081

ENV VITE_MANAGEMENT_API_URL=$VITE_MANAGEMENT_API_URL
ENV VITE_COLLECTOR_API_URL=$VITE_COLLECTOR_API_URL

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 3000

CMD ["nginx", "-g", "daemon off;"]
FROM golang:1.24-alpine AS builder

WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN go build -o /app/soc-intake-gateway .

FROM alpine:latest
WORKDIR /app
COPY --from=builder /app/soc-intake-gateway .

EXPOSE 8080
CMD ["./soc-intake-gateway"]

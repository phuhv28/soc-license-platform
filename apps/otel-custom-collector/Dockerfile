# Stage 1: Build the custom collector
FROM golang:alpine as builder

WORKDIR /build

# Install basic tools
RUN apk add --no-cache make git curl bash

# Install OCB (OpenTelemetry Collector Builder)
RUN go install go.opentelemetry.io/collector/cmd/builder@v0.98.0
RUN cp /go/bin/builder ./ocb

# Copy builder configuration
COPY builder.yaml .

# Copy custom processor
COPY processor/ ./processor/

# Run ocb to generate and compile the custom collector
# ocb will generate the code in /build/dist and then run 'go build'
RUN ./ocb --config builder.yaml

# Stage 2: Minimal runtime image
FROM alpine:3.19

RUN apk --update add ca-certificates

WORKDIR /

COPY --from=builder /build/dist/otelcol-custom /otelcol-custom
COPY otel-collector-config.yaml /otel-collector-config.yaml

EXPOSE 4317 4318

ENTRYPOINT ["/otelcol-custom"]
CMD ["--config", "/otel-collector-config.yaml"]

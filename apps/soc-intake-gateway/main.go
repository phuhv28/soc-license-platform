package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	"github.com/redis/go-redis/v9"
	"go.uber.org/zap"
)

func main() {
	logger, _ := zap.NewProduction()
	defer logger.Sync()

	redisURL := os.Getenv("REDIS_URL")
	if redisURL == "" {
		redisURL = "redis:6379"
	}
	kafkaBrokers := os.Getenv("KAFKA_BROKERS")
	if kafkaBrokers == "" {
		kafkaBrokers = "kafka:9092"
	}
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	burstMultiplierStr := os.Getenv("BURST_MULTIPLIER")
	burstMultiplier := 10.0
	if burstMultiplierStr != "" {
		if parsed, err := strconv.ParseFloat(burstMultiplierStr, 64); err == nil && parsed >= 1.0 {
			burstMultiplier = parsed
		}
	}

	redisClient := redis.NewClient(&redis.Options{
		Addr: redisURL,
	})

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := redisClient.Ping(ctx).Err(); err != nil {
		logger.Fatal("Failed to connect to redis", zap.Error(err))
	}

	kafkaProducer, err := newKafkaProducer(kafkaBrokers)
	if err != nil {
		logger.Fatal("Failed to create kafka producer", zap.Error(err))
	}
	defer kafkaProducer.Close()

	metricsManager := newMetricsManager(redisClient, kafkaProducer, burstMultiplier, logger)
	metricsManager.Start()
	defer metricsManager.Stop()

	server := newIntakeServer(redisClient, kafkaProducer, metricsManager, logger)

	srv := &http.Server{
		Addr:    ":" + port,
		Handler: server.router(),
	}

	go func() {
		logger.Info(fmt.Sprintf("Starting custom intake gateway on port %s", port))
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Fatal("listen error", zap.Error(err))
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	logger.Info("Shutting down server...")

	ctxShutDown, cancelShutDown := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancelShutDown()

	if err := srv.Shutdown(ctxShutDown); err != nil {
		logger.Fatal("Server Shutdown Failed", zap.Error(err))
	}
	logger.Info("Server exited")
}

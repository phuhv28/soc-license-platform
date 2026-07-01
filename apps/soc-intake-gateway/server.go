package main

import (
	"context"
	"encoding/json"
	"io"
	"net/http"

	"github.com/redis/go-redis/v9"
	"go.uber.org/zap"
)

type intakeServer struct {
	redisClient    *redis.Client
	kafkaProducer  *kafkaProducer
	metricsManager *metricsManager
	logger         *zap.Logger
}

func newIntakeServer(r *redis.Client, k *kafkaProducer, m *metricsManager, logger *zap.Logger) *intakeServer {
	return &intakeServer{
		redisClient:    r,
		kafkaProducer:  k,
		metricsManager: m,
		logger:         logger,
	}
}

func (s *intakeServer) router() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/api/v1/logs", s.handleLogs)
	mux.HandleFunc("/health", s.handleHealth)
	return mux
}

func (s *intakeServer) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}

func (s *intakeServer) handleLogs(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	tenantID := r.Header.Get("X-Tenant-ID")
	if tenantID == "" {
		http.Error(w, "Missing X-Tenant-ID header", http.StatusUnauthorized)
		return
	}

	agentName := r.Header.Get("X-Agent-Name")
	if agentName == "" {
		agentName = "unknown"
	}

	bodyBytes, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Failed to read body", http.StatusBadRequest)
		return
	}
	defer r.Body.Close()

	var events []map[string]interface{}
	if err := json.Unmarshal(bodyBytes, &events); err != nil {
		http.Error(w, "Invalid JSON array", http.StatusBadRequest)
		return
	}

	requested := int64(len(events))
	if requested == 0 {
		w.WriteHeader(http.StatusOK)
		return
	}

	accepted := s.metricsManager.requestTokens(r.Context(), tenantID, requested)

	// Metrics breakdown per log source
	var currentAccepted int64 = 0
	var acceptedEvents []map[string]interface{}
	if accepted > 0 {
		acceptedEvents = make([]map[string]interface{}, 0, accepted)
	}

	for _, event := range events {
		logSource := "unknown"
		if ls, ok := event["log.source"].(string); ok {
			logSource = ls
		}

		if currentAccepted < accepted {
			currentAccepted++
			acceptedEvents = append(acceptedEvents, event)
			s.metricsManager.recordMetrics(tenantID, agentName, logSource, 1, 1, 0)
		} else {
			s.metricsManager.recordMetrics(tenantID, agentName, logSource, 1, 0, 1)
		}
	}

	if accepted > 0 {
		acceptedBytes, err := json.Marshal(acceptedEvents)
		if err != nil {
			s.logger.Error("Failed to marshal accepted events", zap.Error(err))
		} else if s.kafkaProducer != nil {
			if err := s.kafkaProducer.Produce(context.Background(), tenantID, acceptedBytes); err != nil {
				s.logger.Error("Failed to produce to kafka", zap.Error(err))
			}
		}
	}

	if accepted == 0 {
		http.Error(w, "Quota exceeded", http.StatusTooManyRequests)
		return
	}

	w.WriteHeader(http.StatusAccepted)
}

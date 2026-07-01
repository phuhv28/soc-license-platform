package main

import (
	"bytes"
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/alicebob/miniredis/v2"
	"github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestIntakeServer_HandleHealth(t *testing.T) {
	s := &intakeServer{}
	req, _ := http.NewRequest("GET", "/health", nil)
	rr := httptest.NewRecorder()

	s.handleHealth(rr, req)

	assert.Equal(t, http.StatusOK, rr.Code)
	assert.Equal(t, "OK", rr.Body.String())
}

func TestIntakeServer_HandleLogs_MissingTenant(t *testing.T) {
	s := &intakeServer{}
	req, _ := http.NewRequest("POST", "/api/v1/logs", nil)
	rr := httptest.NewRecorder()

	s.handleLogs(rr, req)

	assert.Equal(t, http.StatusUnauthorized, rr.Code)
}

func TestIntakeServer_HandleLogs_Success(t *testing.T) {
	mr, _ := miniredis.Run()
	defer mr.Close()

	rdb := redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	logger, _ := zap.NewDevelopment()
	m := newMetricsManager(rdb, nil, 1.0, logger)

	s := newIntakeServer(rdb, nil, m, logger)

	// Set quota
	rdb.Set(context.Background(), "quota:tenant-1", "1000", 0)

	payload := `[{"log.source": "nginx", "message": "hello"}, {"message": "world"}]`
	req, _ := http.NewRequest("POST", "/api/v1/logs", bytes.NewBufferString(payload))
	req.Header.Set("X-Tenant-ID", "tenant-1")
	req.Header.Set("X-Agent-Name", "agent-x")

	rr := httptest.NewRecorder()

	s.handleLogs(rr, req)

	assert.Equal(t, http.StatusAccepted, rr.Code)

	state := m.getTenantState("tenant-1")
	state.metricsMu.RLock()
	defer state.metricsMu.RUnlock()

	assert.Equal(t, int64(2), state.metrics.TotalReceived)
	assert.Equal(t, int64(2), state.metrics.TotalAccepted)
	assert.Equal(t, int64(0), state.metrics.TotalDropped)
}

func TestIntakeServer_HandleLogs_QuotaExceeded(t *testing.T) {
	mr, _ := miniredis.Run()
	defer mr.Close()

	rdb := redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	logger, _ := zap.NewDevelopment()
	m := newMetricsManager(rdb, nil, 1.0, logger)

	s := newIntakeServer(rdb, nil, m, logger)

	// Set quota to 0 to force drop
	rdb.Set(context.Background(), "quota:tenant-1", "0", 0)

	payload := `[{"log.source": "nginx", "message": "hello"}]`
	req, _ := http.NewRequest("POST", "/api/v1/logs", bytes.NewBufferString(payload))
	req.Header.Set("X-Tenant-ID", "tenant-1")

	rr := httptest.NewRecorder()

	s.handleLogs(rr, req)

	assert.Equal(t, http.StatusTooManyRequests, rr.Code)

	state := m.getTenantState("tenant-1")
	state.metricsMu.RLock()
	defer state.metricsMu.RUnlock()

	assert.Equal(t, int64(1), state.metrics.TotalReceived)
	assert.Equal(t, int64(0), state.metrics.TotalAccepted)
	assert.Equal(t, int64(1), state.metrics.TotalDropped)
}

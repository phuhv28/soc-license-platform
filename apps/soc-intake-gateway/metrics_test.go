package main

import (
	"context"
	"testing"

	"github.com/alicebob/miniredis/v2"
	"github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestMetricsManager_RequestTokens(t *testing.T) {
	mr, err := miniredis.Run()
	if err != nil {
		t.Fatalf("failed to run miniredis: %v", err)
	}
	defer mr.Close()

	rdb := redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	logger, _ := zap.NewDevelopment()
	m := newMetricsManager(rdb, nil, 1.0, logger)

	ctx := context.Background()
	tenantID := "test-tenant"

	// Setup quota in redis
	rdb.Set(ctx, "quota:"+tenantID, "1000", 0)

	// Test request smaller than quota * window
	accepted := m.requestTokens(ctx, tenantID, 100)
	assert.Equal(t, int64(100), accepted)

	state := m.getTenantState(tenantID)
	assert.GreaterOrEqual(t, state.tokens, int64(0)) // tokens should be remaining

	// Test request larger than available tokens
	// Force clear memory tokens to trigger redis fetch
	state.mu.Lock()
	state.tokens = 0
	state.mu.Unlock()

	accepted = m.requestTokens(ctx, tenantID, 1000000) // Much larger than quota * window * burst
	// 1000 * 1 * 300 = 300000 max tokens
	// Previous usage was 100 + some prefetch
	// Should return less than requested
	assert.Greater(t, accepted, int64(0))
	assert.Less(t, accepted, int64(1000000))
}

func TestMetricsManager_RecordMetrics(t *testing.T) {
	logger, _ := zap.NewDevelopment()
	m := newMetricsManager(nil, nil, 1.0, logger)

	tenantID := "test-tenant"
	m.recordMetrics(tenantID, "agent-1", "source-1", 100, 80, 20)
	m.recordMetrics(tenantID, "agent-1", "source-2", 50, 40, 10)

	state := m.getTenantState(tenantID)
	assert.NotNil(t, state)

	state.metricsMu.RLock()
	defer state.metricsMu.RUnlock()

	assert.Equal(t, int64(150), state.metrics.TotalReceived)
	assert.Equal(t, int64(120), state.metrics.TotalAccepted)
	assert.Equal(t, int64(30), state.metrics.TotalDropped)

	agentMetrics := state.metrics.AgentMetrics["agent-1"]
	assert.NotNil(t, agentMetrics)
	assert.Equal(t, int64(150), agentMetrics.Received)
	
	source1Metrics := state.metrics.LogSourceMetrics["source-1"]
	assert.NotNil(t, source1Metrics)
	assert.Equal(t, int64(100), source1Metrics.Received)
}

func TestGetWindows(t *testing.T) {
	// Mon, 01 Jan 2024 12:34:56 UTC
	unixSec := int64(1704112496)

	m1 := getMinuteWindow(unixSec)
	assert.Equal(t, "202401011234", m1)

	m5 := getFloorMinuteWindow(unixSec, 5)
	assert.Equal(t, "202401011230", m5)

	m15 := getFloorMinuteWindow(unixSec, 15)
	assert.Equal(t, "202401011230", m15)

	d1 := getDayWindow(unixSec)
	assert.Equal(t, "20240101", d1)
}

func TestMetricsManager_FlushToRedis(t *testing.T) {
	mr, _ := miniredis.Run()
	defer mr.Close()

	rdb := redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	logger, _ := zap.NewDevelopment()
	m := newMetricsManager(rdb, nil, 1.0, logger)

	tenantID := "test-tenant"
	m.recordMetrics(tenantID, "agent-1", "source-1", 100, 80, 20)

	m.flushToRedis()

	// Check if keys exist in redis
	keys, _ := rdb.Keys(context.Background(), "*").Result()
	assert.NotEmpty(t, keys)

	// Since we mock time with time.Now() internally, we just check that some top/usage keys were generated
	foundTop := false
	foundUsage := false
	for _, key := range keys {
		if len(key) >= 3 && key[:3] == "top" {
			foundTop = true
		}
		if len(key) >= 5 && key[:5] == "usage" {
			foundUsage = true
		}
	}
	assert.True(t, foundTop, "Should have top keys in redis")
	assert.True(t, foundUsage, "Should have usage keys in redis")

	// State metrics should be cleared
	state := m.getTenantState(tenantID)
	state.metricsMu.RLock()
	defer state.metricsMu.RUnlock()
	assert.Equal(t, int64(0), state.metrics.TotalReceived)
}

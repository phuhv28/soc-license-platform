package main

import (
	"context"
	"fmt"
	"strconv"
	"sync"
	"time"

	"github.com/redis/go-redis/v9"
	"go.uber.org/zap"
)

var tokenPrefetchScript = redis.NewScript(`
	local quota = tonumber(KEYS[1])
	local tokens_key = KEYS[2]

	local now = tonumber(ARGV[1])
	local requested = tonumber(ARGV[2])
	local burst_multiplier = tonumber(ARGV[3])
	
	local window_size = 300
	local limit = quota * burst_multiplier * window_size

	local current_window = math.floor(now / window_size)
	local key = tokens_key .. ":" .. current_window

	local current_usage = tonumber(redis.call("get", key) or "0")

	if current_usage + requested > limit then
		local granted = limit - current_usage
		if granted > 0 then
			redis.call("incrby", key, granted)
			redis.call("expire", key, window_size * 2)
			return granted
		end
		return 0
	end

	redis.call("incrby", key, requested)
	redis.call("expire", key, window_size * 2)
	return requested
`)

type tenantDimensionMetrics struct {
	Received int64
	Accepted int64
	Dropped  int64
}

type tenantMetrics struct {
	TotalReceived int64
	TotalAccepted int64
	TotalDropped  int64

	AgentMetrics     map[string]*tenantDimensionMetrics
	LogSourceMetrics map[string]*tenantDimensionMetrics
}

func newTenantMetrics() *tenantMetrics {
	return &tenantMetrics{
		AgentMetrics:     make(map[string]*tenantDimensionMetrics),
		LogSourceMetrics: make(map[string]*tenantDimensionMetrics),
	}
}

type tenantState struct {
	mu     sync.Mutex
	tokens int64

	metricsMu sync.Mutex
	metrics   *tenantMetrics
}

func newTenantState() *tenantState {
	return &tenantState{
		metrics: newTenantMetrics(),
	}
}

type metricsManager struct {
	client *redis.Client
	logger *zap.Logger

	statesMu sync.RWMutex
	states   map[string]*tenantState

	burstMultiplier float64

	cancel context.CancelFunc
}

func newMetricsManager(client *redis.Client, burstMultiplier float64, logger *zap.Logger) *metricsManager {
	return &metricsManager{
		client: client,
		logger: logger,
		states: make(map[string]*tenantState),
		burstMultiplier: burstMultiplier,
	}
}

func (m *metricsManager) Start() {
	ctx, cancel := context.WithCancel(context.Background())
	m.cancel = cancel
	go m.syncLoop(ctx)
}

func (m *metricsManager) Stop() {
	if m.cancel != nil {
		m.cancel()
	}
}

func (m *metricsManager) getTenantState(tenantID string) *tenantState {
	m.statesMu.RLock()
	state, ok := m.states[tenantID]
	m.statesMu.RUnlock()

	if ok {
		return state
	}

	m.statesMu.Lock()
	defer m.statesMu.Unlock()
	state, ok = m.states[tenantID]
	if !ok {
		state = newTenantState()
		m.states[tenantID] = state
	}
	return state
}

func (m *metricsManager) requestTokens(ctx context.Context, tenantID string, requested int64) int64 {
	state := m.getTenantState(tenantID)
	
	state.mu.Lock()
	defer state.mu.Unlock()

	if state.tokens < requested {
		needed := requested - state.tokens
		prefetch := int64(200) // Default batch size
		if needed > prefetch {
			prefetch = needed
		}

		quotaStr, _ := m.client.Get(ctx, "quota:"+tenantID).Result()
		quota, err := strconv.ParseInt(quotaStr, 10, 64)
		if err != nil || quota <= 0 {
			// No active license or quota is 0, reject all logs
			return 0
		}

		nowFloat := float64(time.Now().UnixMilli()) / 1000.0

		keys := []string{
			fmt.Sprintf("%d", quota),
			fmt.Sprintf("rl:tokens:%s", tenantID),
			fmt.Sprintf("rl:ts:%s", tenantID),
		}
		grantedIntf, err := tokenPrefetchScript.Run(ctx, m.client, keys, nowFloat, prefetch, m.burstMultiplier).Result()
		if err == nil {
			state.tokens += grantedIntf.(int64)
		} else {
			m.logger.Warn("Failed to prefetch tokens", zap.Error(err))
		}
	}

	var accepted int64 = 0
	if state.tokens >= requested {
		accepted = requested
		state.tokens -= requested
	} else {
		accepted = state.tokens
		state.tokens = 0
	}

	return accepted
}

func (m *metricsManager) recordMetrics(tenantID, agentName, logSource string, received, accepted, dropped int64) {
	state := m.getTenantState(tenantID)
	
	state.metricsMu.Lock()
	defer state.metricsMu.Unlock()

	state.metrics.TotalReceived += received
	state.metrics.TotalAccepted += accepted
	state.metrics.TotalDropped += dropped
	
	if _, ok := state.metrics.AgentMetrics[agentName]; !ok {
		state.metrics.AgentMetrics[agentName] = &tenantDimensionMetrics{}
	}
	state.metrics.AgentMetrics[agentName].Received += received
	state.metrics.AgentMetrics[agentName].Accepted += accepted
	state.metrics.AgentMetrics[agentName].Dropped += dropped

	if _, ok := state.metrics.LogSourceMetrics[logSource]; !ok {
		state.metrics.LogSourceMetrics[logSource] = &tenantDimensionMetrics{}
	}
	state.metrics.LogSourceMetrics[logSource].Received += received
	state.metrics.LogSourceMetrics[logSource].Accepted += accepted
	state.metrics.LogSourceMetrics[logSource].Dropped += dropped
}

func (m *metricsManager) syncLoop(ctx context.Context) {
	ticker := time.NewTicker(3 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			m.flushToRedis()
		}
	}
}

func (m *metricsManager) flushToRedis() {
	nowSec := time.Now().Unix()
	window1m := getMinuteWindow(nowSec)
	window5m := getFloorMinuteWindow(nowSec, 5)
	window15m := getFloorMinuteWindow(nowSec, 15)
	window1d := getDayWindow(nowSec)

	pipe := m.client.Pipeline()
	hasData := false

	m.statesMu.RLock()
	var tenantIDs []string
	for tenantID := range m.states {
		tenantIDs = append(tenantIDs, tenantID)
	}
	m.statesMu.RUnlock()

	for _, tenantID := range tenantIDs {
		state := m.getTenantState(tenantID)
		
		state.metricsMu.Lock()
		currentMetrics := state.metrics
		state.metrics = newTenantMetrics()
		state.metricsMu.Unlock()

		if currentMetrics.TotalReceived == 0 {
			continue
		}
		hasData = true

		received := currentMetrics.TotalReceived
		accepted := currentMetrics.TotalAccepted
		dropped := currentMetrics.TotalDropped

		incrementWindowCounters(pipe, tenantID, "1m", window1m, received, accepted, dropped, 48*time.Hour)
		incrementWindowCounters(pipe, tenantID, "5m", window5m, received, accepted, dropped, 7*24*time.Hour)
		incrementWindowCounters(pipe, tenantID, "15m", window15m, received, accepted, dropped, 14*24*time.Hour)
		incrementWindowCounters(pipe, tenantID, "1d", window1d, received, accepted, dropped, 90*24*time.Hour)

		for agent, mStats := range currentMetrics.AgentMetrics {
			incrementDimension(pipe, tenantID, "agent", agent, window1m, window5m, window15m, window1d, mStats.Received, mStats.Accepted, mStats.Dropped)
		}

		for ls, mStats := range currentMetrics.LogSourceMetrics {
			incrementDimension(pipe, tenantID, "logsource", ls, window1m, window5m, window15m, window1d, mStats.Received, mStats.Accepted, mStats.Dropped)
		}
	}

	if hasData {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if _, err := pipe.Exec(ctx); err != nil {
			m.logger.Error("Failed to flush redis metrics", zap.Error(err))
		}
	}
}

func incrementWindowCounters(pipe redis.Pipeliner, tenantId, windowType, windowKey string, received, accepted, dropped int64, ttl time.Duration) {
	if received > 0 {
		k := fmt.Sprintf("usage:%s:received:%s:%s", tenantId, windowType, windowKey)
		pipe.IncrBy(context.Background(), k, received)
		pipe.Expire(context.Background(), k, ttl)
	}
	if accepted > 0 {
		k := fmt.Sprintf("usage:%s:accepted:%s:%s", tenantId, windowType, windowKey)
		pipe.IncrBy(context.Background(), k, accepted)
		pipe.Expire(context.Background(), k, ttl)
	}
	if dropped > 0 {
		k := fmt.Sprintf("usage:%s:dropped:%s:%s", tenantId, windowType, windowKey)
		pipe.IncrBy(context.Background(), k, dropped)
		pipe.Expire(context.Background(), k, ttl)
	}
}

func incrementDimension(pipe redis.Pipeliner, tenantId, dimType, dimValue, window1m, window5m, window15m, window1d string, received, accepted, dropped int64) {
	windows := map[string]string{
		"1m":  window1m,
		"5m":  window5m,
		"15m": window15m,
		"1d":  window1d,
	}

	ttls := map[string]time.Duration{
		"1m":  48 * time.Hour,
		"5m":  7 * 24 * time.Hour,
		"15m": 14 * 24 * time.Hour,
		"1d":  90 * 24 * time.Hour,
	}

	for wType, wKey := range windows {
		ttl := ttls[wType]
		if received > 0 {
			k := fmt.Sprintf("top:%s:%s:received:%s:%s", tenantId, dimType, wType, wKey)
			pipe.ZIncrBy(context.Background(), k, float64(received), dimValue)
			pipe.Expire(context.Background(), k, ttl)
		}
		if accepted > 0 {
			k := fmt.Sprintf("top:%s:%s:accepted:%s:%s", tenantId, dimType, wType, wKey)
			pipe.ZIncrBy(context.Background(), k, float64(accepted), dimValue)
			pipe.Expire(context.Background(), k, ttl)
		}
		if dropped > 0 {
			k := fmt.Sprintf("top:%s:%s:dropped:%s:%s", tenantId, dimType, wType, wKey)
			pipe.ZIncrBy(context.Background(), k, float64(dropped), dimValue)
			pipe.Expire(context.Background(), k, ttl)
		}
	}
}

func getMinuteWindow(unixSeconds int64) string {
	return time.Unix(unixSeconds, 0).Format("200601021504")
}

func getFloorMinuteWindow(unixSeconds int64, minuteInterval int) string {
	t := time.Unix(unixSeconds, 0)
	flooredMinute := (t.Minute() / minuteInterval) * minuteInterval
	t = time.Date(t.Year(), t.Month(), t.Day(), t.Hour(), flooredMinute, 0, 0, t.Location())
	return t.Format("200601021504")
}

func getDayWindow(unixSeconds int64) string {
	return time.Unix(unixSeconds, 0).Format("20060102")
}

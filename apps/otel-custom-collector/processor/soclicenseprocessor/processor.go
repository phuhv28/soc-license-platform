package soclicenseprocessor

import (
	"context"
	"fmt"
	"strconv"
	"sync"
	"time"

	"github.com/redis/go-redis/v9"
	"go.opentelemetry.io/collector/component"
	"go.opentelemetry.io/collector/consumer"
	"go.opentelemetry.io/collector/pdata/plog"
	"go.uber.org/zap"
)

var (
	tokenPrefetchScript = redis.NewScript(`
		local quota = tonumber(KEYS[1])
		local tokens_key = KEYS[2]
		local timestamp_key = KEYS[3]
		local rate = quota
		local capacity = quota

		local now = tonumber(ARGV[1])
		local requested = tonumber(ARGV[2])

		local fill_time = capacity / rate
		local ttl = math.floor(fill_time * 2)
		if ttl < 60 then
			ttl = 60
		end

		local last_tokens = tonumber(redis.call("get", tokens_key))
		if last_tokens == nil then
			last_tokens = capacity
		end

		local last_refreshed = tonumber(redis.call("get", timestamp_key))
		if last_refreshed == nil then
			last_refreshed = 0
		end

		local delta = math.max(0, now - last_refreshed)
		local filled_tokens = math.min(capacity, last_tokens + (delta * rate))
		
		local granted = 0
		if filled_tokens >= requested then
			granted = requested
			filled_tokens = filled_tokens - requested
		else
			granted = math.floor(filled_tokens)
			filled_tokens = filled_tokens - granted
		end

		redis.call("setex", tokens_key, ttl, filled_tokens)
		redis.call("setex", timestamp_key, ttl, now)

		return granted
	`)
)

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

type socLicenseProcessor struct {
	client       *redis.Client
	nextConsumer consumer.Logs
	logger       *zap.Logger

	statesMu sync.RWMutex
	states   map[string]*tenantState
	cancel   context.CancelFunc
}

func newSocLicenseProcessor(cfg *Config, nextConsumer consumer.Logs, logger *zap.Logger) (*socLicenseProcessor, error) {
	client := redis.NewClient(&redis.Options{
		Addr: cfg.RedisURL,
	})
	
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := client.Ping(ctx).Err(); err != nil {
		return nil, fmt.Errorf("failed to connect to redis: %w", err)
	}

	return &socLicenseProcessor{
		client:       client,
		nextConsumer: nextConsumer,
		logger:       logger,
		states:       make(map[string]*tenantState),
	}, nil
}

func (p *socLicenseProcessor) getTenantState(tenantID string) *tenantState {
	p.statesMu.RLock()
	state, ok := p.states[tenantID]
	p.statesMu.RUnlock()

	if ok {
		return state
	}

	p.statesMu.Lock()
	defer p.statesMu.Unlock()
	state, ok = p.states[tenantID]
	if !ok {
		state = newTenantState()
		p.states[tenantID] = state
	}
	return state
}

func (p *socLicenseProcessor) Start(ctx context.Context, host component.Host) error {
	syncCtx, cancel := context.WithCancel(context.Background())
	p.cancel = cancel
	go p.syncMetricsLoop(syncCtx)
	return nil
}

func (p *socLicenseProcessor) Shutdown(ctx context.Context) error {
	if p.cancel != nil {
		p.cancel()
	}
	return p.client.Close()
}

func (p *socLicenseProcessor) Capabilities() consumer.Capabilities {
	return consumer.Capabilities{MutatesData: true}
}

func (p *socLicenseProcessor) ConsumeLogs(ctx context.Context, ld plog.Logs) error {
	nowMs := time.Now().UnixMilli()
	nowFloat := float64(nowMs) / 1000.0

	var totalAccepted int64

	for i := 0; i < ld.ResourceLogs().Len(); i++ {
		rl := ld.ResourceLogs().At(i)
		tenantID := "unknown"
		agentName := "unknown"

		if val, ok := rl.Resource().Attributes().Get("tenant.id"); ok {
			tenantID = val.Str()
		}
		if val, ok := rl.Resource().Attributes().Get("agent.name"); ok {
			agentName = val.Str()
		}

		var resourceRecordsCount int64 = 0
		for j := 0; j < rl.ScopeLogs().Len(); j++ {
			sl := rl.ScopeLogs().At(j)
			resourceRecordsCount += int64(sl.LogRecords().Len())
		}

		if resourceRecordsCount == 0 {
			continue
		}

		state := p.getTenantState(tenantID)
		
		state.mu.Lock()
		if state.tokens < resourceRecordsCount {
			needed := resourceRecordsCount - state.tokens
			prefetch := int64(200) // Default batch size
			if needed > prefetch {
				prefetch = needed
			}

			// Prefetch from Redis synchronously to guarantee limits
			quotaStr, _ := p.client.Get(ctx, "quota:"+tenantID).Result()
			quota, _ := strconv.ParseInt(quotaStr, 10, 64)
			if quota <= 0 {
				quota = 100 // default fallback
			}

			keys := []string{
				fmt.Sprintf("%d", quota),
				fmt.Sprintf("rl:tokens:%s", tenantID),
				fmt.Sprintf("rl:ts:%s", tenantID),
			}
			grantedIntf, err := tokenPrefetchScript.Run(ctx, p.client, keys, nowFloat, prefetch).Result()
			if err == nil {
				state.tokens += grantedIntf.(int64)
			} else {
				p.logger.Warn("Failed to prefetch tokens", zap.Error(err))
			}
		}

		var acceptedCount int64 = 0
		if state.tokens >= resourceRecordsCount {
			acceptedCount = resourceRecordsCount
			state.tokens -= resourceRecordsCount
		} else {
			acceptedCount = state.tokens
			state.tokens = 0
		}
		state.mu.Unlock()

		droppedCount := resourceRecordsCount - acceptedCount
		totalAccepted += acceptedCount

		// Filter logs
		var currentAccepted int64 = 0
		
		// Record local metrics safely
		state.metricsMu.Lock()
		state.metrics.TotalReceived += resourceRecordsCount
		state.metrics.TotalAccepted += acceptedCount
		state.metrics.TotalDropped += droppedCount
		
		if _, ok := state.metrics.AgentMetrics[agentName]; !ok {
			state.metrics.AgentMetrics[agentName] = &tenantDimensionMetrics{}
		}
		state.metrics.AgentMetrics[agentName].Received += resourceRecordsCount
		state.metrics.AgentMetrics[agentName].Accepted += acceptedCount
		state.metrics.AgentMetrics[agentName].Dropped += droppedCount

		for j := 0; j < rl.ScopeLogs().Len(); j++ {
			sl := rl.ScopeLogs().At(j)
			newRecords := sl.LogRecords()
			newRecords.RemoveIf(func(lr plog.LogRecord) bool {
				logSource := "unknown"
				if val, ok := lr.Attributes().Get("log.source"); ok {
					logSource = val.Str()
				}
				
				if _, ok := state.metrics.LogSourceMetrics[logSource]; !ok {
					state.metrics.LogSourceMetrics[logSource] = &tenantDimensionMetrics{}
				}
				state.metrics.LogSourceMetrics[logSource].Received++

				if currentAccepted < acceptedCount {
					currentAccepted++
					state.metrics.LogSourceMetrics[logSource].Accepted++
					return false // keep
				} else {
					state.metrics.LogSourceMetrics[logSource].Dropped++
					return true // drop
				}
			})
		}
		state.metricsMu.Unlock()
	}

	if totalAccepted > 0 {
		ld.ResourceLogs().RemoveIf(func(rl plog.ResourceLogs) bool {
			rl.ScopeLogs().RemoveIf(func(sl plog.ScopeLogs) bool {
				return sl.LogRecords().Len() == 0
			})
			return rl.ScopeLogs().Len() == 0
		})

		if ld.ResourceLogs().Len() > 0 {
			return p.nextConsumer.ConsumeLogs(ctx, ld)
		}
	}

	return nil
}

func (p *socLicenseProcessor) syncMetricsLoop(ctx context.Context) {
	ticker := time.NewTicker(3 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			p.flushMetricsToRedis()
		}
	}
}

func (p *socLicenseProcessor) flushMetricsToRedis() {
	nowSec := time.Now().Unix()
	window1m := getMinuteWindow(nowSec)
	window5m := getFloorMinuteWindow(nowSec, 5)
	window15m := getFloorMinuteWindow(nowSec, 15)
	window1d := getDayWindow(nowSec)

	pipe := p.client.Pipeline()
	hasData := false

	p.statesMu.RLock()
	// Copy tenant IDs to avoid holding statesMu too long
	var tenantIDs []string
	for tenantID := range p.states {
		tenantIDs = append(tenantIDs, tenantID)
	}
	p.statesMu.RUnlock()

	for _, tenantID := range tenantIDs {
		state := p.getTenantState(tenantID)
		
		state.metricsMu.Lock()
		// Swap metrics to reset counters quickly
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

		for agent, m := range currentMetrics.AgentMetrics {
			incrementDimension(pipe, tenantID, "agent", agent, window1m, window5m, window15m, m.Received, m.Accepted, m.Dropped)
		}

		for ls, m := range currentMetrics.LogSourceMetrics {
			incrementDimension(pipe, tenantID, "logsource", ls, window1m, window5m, window15m, m.Received, m.Accepted, m.Dropped)
		}
	}

	if hasData {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if _, err := pipe.Exec(ctx); err != nil {
			p.logger.Error("Failed to flush redis metrics", zap.Error(err))
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

func incrementDimension(pipe redis.Pipeliner, tenantId, dimType, dimValue, window1m, window5m, window15m string, received, accepted, dropped int64) {
	windows := map[string]string{
		"1m":  window1m,
		"5m":  window5m,
		"15m": window15m,
	}

	ttls := map[string]time.Duration{
		"1m":  48 * time.Hour,
		"5m":  7 * 24 * time.Hour,
		"15m": 14 * 24 * time.Hour,
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

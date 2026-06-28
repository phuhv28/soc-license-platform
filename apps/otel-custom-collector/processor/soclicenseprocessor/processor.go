package soclicenseprocessor

import (
	"context"
	"fmt"
	"strconv"
	"time"

	"github.com/redis/go-redis/v9"
	"go.opentelemetry.io/collector/component"
	"go.opentelemetry.io/collector/consumer"
	"go.opentelemetry.io/collector/pdata/plog"
	"go.uber.org/zap"
)

var (
	tokenBucketScript = redis.NewScript(`
		local quota = tonumber(KEYS[1])
		local tokens_key = KEYS[2]
		local timestamp_key = KEYS[3]
		local rate = quota
		local capacity = quota

		local now = tonumber(ARGV[1])
		local requested = tonumber(ARGV[2])

		local fill_time = capacity / rate
		local ttl = math.floor(fill_time * 2)

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
		local allowed = filled_tokens >= requested
		local new_tokens = filled_tokens
		local accepted_count = 0

		if allowed then
			new_tokens = filled_tokens - requested
			accepted_count = requested
		else
			accepted_count = math.floor(filled_tokens)
			new_tokens = filled_tokens - accepted_count
		end

		redis.call("setex", tokens_key, ttl, new_tokens)
		redis.call("setex", timestamp_key, ttl, now)

		return accepted_count
	`)
)

type socLicenseProcessor struct {
	client       *redis.Client
	nextConsumer consumer.Logs
	logger       *zap.Logger
}

func newSocLicenseProcessor(cfg *Config, nextConsumer consumer.Logs, logger *zap.Logger) (*socLicenseProcessor, error) {
	client := redis.NewClient(&redis.Options{
		Addr: cfg.RedisURL,
	})
	
	// Test connection
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := client.Ping(ctx).Err(); err != nil {
		return nil, fmt.Errorf("failed to connect to redis: %w", err)
	}

	return &socLicenseProcessor{
		client:       client,
		nextConsumer: nextConsumer,
		logger:       logger,
	}, nil
}

func (p *socLicenseProcessor) Start(ctx context.Context, host component.Host) error {
	return nil
}

func (p *socLicenseProcessor) Shutdown(ctx context.Context) error {
	return p.client.Close()
}

func (p *socLicenseProcessor) Capabilities() consumer.Capabilities {
	return consumer.Capabilities{MutatesData: true}
}

func (p *socLicenseProcessor) ConsumeLogs(ctx context.Context, ld plog.Logs) error {
	p.logger.Debug("ConsumeLogs invoked", zap.Int("resource_logs_count", ld.ResourceLogs().Len()))

	nowMs := time.Now().UnixMilli()
	nowSec := nowMs / 1000

	window1m := getMinuteWindow(nowSec)
	window5m := getFloorMinuteWindow(nowSec, 5)
	window15m := getFloorMinuteWindow(nowSec, 15)
	window1d := getDayWindow(nowSec)

	var totalReceived int64
	var totalAccepted int64
	var totalDropped int64

	// Dimensions tracking per tenant
	// tenantID -> agent -> count
	agentReceived := make(map[string]map[string]int64)
	agentAccepted := make(map[string]map[string]int64)
	agentDropped := make(map[string]map[string]int64)

	logSourceReceived := make(map[string]map[string]int64)
	logSourceAccepted := make(map[string]map[string]int64)
	logSourceDropped := make(map[string]map[string]int64)

	tenantReceivedTotal := make(map[string]int64)
	tenantAcceptedTotal := make(map[string]int64)

	// Filter logs and compute metrics
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

		if agentReceived[tenantID] == nil {
			agentReceived[tenantID] = make(map[string]int64)
			agentAccepted[tenantID] = make(map[string]int64)
			agentDropped[tenantID] = make(map[string]int64)
			logSourceReceived[tenantID] = make(map[string]int64)
			logSourceAccepted[tenantID] = make(map[string]int64)
			logSourceDropped[tenantID] = make(map[string]int64)
		}

		// Count records in this resource log
		var resourceRecordsCount int64 = 0
		for j := 0; j < rl.ScopeLogs().Len(); j++ {
			sl := rl.ScopeLogs().At(j)
			resourceRecordsCount += int64(sl.LogRecords().Len())
		}
		tenantReceivedTotal[tenantID] += resourceRecordsCount
		totalReceived += resourceRecordsCount

		// Rate limit check
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
		nowFloat := float64(nowMs) / 1000.0

		acceptedCountIntf, err := tokenBucketScript.Run(ctx, p.client, keys, nowFloat, resourceRecordsCount).Result()
		var acceptedCount int64 = 0
		if err == nil {
			acceptedCount = acceptedCountIntf.(int64)
		}

		tenantAcceptedTotal[tenantID] += acceptedCount
		totalAccepted += acceptedCount
		droppedCount := resourceRecordsCount - acceptedCount
		totalDropped += droppedCount

		agentReceived[tenantID][agentName] += resourceRecordsCount
		agentAccepted[tenantID][agentName] += acceptedCount
		agentDropped[tenantID][agentName] += droppedCount

		// Now filter the actual logs
		var currentAccepted int64 = 0
		for j := 0; j < rl.ScopeLogs().Len(); j++ {
			sl := rl.ScopeLogs().At(j)
			
			// We need to build a new log records slice containing only accepted logs
			newRecords := sl.LogRecords()
			newRecords.RemoveIf(func(lr plog.LogRecord) bool {
				logSource := "unknown"
				if val, ok := lr.Attributes().Get("log.source"); ok {
					logSource = val.Str()
				}
				
				logSourceReceived[tenantID][logSource]++

				if currentAccepted < acceptedCount {
					currentAccepted++
					logSourceAccepted[tenantID][logSource]++
					return false // keep
				} else {
					logSourceDropped[tenantID][logSource]++
					return true // drop
				}
			})
		}
	}

	// Update Redis Metrics
	pipe := p.client.Pipeline()
	for tenantID, received := range tenantReceivedTotal {
		accepted := tenantAcceptedTotal[tenantID]
		dropped := received - accepted

		// Global counters
		incrementWindowCounters(pipe, tenantID, "1m", window1m, received, accepted, dropped, 48*60*60)
		incrementWindowCounters(pipe, tenantID, "5m", window5m, received, accepted, dropped, 7*24*60*60)
		incrementWindowCounters(pipe, tenantID, "15m", window15m, received, accepted, dropped, 14*24*60*60)
		incrementWindowCounters(pipe, tenantID, "1d", window1d, received, accepted, dropped, 90*24*60*60)

		// Dimension Counters - Agent
		for agent, rec := range agentReceived[tenantID] {
			acc := agentAccepted[tenantID][agent]
			drp := agentDropped[tenantID][agent]
			incrementDimension(pipe, tenantID, "agent", agent, window1m, window5m, window15m, rec, acc, drp)
		}

		// Dimension Counters - Log Source
		for ls, rec := range logSourceReceived[tenantID] {
			acc := logSourceAccepted[tenantID][ls]
			drp := logSourceDropped[tenantID][ls]
			incrementDimension(pipe, tenantID, "logsource", ls, window1m, window5m, window15m, rec, acc, drp)
		}
	}
	_, err := pipe.Exec(ctx)
	if err != nil {
		p.logger.Error("Failed to update redis metrics", zap.Error(err))
	}

	// Pass accepted logs to the next consumer
	if totalAccepted > 0 {
		// Clean up empty ScopeLogs and ResourceLogs
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

	ttl1m := 48 * time.Hour
	ttl5m := 7 * 24 * time.Hour
	ttl15m := 14 * 24 * time.Hour
	ttls := map[string]time.Duration{
		"1m":  ttl1m,
		"5m":  ttl5m,
		"15m": ttl15m,
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

// Time Utils

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

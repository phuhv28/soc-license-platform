package main

import (
	"context"

	"github.com/segmentio/kafka-go"
)

type kafkaProducer struct {
	writer *kafka.Writer
}

func newKafkaProducer(brokers string) (*kafkaProducer, error) {
	w := &kafka.Writer{
		Addr:     kafka.TCP(brokers),
		Topic:    "soc-logs",
		Balancer: &kafka.LeastBytes{},
	}
	return &kafkaProducer{writer: w}, nil
}

func (k *kafkaProducer) Produce(ctx context.Context, tenantID string, data []byte) error {
	msg := kafka.Message{
		Key:   []byte(tenantID),
		Value: data,
	}
	return k.writer.WriteMessages(ctx, msg)
}

func (k *kafkaProducer) Close() error {
	return k.writer.Close()
}

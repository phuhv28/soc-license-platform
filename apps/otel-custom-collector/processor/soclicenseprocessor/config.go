package soclicenseprocessor

type Config struct {
	RedisURL string `mapstructure:"redis_url"`
}

func (c *Config) Validate() error {
	return nil
}

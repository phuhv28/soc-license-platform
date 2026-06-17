console.log("Event producer started");

const tenantId = process.env.TENANT_ID || "tenant-a";
const eps = Number(process.env.EPS || 100);
const batchSize = Number(process.env.BATCH_SIZE || 50);
const collectorUrl = process.env.COLLECTOR_URL || "http://localhost:8081";

console.log("Config:");
console.log({
  tenantId,
  eps,
  batchSize,
  collectorUrl,
});

setInterval(() => {
  console.log("Event producer heartbeat", {
    tenantId,
    eps,
    batchSize,
    collectorUrl,
    timestamp: new Date().toISOString(),
  });
}, 5000);
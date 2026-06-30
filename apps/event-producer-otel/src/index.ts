import axios from "axios";
import { v4 as uuidv4 } from "uuid";

// ── Configuration ───────────────────────────────────────────────────────

const tenantIds = (process.env.TENANT_IDS || process.env.TENANT_ID || "tenant-a")
  .split(",")
  .map((id) => id.trim())
  .filter((id) => id.length > 0);

const eps = Number(process.env.EPS || 100);
const batchSize = Number(process.env.BATCH_SIZE || 50);
const collectorUrl = process.env.COLLECTOR_URL || "http://localhost:4318";
const batchEndpoint = `${collectorUrl}/api/v1/logs`;

// Calculate interval: send batchSize events per interval to achieve target EPS
// interval = (batchSize / eps) * 1000 ms
const intervalMs = Math.max(Math.round((batchSize / eps) * 1000), 100);

const EVENT_TYPES = [
  "firewall.connection",
  "ids.alert",
  "auth.login",
  "auth.logout",
  "file.access",
  "network.dns",
  "endpoint.process",
  "email.received",
  "web.request",
  "system.audit",
];

// ── Stats Tracking ──────────────────────────────────────────────────────

interface TenantStats {
  totalSent: number;
  totalAccepted: number;
  totalDropped: number;
  errors: number;
}

const stats: Record<string, TenantStats> = {};
for (const tid of tenantIds) {
  stats[tid] = {
    totalSent: 0,
    totalAccepted: 0,
    totalDropped: 0,
    errors: 0,
  };
}

// ── Event Generation ────────────────────────────────────────────────────

function rand(min: number, max: number): number {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function generateFlatBatch(size: number) {
  const events = [];
  
  for (let i = 0; i < size; i++) {
    const eventType = EVENT_TYPES[Math.floor(Math.random() * EVENT_TYPES.length)];
    
    const payload = {
      sourceIp: `10.${rand(0, 255)}.${rand(0, 255)}.${rand(1, 254)}`,
      destinationIp: `192.168.${rand(0, 255)}.${rand(1, 254)}`,
      port: rand(1, 65535),
      protocol: ["TCP", "UDP", "ICMP"][rand(0, 2)],
      severity: ["low", "medium", "high", "critical"][rand(0, 3)],
      message: `Event from ${eventType}`,
    };

    events.push({
      "log.source": eventType,
      "event.id": uuidv4(),
      "timestamp": new Date().toISOString(),
      ...payload
    });
  }

  return events;
}

// ── Batch Sender ────────────────────────────────────────────────────────

async function sendBatch(tenantId: string): Promise<void> {
  const batch = generateFlatBatch(batchSize);
  const tenantStat = stats[tenantId];
  const batchAgent = `agent-${rand(1, 5)}`;

  try {
    const response = await axios.post(batchEndpoint, batch, {
      headers: { 
        "Content-Type": "application/json",
        "X-Tenant-ID": tenantId,
        "X-Agent-Name": batchAgent
      },
      timeout: 5000,
    });

    // Custom Intake Server returns 202 Accepted.
    // For simplicity, we log sent.
    const sent = batchSize;
    let accepted = sent;
    let dropped = 0;

    tenantStat.totalSent += sent;
    tenantStat.totalAccepted += accepted;
    tenantStat.totalDropped += dropped;
    
  } catch (error: any) {
    tenantStat.errors++;
    if (error.code === "ECONNREFUSED") {
      // Collector not ready yet, silently retry
    } else {
      console.error(
        `[${tenantId}] Error sending batch: ${error.message || error}`
      );
    }
  }
}

// ── Stats Reporter ──────────────────────────────────────────────────────

function reportStats(): void {
  console.log("\n═══ OTLP Event Producer Stats ═══");
  for (const tid of tenantIds) {
    const s = stats[tid];
    console.log(
      `  [${tid}] sent=${s.totalSent} accepted=${s.totalAccepted} dropped=${s.totalDropped} errors=${s.errors}`
    );
  }
  console.log("═════════════════════════════════\n");
}

// ── Main ────────────────────────────────────────────────────────────────

console.log("╔══════════════════════════════════════════╗");
console.log("║      SOC OTLP Event Producer Started     ║");
console.log("╚══════════════════════════════════════════╝");
console.log("Config:", {
  tenantIds,
  eps,
  batchSize,
  intervalMs,
  collectorUrl,
});

// Start sending batches for each tenant
for (const tid of tenantIds) {
  setInterval(() => sendBatch(tid), intervalMs);
}

// Report stats every 10 seconds
setInterval(reportStats, 10_000);

// Initial delay before first stats report
setTimeout(reportStats, 5_000);

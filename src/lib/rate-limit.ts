// src/lib/rate-limit.ts

let kvModule: any;

async function getKV() {
  if (kvModule === undefined) {
    try {
      kvModule = await import("@vercel/kv").then(m => m.kv);
    } catch {
      kvModule = null;
    }
  }
  return kvModule;
}

// In‑memory fallback Map (resets on cold start, fine for personal use)
const memory = new Map<string, { count: number; reset: number }>();

export async function rateLimit(
  key: string,
  limit: number,
  windowMs: number
): Promise<boolean> {
  const kv = await getKV();

  if (kv) {
    // Use real KV (incr + expire)
    const now = Date.now();
    const windowKey = `rate:${key}:${Math.floor(now / windowMs)}`;
    const current = await kv.incr(windowKey);
    if (current === 1) {
      await kv.expire(windowKey, Math.ceil(windowMs / 1000));
    }
    return current <= limit;
  }

  // Fallback to in‑memory when KV is not available
  const now = Date.now();
  const entry = memory.get(key) || { count: 0, reset: now + windowMs };
  if (now > entry.reset) {
    entry.count = 1;
    entry.reset = now + windowMs;
  } else {
    entry.count++;
  }
  memory.set(key, entry);
  return entry.count <= limit;
}

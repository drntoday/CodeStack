import { kv } from "@vercel/kv";

export async function rateLimit(key: string, limit: number, windowMs: number): Promise<boolean> {
  const now = Date.now();
  const windowKey = `rate:${key}:${Math.floor(now / windowMs)}`;
  const current = await kv.incr(windowKey);
  if (current === 1) {
    await kv.expire(windowKey, Math.ceil(windowMs / 1000)); // set TTL
  }
  return current <= limit;
}

import { randomUUID as uuid } from "crypto";

let kvModule: any;
async function getKV() {
  if (kvModule === undefined) {
    try { kvModule = await import("@vercel/kv").then(m => m.kv); } catch { kvModule = null; }
  }
  return kvModule;
}

const memory = new Map<string, any>();

export async function createJob(): Promise<string> {
  const id = uuid();
  const job = { id, status: "running", result: null, error: null };
  const k = `job:${id}`;
  const kv = await getKV();
  if (kv) await kv.set(k, job);
  else memory.set(k, job);
  return id;
}

export async function updateJob(id: string, updates: Record<string, any>) {
  const k = `job:${id}`;
  const kv = await getKV();
  const current = kv ? (await kv.get(k)) : memory.get(k);
  if (!current) return;
  const updated = { ...current, ...updates };
  if (kv) await kv.set(k, updated);
  else memory.set(k, updated);
}

export async function getJob(id: string): Promise<any | null> {
  const k = `job:${id}`;
  const kv = await getKV();
  return kv ? (await kv.get(k)) : memory.get(k) || null;
}

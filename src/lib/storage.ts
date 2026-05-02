import { kv } from "@vercel/kv";

export async function getConversation(key: string) {
  const messages = await kv.get(key);
  return messages || [];
}

export async function setConversation(key: string, messages: unknown[]) {
  await kv.set(key, messages);
}

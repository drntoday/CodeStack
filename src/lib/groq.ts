import Groq from "groq-sdk";

// Model mapping per task type (unchanged)
const MODEL_MAP: Record<string, { primary: string; fallback: string }> = {
  chat:           { primary: "llama-3.1-8b-instant",          fallback: "qwen/qwen3-32b" },
  refactor:       { primary: "meta-llama/llama-4-scout-17b-16e-instruct", fallback: "qwen/qwen3-32b" },
  tests:          { primary: "qwen/qwen3-32b",                fallback: "llama-3.3-70b-versatile" },
  audit:          { primary: "qwen/qwen3-32b",                fallback: "llama-3.3-70b-versatile" },
  architecture:   { primary: "llama-3.1-8b-instant",          fallback: "qwen/qwen3-32b" },
  docs:           { primary: "meta-llama/llama-4-scout-17b-16e-instruct", fallback: "llama-3.3-70b-versatile" },
  search:         { primary: "llama-3.1-8b-instant",          fallback: "qwen/qwen3-32b" },
  commitMessage:  { primary: "llama-3.1-8b-instant",          fallback: "qwen/qwen3-32b" },
  ci:             { primary: "llama-3.1-8b-instant",          fallback: "qwen/qwen3-32b" },
};

// Simple in‑memory rate‑limit tracker (unchanged)
const usageCounters: Record<string, number> = {};

export async function queryGroq(
  taskType: string,
  messages: Array<{ role: string; content: string }>,
  options?: { maxTokens?: number; temperature?: number }
): Promise<string> {
  const apiKey = process.env.GROQ_API_KEY;
  if (!apiKey) {
    throw new Error("GROQ_API_KEY is not set");
  }

  // Groq client is now created inside the function, not at the top level
  const groq = new Groq({ apiKey });

  const models = MODEL_MAP[taskType] || MODEL_MAP.chat;

  const tryModel = async (model: string) => {
    // Basic rate‑limit guard
    const key = `${model}-${new Date().getMinutes()}`;
    usageCounters[key] = (usageCounters[key] || 0) + 1;

    const groqMessages = messages.map((m) => ({
      role: m.role as any,
      content: m.content,
    }));

    const completion = await groq.chat.completions.create({
      model,
      messages: groqMessages,
      max_tokens: options?.maxTokens ?? 2048,
      temperature: options?.temperature ?? 0.7,
    });

    return completion.choices[0]?.message?.content || "";
  };

  // Try primary model, if 429 fallback to secondary
  try {
    return await tryModel(models.primary);
  } catch (err: any) {
    if (err?.status === 429 || err?.message?.includes("rate_limit")) {
      console.warn(`Rate limited on ${models.primary}, trying ${models.fallback}`);
      return await tryModel(models.fallback);
    }
    throw err;
  }
}

/**
 * Query Groq with a specific model (for custom use cases like fast suggestions)
 */
export async function queryGroqWithModel(
  model: string,
  messages: Array<{ role: string; content: string }>,
  options?: { maxTokens?: number; temperature?: number }
): Promise<string> {
  const apiKey = process.env.GROQ_API_KEY;
  if (!apiKey) {
    throw new Error("GROQ_API_KEY is not set");
  }

  const groq = new Groq({ apiKey });

  const key = `${model}-${new Date().getMinutes()}`;
  usageCounters[key] = (usageCounters[key] || 0) + 1;

  const groqMessages = messages.map((m) => ({
    role: m.role as any,
    content: m.content,
  }));

  const completion = await groq.chat.completions.create({
    model,
    messages: groqMessages,
    max_tokens: options?.maxTokens ?? 1024,
    temperature: options?.temperature ?? 0.7,
  });

  return completion.choices[0]?.message?.content || "";
}

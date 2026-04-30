export const config = { runtime: 'edge' };

export default async function handler(req) {
  if (req.method !== 'POST') {
    return new Response('Method Not Allowed', { status: 405 });
  }

  const { prompt } = await req.json();
  if (!prompt) {
    return new Response(JSON.stringify({ error: 'Prompt required' }), { status: 400 });
  }

  const deepseekRes = await fetch('https://api.deepseek.com/v1/chat/completions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${process.env.DEEPSEEK_API_KEY}`,
    },
    body: JSON.stringify({
      model: 'deepseek-chat',
      messages: [
        { role: 'system', content: 'You are a coding expert. Return only raw code, no markdown fences.' },
        { role: 'user', content: prompt },
      ],
      temperature: 0.2,
      max_tokens: 2048,
    }),
  });

  const data = await deepseekRes.json();
  const code = data.choices?.[0]?.message?.content?.trim() ?? 'No code returned.';
  return new Response(JSON.stringify({ code }), {
    headers: { 'Content-Type': 'application/json' },
  });
}

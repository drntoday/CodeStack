const Groq = require("groq-sdk");
const fs = require("fs");
const path = require("path");

const plan = JSON.parse(process.argv[2]);
const groq = new Groq({ apiKey: process.env.GROQ_API_KEY });

async function refactorFile(file, instruction) {
  if (!fs.existsSync(file)) return;
  const content = fs.readFileSync(file, "utf-8");
  console.log(`Refactoring ${file}...`);
  const prompt = `File: ${file}
${content}

Task: ${instruction}

Provide ONLY the new file content inside a code block.`;
  const completion = await groq.chat.completions.create({
    model: "meta-llama/llama-4-scout-17b-16e-instruct", // handle large context
    messages: [{ role: "user", content: prompt }],
    max_tokens: 4096,
  });
  const text = completion.choices[0]?.message?.content || "";
  const match = text.match(/```[\s\S]*?\n([\s\S]*?)\n```/);
  const newContent = match ? match[1] : text;
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, newContent);
}

(async () => {
  for (const { file, instruction } of plan) {
    await refactorFile(file, instruction);
    await new Promise(r => setTimeout(r, 5000)); // rate limit safety
  }
})().catch(e => { console.error(e); process.exit(1); });

const { GoogleGenerativeAI } = require("@google/generative-ai");
const fs = require("fs");
const path = require("path");

const plan = JSON.parse(process.argv[2]);
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });

async function refactorFile(file, instruction) {
  if (!fs.existsSync(file)) return;
  const content = fs.readFileSync(file, "utf-8");
  console.log(`Refactoring ${file}...`);
  const prompt = `File: ${file}\nOriginal content:\n\`\`\`\n${content}\n\`\`\`\n\nTask: ${instruction}\n\nProvide ONLY the new file content inside a code block. No explanations.`;
  const result = await model.generateContent(prompt);
  const response = await result.response;
  const text = response.text();
  // Extract code block
  const match = text.match(/```[\s\S]*?\n([\s\S]*?)\n```/);
  const newContent = match ? match[1] : text;
  // Ensure directory exists
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, newContent);
}

async function main() {
  for (const { file, instruction } of plan) {
    await refactorFile(file, instruction);
    // Respect rate limit (Gemini free: 15 RPM, ~4s per request)
    await new Promise(r => setTimeout(r, 5000));
  }
}

main().catch(e => {
  console.error(e);
  process.exit(1);
});

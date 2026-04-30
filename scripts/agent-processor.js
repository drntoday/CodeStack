const { GoogleGenerativeAI } = require("@google/generative-ai");
const fs = require("fs");
const path = require("path");

async function run() {
  const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
  const model = genAI.getGenerativeModel({ model: "gemini-1.5-pro" });

  // 1. Parse the task sent from Vercel
  const payload = JSON.parse(process.env.TASK_PAYLOAD);
  const { task_description, target_files } = payload;

  console.log(`Starting task: ${task_description}`);

  // 2. Read the content of the target files to give Gemini context
  let codebaseContext = "";
  target_files.forEach(file => {
    if (fs.existsSync(file)) {
      const content = fs.readFileSync(file, "utf8");
      codebaseContext += `\n--- FILE: ${file} ---\n${content}\n`;
    }
  });

  // 3. The Prompt: Asking Gemini to return ONLY the updated code
  const prompt = `
    You are an expert software engineer. 
    Task: ${task_description}
    
    Current Code Context:
    ${codebaseContext}

    Instructions:
    Return the updated code for the files. 
    Format your response as a JSON object where keys are filenames and values are the full updated file content.
    Example: {"path/to/file.js": "new content here"}
    DO NOT include markdown formatting like \`\`\`json. Return raw JSON text.
  `;

  const result = await model.generateContent(prompt);
  const responseText = result.response.text();

  try {
    // 4. Parse Gemini's response and write to disk
    const updates = JSON.parse(responseText.trim());
    
    for (const [filePath, newContent] of Object.entries(updates)) {
      const fullPath = path.resolve(process.cwd(), filePath);
      
      // Create directory if it doesn't exist
      fs.mkdirSync(path.dirname(fullPath), { recursive: true });
      
      fs.writeFileSync(fullPath, newContent, "utf8");
      console.log(`Successfully updated: ${filePath}`);
    }
  } catch (e) {
    console.error("Failed to parse Gemini response. Ensure it returned valid JSON.");
    console.error("Raw Response:", responseText);
    process.exit(1);
  }
}

run();

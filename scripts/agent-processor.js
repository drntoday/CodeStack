const { GoogleGenerativeAI } = require("@google/generative-ai");
const fs = require("fs");
const path = require("path");
const { execSync } = require("child_process");

async function run() {
  const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
  // Using 1.5 Pro for its massive context and superior reasoning
  const model = genAI.getGenerativeModel({ 
    model: "gemini-1.5-pro",
    generationConfig: { responseMimeType: "application/json" } 
  });

  const payload = JSON.parse(process.env.TASK_PAYLOAD);
  const { task_description, target_files } = payload;

  let currentCodeContext = "";
  target_files.forEach(file => {
    if (fs.existsSync(file)) {
      currentCodeContext += `\nFILE: ${file}\nCONTENT:\n${fs.readFileSync(file, "utf8")}\n`;
    }
  });

  let task = task_description;
  let success = false;
  let attempts = 0;

  while (attempts < 2 && !success) {
    console.log(`--- Attempt ${attempts + 1} ---`);
    
    const prompt = `
      You are an elite AI engineer. 
      TASK: ${task}
      CONTEXT: ${currentCodeContext}
      
      Respond with a JSON object where keys are file paths and values are the NEW content.
      Schema: { "filepath": "content" }
    `;

    try {
      const result = await model.generateContent(prompt);
      const updates = JSON.parse(result.response.text());

      // Apply changes
      for (const [filePath, content] of Object.entries(updates)) {
        fs.mkdirSync(path.dirname(filePath), { recursive: true });
        fs.writeFileSync(filePath, content, "utf8");
        console.log(`Updated: ${filePath}`);
      }

      // Self-Healing: Check if the code actually works
      try {
        console.log("Running verification...");
        // This runs 'npm test' or a simple node check
        execSync("npm test", { stdio: "inherit" });
        success = true;
        console.log("✅ Verification successful.");
      } catch (err) {
        console.error("❌ Build/Test failed. Asking Gemini to fix it...");
        task = `The previous attempt failed with this error: ${err.message}. Please fix the code. Original task: ${task_description}`;
        attempts++;
      }
    } catch (error) {
      console.error("Critical error in Agent Loop:", error);
      process.exit(1);
    }
  }
}

run();

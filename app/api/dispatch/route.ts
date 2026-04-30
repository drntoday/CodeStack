import { GoogleGenerativeAI } from "@google/generative-ai";
import { NextResponse } from "next/server";

export async function POST(req: Request) {
  try {
    const { fileList, projectGoal } = await req.json();

    if (!process.env.GEMINI_API_KEY) {
      return NextResponse.json({ error: "API Key missing" }, { status: 500 });
    }

    const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
    const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash" });

    const prompt = `
      You are a Lead Architect. Analyze this file list from the "CodeStack" project:
      FILES: ${JSON.stringify(fileList)}
      PROJECT GOAL: ${projectGoal || "General Software Development"}

      Create a structured Markdown Documentation including:
      1. ## Project Overview (What this does)
      2. ## Architecture Map (Explain the folder structure)
      3. ## Key Logic Flows (How data moves between files)
      
      Keep it professional, concise, and helpful for a new developer onboarding.
    `;

    const result = await model.generateContent(prompt);
    const text = result.response.text();

    return NextResponse.json({ markdown: text });
  } catch (error: any) {
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}

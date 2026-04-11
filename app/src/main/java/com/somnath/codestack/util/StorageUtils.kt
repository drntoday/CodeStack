package com.somnath.codestack.util

import android.content.Context

// --- PERSISTENCE HELPERS ---

fun saveApiKey(context: Context, key: String) =
    context.getSharedPreferences("cs_prefs", 0).edit().putString("gemini_key", key).apply()

fun getApiKey(context: Context) =
    context.getSharedPreferences("cs_prefs", 0).getString("gemini_key", "") ?: ""

fun saveGitHubToken(context: Context, token: String) =
    context.getSharedPreferences("cs_prefs", 0).edit().putString("github_token", token).apply()

fun getGitHubToken(context: Context) =
    context.getSharedPreferences("cs_prefs", 0).getString("github_token", "") ?: ""

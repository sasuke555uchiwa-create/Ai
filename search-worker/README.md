# SASUKE AI — Cloudflare Worker Search Backend (Tavily Proxy)

This Cloudflare Worker provides a secure, rate-limited proxy to the [Tavily Search API](https://tavily.com) for the SASUKE AI Android app. It replaces Gemini's native Google Search grounding (which requires paid Gemini billing) with Tavily's free search tier (1,000 free searches/month).

---

## Architecture & Security Highlights

1. **Zero Key Exposure**: `TAVILY_API_KEY` is stored strictly as an encrypted Cloudflare Secret. The Android app NEVER receives, stores, or sees the API key.
2. **Quota Conservation**: Restricts queries to `search_depth: "basic"` and maximum 5 results with bounded snippets to preserve the free monthly quota.
3. **Abuse Protection & Rate Limiting**: Built-in 30 req/min per IP rate limiter prevents abusive query flooding.
4. **Token Verification**: Supports Firebase Auth ID Token verification or an optional shared client secret.
5. **Untrusted Content Bounding**: Returns normalized, sanitized JSON containing only original titles, URLs, and snippet texts.

---

## Step-by-Step Setup and Deployment

### 1. Get a Free Tavily API Key
1. Go to [https://app.tavily.com](https://app.tavily.com) and create a free account.
2. Under **Overview / API Keys**, copy your free API key (`tvly-...`).

### 2. Install Wrangler CLI
Make sure you have Node.js 18+ installed on your computer.

```bash
# In the search-worker directory:
cd search-worker

# Install dependencies:
npm install
```

### 3. Log into Cloudflare
```bash
npx wrangler login
```
Follow the browser prompt to authorize Wrangler with your free Cloudflare account.

### 4. Configure Secrets (CRITICAL)
Store your Tavily API key securely using Cloudflare Secrets:

```bash
npx wrangler secret put TAVILY_API_KEY
```
When prompted, paste your Tavily API key (`tvly-...`).

*(Optional)* If you want to restrict the worker to requests from your Firebase project:
```bash
npx wrangler secret put FIREBASE_PROJECT_ID
# Enter your Firebase Project ID (e.g. from google-services.json)
```

*(Optional)* If you want a shared client secret:
```bash
npx wrangler secret put APP_CLIENT_SECRET
# Enter any secure random string
```

### 5. Deploy the Worker
Deploy the worker to your Cloudflare Workers subdomain:

```bash
npx wrangler deploy
```

Upon successful deployment, Wrangler will output your worker URL:
```
Deployed sasuke-search-worker
https://sasuke-search-worker.<your-subdomain>.workers.dev
```

### 6. Test Your Deployed Worker
Verify that the worker is live:

```bash
# Health check:
curl https://sasuke-search-worker.<your-subdomain>.workers.dev/health

# Search test:
curl -X POST https://sasuke-search-worker.<your-subdomain>.workers.dev/search \
  -H "Content-Type: application/json" \
  -d '{"query": "Latest advances in AI"}'
```

Expected response format:
```json
{
  "query": "Latest advances in AI",
  "provider": "tavily",
  "count": 5,
  "results": [
    {
      "title": "...",
      "url": "https://...",
      "snippet": "...",
      "published_date": "2026-..."
    }
  ]
}
```

---

## 7. Configure in SASUKE AI Android App

1. Open **SASUKE AI** on your device.
2. Tap **Settings** (or the Settings icon in the drawer/top bar).
3. Under **Web Search Provider (Cloudflare Worker + Tavily)**:
   - Enter your worker URL: `https://sasuke-search-worker.<your-subdomain>.workers.dev`
   - Tap **Save Search Worker URL**.
4. In the Chat screen, toggle **Search ON**.
5. Ask any current-facts question (e.g., *"What are the latest space exploration milestones?"*).
6. The app will fetch live Tavily results via your Worker, synthesize an answer with Gemini, and display verifiable clickable source links!

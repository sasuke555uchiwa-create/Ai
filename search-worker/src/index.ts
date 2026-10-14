/**
 * SASUKE AI - Cloudflare Worker Search Backend
 * Integrates Tavily Search API with SASUKE AI Android App
 */

export interface Env {
  // Secret: Set via `npx wrangler secret put TAVILY_API_KEY`
  TAVILY_API_KEY?: string;

  // Optional: Shared client token for abuse protection
  APP_CLIENT_SECRET?: string;

  // Optional: Firebase project ID for verifying Firebase ID tokens
  FIREBASE_PROJECT_ID?: string;

  // Optional: Rate limit requests per minute per IP (default: 30)
  RATE_LIMIT_PER_MINUTE?: string;
}

interface SearchRequestBody {
  query?: string;
  max_results?: number;
}

interface NormalizedSearchResult {
  title: string;
  url: string;
  snippet: string;
  published_date: string | null;
}

// In-memory sliding window rate limiter per IP
const ipRequestCounts = new Map<string, { count: number; resetTime: number }>();

function checkRateLimit(ip: string, limitPerMinute: number = 30): boolean {
  const now = Date.now();
  const record = ipRequestCounts.get(ip);

  if (!record || now > record.resetTime) {
    ipRequestCounts.set(ip, { count: 1, resetTime: now + 60_000 });
    return true;
  }

  if (record.count >= limitPerMinute) {
    return false;
  }

  record.count += 1;
  return true;
}

// Clean up stale rate limit entries periodically
function cleanupRateLimitCache() {
  const now = Date.now();
  for (const [ip, record] of ipRequestCounts.entries()) {
    if (now > record.resetTime) {
      ipRequestCounts.delete(ip);
    }
  }
}

/**
 * Basic Firebase ID Token verification (JWT header/payload checks)
 * Validates issuer, audience, and expiration.
 */
function verifyFirebaseIdToken(token: string, projectId: string): boolean {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return false;

    // Decode JWT payload (part 1)
    const payloadJson = atob(parts[1].replace(/-/g, "+").replace(/_/g, "/"));
    const payload = JSON.parse(payloadJson);

    const now = Math.floor(Date.now() / 1000);

    // Validate claims
    const expectedIss = `https://securetoken.google.com/${projectId}`;
    if (payload.iss !== expectedIss) return false;
    if (payload.aud !== projectId) return false;
    if (typeof payload.exp !== "number" || payload.exp < now) return false;
    if (!payload.sub || typeof payload.sub !== "string") return false;

    return true;
  } catch {
    return false;
  }
}

function jsonResponse(data: unknown, status: number = 200, headers: HeadersInit = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization, X-App-Client, X-App-Token",
      ...headers,
    },
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    // Handle CORS preflight
    if (request.method === "OPTIONS") {
      return jsonResponse({ ok: true }, 204);
    }

    // Health check endpoint
    if (url.pathname === "/" || url.pathname === "/health") {
      return jsonResponse({
        status: "ok",
        service: "SASUKE AI Tavily Search Proxy",
        version: "1.0.0",
        configured: Boolean(env.TAVILY_API_KEY && env.TAVILY_API_KEY.length > 5),
        rate_limit_per_minute: parseInt(env.RATE_LIMIT_PER_MINUTE || "30", 10),
      });
    }

    // Search endpoint
    if (url.pathname === "/search") {
      if (request.method !== "POST") {
        return jsonResponse(
          { error: "Method not allowed. Use POST /search", code: 405 },
          405
        );
      }

      // Client IP detection & Rate Limiting
      const clientIp = request.headers.get("cf-connecting-ip") || "unknown-ip";
      const limit = parseInt(env.RATE_LIMIT_PER_MINUTE || "30", 10);
      cleanupRateLimitCache();

      if (!checkRateLimit(clientIp, limit)) {
        return jsonResponse(
          {
            error: "Too many requests. Rate limit is 30 requests per minute.",
            code: 429,
            retry_after_seconds: 60,
          },
          429,
          { "Retry-After": "60" }
        );
      }

      // Security Check: Optional Client Secret or Firebase Token
      const authHeader = request.headers.get("Authorization") || "";
      const appToken = request.headers.get("X-App-Token") || "";

      if (env.APP_CLIENT_SECRET) {
        const token = authHeader.replace(/^Bearer\s+/i, "") || appToken;
        if (token !== env.APP_CLIENT_SECRET) {
          return jsonResponse(
            { error: "Unauthorized: Invalid application client secret", code: 401 },
            401
          );
        }
      } else if (env.FIREBASE_PROJECT_ID && authHeader.startsWith("Bearer ")) {
        const idToken = authHeader.substring(7).trim();
        if (!verifyFirebaseIdToken(idToken, env.FIREBASE_PROJECT_ID)) {
          return jsonResponse(
            { error: "Unauthorized: Invalid or expired Firebase authentication token", code: 401 },
            401
          );
        }
      }

      // Request Body Validation
      let body: SearchRequestBody;
      try {
        body = (await request.json()) as SearchRequestBody;
      } catch {
        return jsonResponse(
          { error: "Invalid JSON request body. Expected { query: string }", code: 400 },
          400
        );
      }

      const query = (body.query || "").trim();
      if (!query || query.length < 2) {
        return jsonResponse(
          { error: "Search query must be at least 2 characters long", code: 400 },
          400
        );
      }

      if (query.length > 400) {
        return jsonResponse(
          { error: "Search query exceeds maximum length of 400 characters", code: 400 },
          400
        );
      }

      // Check Tavily API Key in Worker secrets
      const tavilyApiKey = env.TAVILY_API_KEY?.trim();
      if (!tavilyApiKey) {
        return jsonResponse(
          {
            error: "Tavily API key is not configured in Worker secrets. Run: npx wrangler secret put TAVILY_API_KEY",
            code: 503,
            is_unconfigured: true,
          },
          503
        );
      }

      // Bounded results to conserve free quota (default: 5 results, search_depth: basic)
      const maxResults = Math.min(Math.max(body.max_results || 5, 1), 5);

      // Call Tavily Search API with 12-second timeout
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 12_000);

      try {
        const tavilyResponse = await fetch("https://api.tavily.com/search", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${tavilyApiKey}`,
          },
          body: JSON.stringify({
            query,
            search_depth: "basic",
            max_results: maxResults,
            include_answer: false,
            include_images: false,
            include_raw_content: false,
          }),
          signal: controller.signal,
        });

        clearTimeout(timeoutId);

        if (!tavilyResponse.isSuccessful && !tavilyResponse.ok) {
          const status = tavilyResponse.status;
          const errorText = await tavilyResponse.text().catch(() => "");

          if (status === 401 || status === 403) {
            return jsonResponse(
              {
                error: "Tavily API Key is invalid or expired. Please check your TAVILY_API_KEY secret.",
                code: 502,
                upstream_status: status,
              },
              502
            );
          }

          if (status === 429) {
            return jsonResponse(
              {
                error: "Tavily free monthly quota exhausted or rate limit exceeded (HTTP 429).",
                code: 429,
                is_quota_exhausted: true,
              },
              429
            );
          }

          return jsonResponse(
            {
              error: `Tavily search provider returned error: ${errorText || "HTTP " + status}`,
              code: 502,
              upstream_status: status,
            },
            502
          );
        }

        const tavilyData = (await tavilyResponse.json()) as {
          results?: Array<{
            title?: string;
            url?: string;
            content?: string;
            published_date?: string;
          }>;
        };

        // Normalize and bounded snippet length (max 400 chars per snippet)
        const rawResults = tavilyData.results || [];
        const normalizedResults: NormalizedSearchResult[] = rawResults
          .filter((item) => item.url && item.url.startsWith("http"))
          .slice(0, 5)
          .map((item) => ({
            title: (item.title || "Web Result").trim(),
            url: item.url!.trim(),
            snippet: (item.content || "").trim().slice(0, 450),
            published_date: item.published_date || null,
          }));

        return jsonResponse({
          query,
          provider: "tavily",
          count: normalizedResults.length,
          results: normalizedResults,
        });
      } catch (err: unknown) {
        clearTimeout(timeoutId);
        const error = err as Error;

        if (error.name === "AbortError") {
          return jsonResponse(
            { error: "Search request timed out after 12 seconds", code: 504 },
            504
          );
        }

        return jsonResponse(
          {
            error: `Network error connecting to search provider: ${error.message}`,
            code: 502,
          },
          502
        );
      }
    }

    return jsonResponse({ error: "Not found", code: 404 }, 404);
  },
};

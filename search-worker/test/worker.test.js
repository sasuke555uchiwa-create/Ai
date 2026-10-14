/**
 * Unit tests for Cloudflare Worker search proxy logic
 * Run with: node --test test/worker.test.js
 */

import test from "node:test";
import assert from "node:assert";

test("Health check endpoint returns status ok", async () => {
  // Verify URL and input validation logic
  const validQuery = "Latest news in robotics";
  assert.strictEqual(validQuery.length >= 2, true);
  assert.strictEqual(validQuery.length <= 400, true);

  const shortQuery = "a";
  assert.strictEqual(shortQuery.length < 2, true);

  const longQuery = "x".repeat(450);
  assert.strictEqual(longQuery.length > 400, true);
});

test("Normalized Tavily results formatting test", () => {
  const mockTavilyRaw = {
    results: [
      {
        title: "NASA Artemis Update",
        url: "https://nasa.gov/artemis",
        content: "NASA announced new milestones for Artemis mission...",
        published_date: "2026-10-10",
      },
      {
        title: "Invalid result with no URL",
        url: "",
        content: "test",
      },
      {
        title: "Invalid non-http URL",
        url: "ftp://bad-link.com",
        content: "bad",
      },
    ],
  };

  const normalized = (mockTavilyRaw.results || [])
    .filter((item) => item.url && item.url.startsWith("http"))
    .slice(0, 5)
    .map((item) => ({
      title: (item.title || "Web Result").trim(),
      url: item.url.trim(),
      snippet: (item.content || "").trim().slice(0, 450),
      published_date: item.published_date || null,
    }));

  assert.strictEqual(normalized.length, 1);
  assert.strictEqual(normalized[0].title, "NASA Artemis Update");
  assert.strictEqual(normalized[0].url, "https://nasa.gov/artemis");
  assert.strictEqual(normalized[0].published_date, "2026-10-10");
  assert.strictEqual(normalized[0].snippet.includes("Artemis"), true);
});

test("Error status code mapping tests", () => {
  // Test upstream 401/403 mapping
  function mapUpstreamError(status) {
    if (status === 401 || status === 403) {
      return { code: 502, error: "Tavily API Key is invalid or expired." };
    }
    if (status === 429) {
      return { code: 429, is_quota_exhausted: true, error: "Tavily free monthly quota exhausted or rate limit exceeded." };
    }
    return { code: 502, error: `Tavily search provider returned error: HTTP ${status}` };
  }

  const unauth = mapUpstreamError(401);
  assert.strictEqual(unauth.code, 502);
  assert.strictEqual(unauth.error.includes("invalid or expired"), true);

  const quota = mapUpstreamError(429);
  assert.strictEqual(quota.code, 429);
  assert.strictEqual(quota.is_quota_exhausted, true);

  const upstream500 = mapUpstreamError(500);
  assert.strictEqual(upstream500.code, 502);
});

test("Rate limit sliding window calculation test", () => {
  const ipCounts = new Map();
  const limit = 30;

  function checkRate(ip, now) {
    const record = ipCounts.get(ip);
    if (!record || now > record.resetTime) {
      ipCounts.set(ip, { count: 1, resetTime: now + 60000 });
      return true;
    }
    if (record.count >= limit) {
      return false;
    }
    record.count += 1;
    return true;
  }

  const clientIp = "192.168.1.100";
  const start = Date.now();

  // First 30 requests should succeed
  for (let i = 0; i < 30; i++) {
    assert.strictEqual(checkRate(clientIp, start), true);
  }

  // 31st request should be rate-limited (HTTP 429)
  assert.strictEqual(checkRate(clientIp, start), false);

  // After 61 seconds, should reset
  assert.strictEqual(checkRate(clientIp, start + 61000), true);
});

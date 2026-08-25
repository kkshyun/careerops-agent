import assert from "node:assert/strict";
import { afterEach, describe, it, mock } from "node:test";
import { formatDate, isClosingSoon, scoreLabel } from "./format.ts";

describe("format helpers", () => {
  afterEach(() => mock.restoreAll());

  it("formats recommendation relevance as a decimal, not a percentage", () => {
    assert.equal(scoreLabel(0.826), "0.83");
    assert.equal(scoreLabel(0), "0.00");
    assert.equal(scoreLabel(0.826).includes("%"), false);
  });

  it("formats a date and handles a missing value", () => {
    assert.match(formatDate("2026-08-26"), /2026/);
    assert.equal(formatDate(null), "—");
  });

  it("includes today and seven days ahead in the closing-soon window", () => {
    mock.method(Date, "now", () => new Date(2026, 7, 26).getTime());
    assert.equal(isClosingSoon("2026-08-26"), true);
    assert.equal(isClosingSoon("2026-09-02"), true);
  });

  it("excludes past dates and dates beyond seven days", () => {
    mock.method(Date, "now", () => new Date(2026, 7, 26).getTime());
    assert.equal(isClosingSoon("2026-08-25"), false);
    assert.equal(isClosingSoon("2026-09-03"), false);
    assert.equal(isClosingSoon(null), false);
  });
});

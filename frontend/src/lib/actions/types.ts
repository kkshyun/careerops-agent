export type ActionResult<T = undefined> =
  | { ok: true; data: T }
  | { ok: false; kind: "demo"; message: string }
  | { ok: false; kind: "error"; message: string; status?: number };

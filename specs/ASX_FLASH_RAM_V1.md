# ASX_FLASH_RAM_V1 — Formal Memory Law

**Status:** Normative  
**Version:** 1.0.0  
**Authority:** ASX_FLASH_RAM_V1  
**Scope:** Deterministic, event-sourced, JSON-defined flash-RAM analog runtime

## 0. Preamble

ASX_FLASH_RAM_V1 defines a software memory fabric where JSON is authoritative, mutation is append-only events, and tiers are deterministic projections from canonical state.

## 1. Canonical JSON Law

- Canonical JSON is required for ABI hashing, event storage, tier state, snapshots, and manifests.
- Canonical form (CJSON-V1): UTF-8, no BOM, LF newlines, lexicographically sorted object keys, no duplicate keys, no trailing commas, no scientific notation, no leading zeros (except `0`), no `-0`, lowercase `true/false/null`.
- Hashing: SHA-256 lowercase hex over exact UTF-8 bytes.

## 2. Event-Sourced Memory Law

- Canonical mutable authority is append-only `state/events.log`.
- T0/T1/T2 cannot mutate canonical object state outside an event.
- Event schema fields: `ts`, `event_id`, `type`, `node`, `payload`.
- Minimum event types:
  - `PAGE_CREATE`, `PAGE_DELETE`, `PAGE_MOVE`
  - `OBJECT_WRITE`, `OBJECT_DELETE`
  - `TIER_EVICT`, `NODE_REGISTER`, `NODE_REMOVE`
- `PAGE_MOVE` must be explicit as an event; implicit tier mutation is forbidden.

## 3. Tier Projection Semantics

Authority hierarchy:
- Event Log = absolute
- T1 (persistent canonical JSON) = derived
- T2 (SQL/index projection) = derived
- T0 (hot lane) = derived

Projection invariant:
- Rebuild(T1 from events) is identical
- Rebuild(T2 from T1) is identical
- Rebuild(T0 from events + deterministic policy) is identical

## 4. Deterministic Page Geometry

- Recommended page size: 4KB logical units.
- Page ID: `SHA256(canonical_page_bytes)` (or equivalent deterministic content-derived identity).
- Packing must depend only on event order.
- Eviction must be deterministic (e.g., logged LRU or deterministic cost-based).
- Forbidden: randomness, wall-clock entropy, thread scheduling effects.

## 5. Deterministic Rebuild Guarantees

- Cold rebuild from preserved events + canonical rules must be bit-identical.
- Cross-machine replay with identical event log + canonical rules + ABI lock must produce identical state.
- Replay ordering: primary `ts` ascending, secondary `event_id` lexicographic.

## 6. ABI Lock Enforcement

Boot sequence:
1. Load grammar bytes
2. Load golden vectors
3. Load canonical source manifest
4. Compute ABI hash
5. Compare to `state/abi.lock.json`
6. On mismatch: freeze cluster, block writes, enter SAFE_MODE

Serving without ABI match is forbidden.

## 7. Integrity & Safety Constraints

- No silent mutation of canonical bytes, packing, or ordering.
- No implicit state outside replay-verifiable artifacts.
- SQL is never authoritative: rebuild SQL from T1 if divergence occurs.

## 8. Conformance Tests

Required tests:
1. Double-hash stability
2. OS variance (Windows/Linux/macOS)
3. Rebuild equivalence
4. Page-move replay correctness
5. Eviction determinism

## 9. Flash-RAM Analogy Mapping

- Memory Controller → JSON Runtime Controller
- Page Movement → `PAGE_MOVE` event
- Wear-leveling → deterministic repacking
- Cache → T0
- Persistent Cells → T1
- Index Tables → T2

## 10. Closure

Conformance requires maintaining Canonical JSON Law, Event-Sourced Mutation Law, Tier Projection Semantics, and Deterministic Rebuild Guarantees.

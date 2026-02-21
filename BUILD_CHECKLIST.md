# ASX-RAM Build & Boot Checklist (Extracted from `README.md`)

> This repository is spec-first. There is no package manager build pipeline in the repo right now; the concrete "build" path described in the spec is the ABI-lock generation + boot enforcement flow.

## 1) Prerequisites

- [ ] **Java 8+** (for the reference `computeAbiHashV1` implementation).
- [ ] **Python 3.8+** (for the reference `compute_abi_hash_v1` implementation).
- [ ] **Node.js 18+** (for WebCrypto `crypto.subtle.digest` in the JS reference).
- [ ] **Input artifacts prepared**:
  - A directory of `*.jar` files.
  - `grammar` bytes file.
  - canonical JSON bytes for golden vectors.

Expected result:
- You can hash all inputs and produce a deterministic 64-hex `abi_hash`.

---

## 2) Compute `abi_hash`

Use one runtime (Java / JS / Python), but all runtimes should produce the **same hash** for the same inputs.

### Option A: Python (quickest local check)

```bash
python3 - <<'PY'
import hashlib, json, pathlib

def sha256_hex(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()

def canon_jar_set_json(jars: dict) -> bytes:
    items = [{"name": k, "sha256": jars[k]} for k in sorted(jars)]
    s = '{"jars":[' + ",".join(
        '{"name":"%s","sha256":"%s"}' % (
            i["name"].replace('\\', '\\\\').replace('"', '\\"'),
            i["sha256"],
        )
        for i in items
    ) + ']}'
    return s.encode('utf-8')

def compute_abi_hash_v1(jars, grammar_bytes, golden_canon_bytes):
    jar_set_hash = sha256_hex(canon_jar_set_json(jars))
    grammar_hash = sha256_hex(grammar_bytes)
    golden_hash  = sha256_hex(golden_canon_bytes)
    abi_material = (
        "ASX_ABI_V1\n" +
        jar_set_hash + "\n" +
        grammar_hash + "\n" +
        golden_hash + "\n"
    ).encode('utf-8')
    return sha256_hex(abi_material)

jar_dir = pathlib.Path("./jars")
grammar_path = pathlib.Path("./grammar.bin")
golden_path = pathlib.Path("./golden.canon.json")

jars = {p.name: sha256_hex(p.read_bytes()) for p in jar_dir.glob("*.jar")}
abi_hash = compute_abi_hash_v1(jars, grammar_path.read_bytes(), golden_path.read_bytes())
print(abi_hash)
PY
```

Expected result:
- Prints exactly one lowercase SHA-256 hex digest (`^[a-f0-9]{64}$`).

---

## 3) Lock hash into pager state

- [ ] Write the computed value into the pager lock field (`pagebook.abi.abi_hash` / boot lock state).

Expected result:
- Boot path has a pinned expected `abi_hash` to compare against.

---

## 4) Run boot enforcement checks

At boot, execute this sequence strictly:

1. load grammar
2. load golden vectors
3. scan JAR directory
4. compute `abi_hash`
5. compare to locked `pagebook.abi.abi_hash`

Expected result:
- **Match** → paging/gRPC/inference can proceed.
- **Mismatch** → **HARD FAIL**: disable OPFS, disable gRPC, refuse inference.

---

## 5) Determinism acceptance checks

- [ ] Run the hash computation in at least **two runtimes** (e.g., Python + JavaScript).
- [ ] Verify both outputs are bit-identical.
- [ ] Re-run after file ordering changes in JAR directory; output should remain unchanged.

Expected result:
- Stable deterministic `abi_hash` regardless of runtime or JAR listing order.

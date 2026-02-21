# ASX-RAM Build & Boot Checklist (Flash-RAM Style, PowerShell-Orchestrated)

> ASX-RAM is operated like a **software flash-RAM/VRAM analog**: JSON-defined runtime cluster nodes emulate memory controllers and tiers in software (not hardware). PowerShell is the orchestrator; Java is compiled with `javac` for math/compression/protocol/API/storage modules.

## 0) Target architecture (must be explicit)

- [ ] Treat runtime as a **JSON memory fabric** with cluster nodes (`node_id`, `tier`, `role`, `capacity`, `policy`).
- [ ] Model tiers as software memory lanes:
  - `T0` hot working-set lane (VRAM-like fast lane)
  - `T1` IDB structured persistent lane
  - `T2` file-mapped/SQL-backed persistent lane
- [ ] Keep node state and movement decisions JSON-canonical for deterministic replay.

Expected result:
- The system behaves like “flash RAM in software”: deterministic page movement across JSON runtime nodes.

---

## 1) Prerequisites

- [ ] **PowerShell 7+** (primary orchestrator for build/boot/control-plane scripts).
- [ ] **JDK 17+** (`javac` + `java`; source-first workflow).
- [ ] **Node.js 18+** (JSON/IDB tooling where needed).
- [ ] **SQL backend** (SQLite/Postgres/etc.) for indexed object/query plane.
- [ ] Repository directories:
  - `src/java/` Java source modules.
  - `state/` boot locks + runtime snapshots.
  - `filemap/` folder-mapped assets declared by JSON descriptors.

Expected result:
- Environment is ready to compile Java sources and run full orchestration via PowerShell.

---

## 2) Build Java modules with `javac` (no JAR pipeline)

```powershell
New-Item -ItemType Directory -Force out | Out-Null
$javaSources = Get-ChildItem -Path ./src/java -Filter *.java -Recurse | ForEach-Object FullName
if (-not $javaSources) { throw "No Java source files found under ./src/java" }
javac -encoding UTF-8 -d ./out $javaSources
```

Expected result:
- `.class` outputs in `./out`.
- Math/compression/protocol/API/storage software modules are compiled and loadable by orchestrator.

---

## 3) Generate deterministic `abi_hash` for cluster boot lock

Compute ABI lock from JSON-canonical source manifest + grammar bytes + golden vectors.

```powershell
$ErrorActionPreference = 'Stop'

function Get-Sha256Hex([byte[]]$bytes) {
  $sha = [System.Security.Cryptography.SHA256]::Create()
  try { ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') }) -join '' }
  finally { $sha.Dispose() }
}

$grammarBytes = [IO.File]::ReadAllBytes('./grammar.bin')
$goldenBytes  = [IO.File]::ReadAllBytes('./golden.canon.json')

$srcEntries = Get-ChildItem ./src/java -Recurse -File |
  Sort-Object FullName |
  ForEach-Object {
    $rel = Resolve-Path -Relative $_.FullName
    $h = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower()
    @{ path = $rel; sha256 = $h }
  }

$clusterLock = @{
  runtime = 'ASX_FLASH_RAM_V1'
  java_sources = $srcEntries
} | ConvertTo-Json -Depth 12 -Compress

$manifestHash = Get-Sha256Hex([Text.Encoding]::UTF8.GetBytes($clusterLock))
$grammarHash  = Get-Sha256Hex($grammarBytes)
$goldenHash   = Get-Sha256Hex($goldenBytes)

$abiMaterial = "ASX_ABI_V1`n$manifestHash`n$grammarHash`n$goldenHash`n"
$abiHash = Get-Sha256Hex([Text.Encoding]::UTF8.GetBytes($abiMaterial))

New-Item -ItemType Directory -Force ./state | Out-Null
@{ abi_hash = $abiHash; runtime = 'ASX_FLASH_RAM_V1' } |
  ConvertTo-Json -Compress |
  Set-Content -Encoding UTF8 ./state/abi.lock.json

$abiHash
```

Expected result:
- Single lowercase 64-hex hash printed.
- Lock file written to `./state/abi.lock.json`.

---

## 4) Boot sequence (PowerShell control plane)

1. load grammar bytes
2. load golden vectors
3. load runtime node JSON + source/file manifests
4. recompute `abi_hash`
5. compare to `state/abi.lock.json`
6. enforce result:
   - match: enable cluster services
   - mismatch: hard fail boot and block writes/mutations

Expected result:
- Only ABI-locked node clusters are allowed to serve storage/API traffic.

---

## 5) JSON runtime cluster node checks (flash-RAM behavior)

- [ ] Each node has JSON identity + role (`controller`, `pager`, `index`, `storage`).
- [ ] Page/object motion is represented as append-only JSON events.
- [ ] IDB persists browser-local objects (`T1`).
- [ ] SQL indexes/query-accelerates JSON objects (`T2` control/index plane).
- [ ] `filemap/` binds JSON file IDs to concrete folders/files for any asset type.

Expected result:
- Software nodes mimic VRAM-like movement semantics with deterministic, JSON-defined state transitions.

---

## 6) Determinism acceptance checks

- [ ] Run lock generation twice with unchanged inputs; hash must match.
- [ ] Change file discovery order; hash remains identical (sorted manifest).
- [ ] Run on another machine/OS; hash remains identical.
- [ ] Replay identical JSON event stream; node placement/actions remain identical.

Expected result:
- Stable flash-RAM software behavior across environments with deterministic ABI lock + replay.

---

## 7) Normative artifacts to keep in sync

- [ ] `specs/ASX_FLASH_RAM_V1.md` (formal memory law)
- [ ] `schemas/asx.flashram.event.schema.json` (event schema)
- [ ] `src/java/CanonicalJsonValidator.java` (canonical JSON + SHA-256 reference)
- [ ] `specs/REPLAY_ENGINE_SPEC.md` (deterministic replay pseudocode)

Expected result:
- Build/boot checklist and formal law artifacts remain aligned for conformance.


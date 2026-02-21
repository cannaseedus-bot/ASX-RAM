# ASX_FLASH_RAM_V1 Replay Engine (Deterministic Pseudocode)

## Core invariants

1. Events sorted by `ts ASC`, then `event_id ASC`.
2. No runtime timing dependency.
3. T0/T1/T2 are projections.

## Replay pseudocode

```text
FUNCTION Replay(event_log):
    SORT event_log BY (ts ASC, event_id ASC)

    INITIALIZE:
        T1 = empty persistent page store
        T2 = empty index store
        T0 = empty hot lane
        NODE_REGISTRY = empty

    FOR each event IN event_log:
        SWITCH event.type:
            CASE NODE_REGISTER: NODE_REGISTRY.add(event.payload.node_id)
            CASE NODE_REMOVE: NODE_REGISTRY.remove(event.payload.node_id)
            CASE OBJECT_WRITE:
                page = PACK_OBJECT(event.payload.object_id, event.payload.data)
                T1.store(page)
            CASE OBJECT_DELETE: T1.remove_object(event.payload.object_id)
            CASE PAGE_CREATE: T1.create_page(event.payload.page_id)
            CASE PAGE_DELETE: T1.delete_page(event.payload.page_id)
            CASE PAGE_MOVE: MOVE_PAGE(event.payload.page_id, event.payload.from, event.payload.to)
            CASE TIER_EVICT: APPLY_EVICTION_POLICY()
            DEFAULT: FAIL("Unknown event type")

    REBUILD T2 FROM T1
    REBUILD T0 FROM T1 USING DETERMINISTIC POLICY

    RETURN {T0, T1, T2}
```

## Deterministic packing

```text
FUNCTION PACK_OBJECT(object_id, data):
    canonical = CANONICAL_JSON(data)
    page_id = HASH(object_id + canonical)
    INSERT into page in deterministic slot order
    RETURN page
```

## Tier movement

```text
FUNCTION MOVE_PAGE(page_id, from, to):
    VERIFY page exists in 'from'
    REMOVE from 'from'
    INSERT into 'to'
```

## Rebuild guarantee

If event log, canonical rules, and ABI lock are identical, replay results must be byte-identical.

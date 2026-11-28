package com.courseqa.retrieval;

import java.util.concurrent.atomic.AtomicBoolean;

/** 只读租约必须用 try-with-resources 释放；重复 close 不重复扣减引用。 */
public final class CatalogSnapshotLease implements AutoCloseable {
    private final CatalogSnapshot snapshot;
    private final AtomicBoolean closed = new AtomicBoolean();

    CatalogSnapshotLease(CatalogSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    CatalogSnapshot snapshot() {
        if (closed.get()) {
            throw new IllegalStateException("快照租约已释放");
        }
        return snapshot;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            snapshot.release();
        }
    }
}

// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.
package com.tricrotism.uworldguard.wgcompat;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counters behind {@code /uwg compat}. Hot-path cost is a single {@link LongAdder#increment()};
 * the per-method stub map is only touched on cold (stubbed) paths.
 */
@NullMarked
public final class CompatDiagnostics {

    public static final LongAdder QUERIES = new LongAdder();
    public static final LongAdder REGION_READS = new LongAdder();
    public static final LongAdder REGION_MUTATIONS = new LongAdder();
    public static final LongAdder WRAPS = new LongAdder();
    public static final LongAdder FLAG_REGISTRATIONS = new LongAdder();
    public static final LongAdder SESSION_DISPATCHES = new LongAdder();
    public static final LongAdder EVENTS_FIRED = new LongAdder();
    public static final LongAdder FLAG_PARSE_FAILURES = new LongAdder();

    /**
     * Last rejection reason per bridged flag. Bounded by the number of flags consumers registered,
     * and only the most recent reason is kept — this exists to answer "why won't it take my value",
     * which the previous attempt answers, not to build a history.
     */
    private static final ConcurrentHashMap<String, String> PARSE_ERRORS = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, LongAdder> STUB_HITS = new ConcurrentHashMap<>();

    private CompatDiagnostics() {
    }

    /**
     * Record a hit on a stubbed API member, e.g. {@code "RegionManager.load"}. Cold paths only.
     */
    public static void stub(final String member) {
        STUB_HITS.computeIfAbsent(member, key -> new LongAdder()).increment();
    }

    /**
     * Record that a consumer's flag refused an input, and why it said it refused.
     */
    public static void flagParseFailure(final String flag, final @Nullable String reason) {
        FLAG_PARSE_FAILURES.increment();
        PARSE_ERRORS.put(flag, reason == null || reason.isBlank() ? "no reason given" : reason);
    }

    /**
     * Snapshot of the last rejection reason per flag, sorted by flag name.
     */
    public static Map<String, String> flagParseErrors() {
        return new TreeMap<>(PARSE_ERRORS);
    }

    /**
     * Snapshot of stubbed-member hit counts, sorted by member name.
     */
    public static Map<String, Long> stubHits() {
        final Map<String, Long> snapshot = new TreeMap<>();
        STUB_HITS.forEach((member, count) -> snapshot.put(member, count.sum()));
        return snapshot;
    }
}

// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

import java.util.Collection;
import java.util.Locale;

/**
 * A tri-state flag: {@link State#ALLOW}, {@link State#DENY}, or unset ({@code null}). Bridges to
 * uWorldGuard's {@code com.tricrotism.uworldguard.flags.StateFlag}, whose default is never null —
 * the null-default case is resolved shim-side.
 */
public class StateFlag extends Flag<StateFlag.State> {

    public enum State {
        ALLOW,
        DENY
    }

    private final boolean def;

    public StateFlag(final String name, final boolean def) {
        super(name);
        this.def = def;
    }

    public StateFlag(final String name, final boolean def, final RegionGroup defaultGroup) {
        super(name, defaultGroup);
        this.def = def;
    }

    @Override
    public State getDefault() {
        return def ? State.ALLOW : null;
    }

    @Override
    public State chooseValue(final Collection<State> values) {
        return combine(values);
    }

    @Override
    public boolean hasConflictStrategy() {
        return true;
    }

    /**
     * Whether an {@code allow} on the global region is meaningless for this flag.
     */
    public boolean preventsAllowOnGlobal() {
        return false;
    }

    @Override
    public State parseInput(final FlagContext context) throws InvalidFlagFormat {
        final String input = context.getUserInput();
        final State state = fromString(input);
        if (state == null) {
            throw new InvalidFlagFormat("Expected 'allow' or 'deny' but got '" + input + "'");
        }
        return state;
    }

    @Override
    public State unmarshal(final Object o) {
        return o == null ? null : fromString(String.valueOf(o));
    }

    @Override
    public Object marshal(final State o) {
        return o == null ? null : o.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Deny-biased test: true when at least one state allows and none denies.
     */
    public static boolean test(final State... states) {
        boolean allowed = false;
        for (final State state : states) {
            if (state == State.DENY) {
                return false;
            }
            if (state == State.ALLOW) {
                allowed = true;
            }
        }
        return allowed;
    }

    public static State combine(final Collection<State> states) {
        if (states == null) {
            return null;
        }
        boolean allowed = false;
        for (final State state : states) {
            if (state == State.DENY) {
                return State.DENY;
            }
            if (state == State.ALLOW) {
                allowed = true;
            }
        }
        return allowed ? State.ALLOW : null;
    }

    public static State combine(final State... states) {
        boolean allowed = false;
        for (final State state : states) {
            if (state == State.DENY) {
                return State.DENY;
            }
            if (state == State.ALLOW) {
                allowed = true;
            }
        }
        return allowed ? State.ALLOW : null;
    }

    public static State denyToNone(final State state) {
        return state == State.DENY ? null : state;
    }

    public static State allowOrNone(final boolean flag) {
        return flag ? State.ALLOW : null;
    }

    private static State fromString(final String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "allow", "true", "yes", "on" -> State.ALLOW;
            case "deny", "false", "no", "off" -> State.DENY;
            default -> null;
        };
    }
}

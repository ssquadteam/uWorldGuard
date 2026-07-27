package com.tricrotism.uworldguard.flags;

import com.tricrotism.uworldguard.domain.Association;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Which players a flag value applies to, mirroring WorldGuard's {@code <flag>-group} qualifier.
 * A flag with no group applies to {@link #ALL}.
 *
 * <p>This is what makes {@code pvp: deny} + {@code pvp-group: nonmembers} mean "outsiders cannot
 * fight here, members can" rather than a blanket deny. Without it a migrated region silently becomes
 * far more restrictive than it was.
 */
@NullMarked
public enum RegionGroup {

    ALL,
    MEMBERS,
    OWNERS,
    NON_MEMBERS,
    NON_OWNERS,
    NONE;

    /**
     * Whether a player with {@code association} is covered by this group. A {@code null} association
     * means the actor is not a player (a creeper, a dispenser); WorldGuard treats those as
     * non-members, so the same is done here.
     */
    public boolean contains(final @Nullable Association association) {
        final Association resolved = association == null ? Association.NON_MEMBER : association;
        return switch (this) {
            case ALL -> true;
            case NONE -> false;
            case MEMBERS -> resolved == Association.MEMBER || resolved == Association.OWNER;
            case OWNERS -> resolved == Association.OWNER;
            case NON_MEMBERS -> resolved == Association.NON_MEMBER;
            case NON_OWNERS -> resolved != Association.OWNER;
        };
    }

    /**
     * Parses a stored or typed group name. Accepts WorldGuard's spellings ({@code nonmembers},
     * {@code non_members}, {@code non-members}) as well as its singular aliases, so imported data and
     * hand-written config both resolve.
     */
    public static @Nullable RegionGroup parse(final String input) {
        return switch (input.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "all", "everyone" -> ALL;
            case "members", "member" -> MEMBERS;
            case "owners", "owner" -> OWNERS;
            case "nonmembers", "non_members", "nonmember", "non_member" -> NON_MEMBERS;
            case "nonowners", "non_owners", "nonowner", "non_owner" -> NON_OWNERS;
            case "none" -> NONE;
            default -> null;
        };
    }

    /**
     * The stored form. Deliberately WorldGuard's spelling ({@code nonmembers}, not {@code non_members})
     * so a hand-edited region file looks like what admins already know; {@link #parse} accepts both.
     */
    public String serialized() {
        return name().toLowerCase(Locale.ROOT).replace("_", "");
    }
}

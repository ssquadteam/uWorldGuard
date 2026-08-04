package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.config.Bypass;
import com.tricrotism.uworldguard.config.EventGate;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.region.ApplicableRegionSet;
import com.tricrotism.uworldguard.region.RegionContainerImpl;
import com.tricrotism.uworldguard.region.RegionQuery;
import com.tricrotism.uworldguard.text.MessageService;
import io.papermc.paper.event.block.VaultChangeStateEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Automated and station-based block machinery: crafters, hoppers, dispensers, enchanting tables,
 * brewing stands, furnaces, TNT priming, sponges, and trial-chamber vaults. None of these run through
 * a player's build or interact check once placed, so each is its own way to keep acting inside a
 * region long after whoever set it up has gone.
 */
@NullMarked
public final class MachineListener implements Listener {

    private final RegionContainerImpl container;
    private final RegionQuery query;
    private final MessageService messages;

    public MachineListener(
        final RegionContainerImpl container, final RegionQuery query, final MessageService messages
    ) {
        this.container = container;
        this.query = query;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafterCraft(final CrafterCraftEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!query.testState(event.getBlock(), Flags.CRAFTER)) {
            event.setCancelled(true);
        }
    }

    /**
     * Hopper and dropper transfers, judged at the destination — the side that gains the item. A hopper
     * chain reaching under a border to drain a region's chests is the case worth stopping, and gating
     * the source instead would let exactly that through.
     *
     * <p>This is the hottest event the plugin listens to: it fires for every item every hopper moves.
     * The registry check comes first because {@code getLocation()} allocates a {@link Location} — on a
     * server where no region sets the flag this handler is a bitset test per world and nothing else:
     * no allocation, no region resolved.
     *
     * <p>{@code EventGate} is not consulted because {@code InventoryMoveItemEvent} extends
     * {@link org.bukkit.event.Event} directly, so the gate cannot resolve a world for it anyway.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperTransfer(final InventoryMoveItemEvent event) {
        if (!container.anyRegionUses(Flags.HOPPER_TRANSFER)) {
            return;
        }
        final Location destination = event.getDestination().getLocation();
        if (destination == null) {
            return;
        }
        final ApplicableRegionSet set = query.getApplicableRegions(destination);
        if (set.worldUses(Flags.HOPPER_TRANSFER) && !set.testState(Flags.HOPPER_TRANSFER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(final BlockDispenseEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!query.testState(event.getBlock(), Flags.DISPENSE)) {
            event.setCancelled(true);
        }
    }

    /**
     * Priming TNT — by redstone, fire, a flaming arrow, or another explosion. Distinct from
     * {@code tnt}, which governs the blast: this stops the block ever lighting.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTntPrime(final TNTPrimeEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (query.testState(event.getBlock(), Flags.TNT_PRIME)) {
            return;
        }
        if (event.getPrimingEntity() instanceof Player player && Bypass.has(player)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpongeAbsorb(final SpongeAbsorbEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!query.testState(event.getBlock(), Flags.SPONGE_ABSORB)) {
            event.setCancelled(true);
        }
    }

    /**
     * Opening a trial-chamber vault (1.21). The loot is one-per-player and unrecoverable, so a region
     * that wants its chambers left alone has no other way to say so.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVault(final VaultChangeStateEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final ApplicableRegionSet set = query.getApplicableRegions(event.getBlock());
        final Player player = event.getPlayer();
        if (player == null) {
            if (!set.testState(Flags.VAULT_USE)) {
                event.setCancelled(true);
            }
            return;
        }
        if (set.testState(Flags.VAULT_USE, player.getUniqueId()) || Bypass.has(player)) {
            return;
        }
        event.setCancelled(true);
        messages.sendDeny(player, Flags.VAULT_USE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(final EnchantItemEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Player player = event.getEnchanter();
        if (query.getApplicableRegions(event.getEnchantBlock())
            .testState(Flags.ENCHANT, player.getUniqueId()) || Bypass.has(player)) {
            return;
        }
        event.setCancelled(true);
        messages.sendDeny(player, Flags.ENCHANT);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrew(final BrewEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!query.testState(event.getBlock(), Flags.BREW)) {
            event.setCancelled(true);
        }
    }

    /**
     * Covers furnaces, smokers, blast furnaces and campfires in one go — they all arrive here as
     * {@link BlockCookEvent}, of which the furnace event is a subclass.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCook(final BlockCookEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!query.testState(event.getBlock(), Flags.SMELT)) {
            event.setCancelled(true);
        }
    }

}

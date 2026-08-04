package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.config.Bypass;
import com.tricrotism.uworldguard.config.EventGate;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.StateFlag;
import com.tricrotism.uworldguard.region.RegionQuery;
import com.tricrotism.uworldguard.text.MessageService;
import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent;
import io.papermc.paper.event.player.PlayerInsertLecternBookEvent;
import io.papermc.paper.event.player.PlayerNameEntityEvent;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockShearEntityEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.*;
import org.jspecify.annotations.NullMarked;

/**
 * Grief vectors that reach a block or entity without going through block-break, block-place or the
 * plain interact flag: shearing, leashing, naming, flower pots, lecterns, signs, entity buckets,
 * armour stands and mannequins.
 *
 * <p>Every handler here follows one shape — resolve the flag at the target, allow bypass, cancel and
 * explain — so each is a couple of lines over {@link #deny}.
 */
@NullMarked
public final class InteractionListener implements Listener {

    private final RegionQuery query;
    private final MessageService messages;

    public InteractionListener(final RegionQuery query, final MessageService messages) {
        this.query = query;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShearEntity(final PlayerShearEntityEvent event) {
        if (!EventGate.disabled(event)) {
            deny(event, event.getPlayer(), event.getEntity(), Flags.SHEAR);
        }
    }

    /**
     * A dispenser shearing a sheep. No player is involved, so this only consults the flag.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispenserShear(final BlockShearEntityEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!query.testState(event.getEntity(), Flags.SHEAR)) {
            event.setCancelled(true);
        }
    }

    /**
     * Leashing. Gated by name because {@code PlayerLeashEntityEvent} extends {@code Event} directly
     * rather than {@code PlayerEvent}, so the usual gate cannot work out which world it happened in.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeash(final PlayerLeashEntityEvent event) {
        final Player player = event.getPlayer();
        if (EventGate.disabled(player.getWorld(), "PlayerLeashEntityEvent")) {
            return;
        }
        deny(event, player, event.getEntity(), Flags.LEASH);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUnleash(final PlayerUnleashEntityEvent event) {
        if (!EventGate.disabled(event)) {
            deny(event, event.getPlayer(), event.getEntity(), Flags.LEASH);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNameEntity(final PlayerNameEntityEvent event) {
        if (!EventGate.disabled(event)) {
            deny(event, event.getPlayer(), event.getEntity(), Flags.NAME_ENTITY);
        }
    }

    /**
     * Bucketing a fish, axolotl or tadpole — a way to carry a region's mobs out in your inventory
     * that neither damage-animals nor chest-access touches.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEntity(final PlayerBucketEntityEvent event) {
        if (!EventGate.disabled(event)) {
            deny(event, event.getPlayer(), event.getEntity(), Flags.BUCKET_ENTITY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(final PlayerArmorStandManipulateEvent event) {
        if (!EventGate.disabled(event)) {
            deny(event, event.getPlayer(), event.getRightClicked(), Flags.ARMOR_STAND_MANIPULATE);
        }
    }

    /**
     * Mannequins (26.x) hold equipment like an armour stand but are a separate entity type, so the
     * armour-stand event never fires for them.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMannequin(final PlayerInteractEntityEvent event) {
        if (EventGate.disabled(event) || !(event.getRightClicked() instanceof Mannequin mannequin)) {
            return;
        }
        deny(event, event.getPlayer(), mannequin, Flags.MANNEQUIN_MANIPULATE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlowerPot(final PlayerFlowerPotManipulateEvent event) {
        if (!EventGate.disabled(event)) {
            denyAt(event, event.getPlayer(), event.getFlowerpot(), Flags.FLOWER_POT);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLecternInsert(final PlayerInsertLecternBookEvent event) {
        if (!EventGate.disabled(event)) {
            denyAt(event, event.getPlayer(), event.getBlock(), Flags.LECTERN);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLecternTake(final PlayerTakeLecternBookEvent event) {
        if (!EventGate.disabled(event)) {
            denyAt(event, event.getPlayer(), event.getLectern().getBlock(), Flags.LECTERN);
        }
    }

    /**
     * Signs are editable after placement since 1.20, so protecting the place and break of a sign no
     * longer protects what it says.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(final SignChangeEvent event) {
        if (!EventGate.disabled(event)) {
            denyAt(event, event.getPlayer(), event.getBlock(), Flags.SIGN_EDIT);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignOpen(final PlayerOpenSignEvent event) {
        if (!EventGate.disabled(event)) {
            denyAt(event, event.getPlayer(), event.getSign().getBlock(), Flags.SIGN_EDIT);
        }
    }

    private void deny(
        final Cancellable event, final Player player, final Entity target, final StateFlag flag
    ) {
        if (query.getApplicableRegions(target).testState(flag, player.getUniqueId()) || Bypass.has(player)) {
            return;
        }
        event.setCancelled(true);
        messages.sendDeny(player, flag);
    }

    private void denyAt(
        final Cancellable event, final Player player, final Block target, final StateFlag flag
    ) {
        if (query.getApplicableRegions(target).testState(flag, player.getUniqueId()) || Bypass.has(player)) {
            return;
        }
        event.setCancelled(true);
        messages.sendDeny(player, flag);
    }
}

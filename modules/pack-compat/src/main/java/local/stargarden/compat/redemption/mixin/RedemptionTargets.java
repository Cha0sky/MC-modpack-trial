package local.stargarden.compat.redemption.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;

// The plugin switches only the eligibility calls inside these targets. The vanilla
// targets receive two further calls from Enigmatic's totem and Wither-drop mixins.
@Mixin(targets = {
        "auviotre.enigmatic.legacy.handlers.EnigmaticEventHandler",
        "auviotre.enigmatic.legacy.contents.item.rings.CursedRing",
        "auviotre.enigmatic.legacy.contents.item.rings.CursedRing$Events",
        "auviotre.enigmatic.legacy.contents.item.misc.BlessStone",
        "auviotre.enigmatic.legacy.contents.item.misc.GuardianHeart",
        "auviotre.enigmatic.legacy.contents.item.charms.DimnessCharm",
        "auviotre.enigmatic.legacy.contents.item.legacy.UnholyGrail",
        "auviotre.enigmatic.legacy.contents.item.materials.TwistedHeart",
        "auviotre.enigmatic.legacy.contents.item.spellstones.TheCube",
        "auviotre.enigmatic.legacy.contents.loot.modifiers.AddCurseLootModifier",
        "auviotre.enigmatic.legacy.contents.loot.modifiers.SpecialLootModifier",
        "auviotre.enigmatic.legacy.packets.server.EnderRingKeyPacket"
}, value = {LivingEntity.class, WitherBoss.class}, remap = false)
abstract class RedemptionTargets {}

@Mixin(targets = "auviotre.enigmatic.legacy.handlers.EnigmaticHandler", remap = false)
interface EligibilityTarget {}

@Mixin(targets = {
        "auviotre.enigmatic.legacy.client.handlers.ClientEventHandler",
        "auviotre.enigmatic.legacy.client.screen.button.EnderChestInventoryButton"
}, remap = false)
abstract class ClientEligibilityTargets {}

@Mixin(targets = "auviotre.enigmatic.legacy.handlers.TooltipHandler", remap = false)
interface TooltipTarget {}

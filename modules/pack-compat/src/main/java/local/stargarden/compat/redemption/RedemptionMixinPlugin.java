package local.stargarden.compat.redemption;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;
import org.spongepowered.asm.util.Annotations;

/**
 * Eligibility checks are spread across normal mod classes and two methods injected into
 * Minecraft by Enigmatic itself. Match the latter by MixinMerged owner as their prefixed
 * names differ per load. Keep SevenCursesBearing and every curse penalty physically bound.
 */
public final class RedemptionMixinPlugin implements IMixinConfigPlugin {
    private static final String HANDLER = "auviotre/enigmatic/legacy/handlers/EnigmaticHandler";
    private static final String BENEFITS = "local/stargarden/compat/redemption/RedemptionBenefits";
    private static final String ENTITY = "Lnet/minecraft/world/entity/LivingEntity;";
    private static final String ITEM = "Lnet/minecraft/world/item/ItemStack;";

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        switch (node.name.substring(node.name.lastIndexOf('/') + 1)) {
            case "EnigmaticHandler" -> {
                access(node, "canUse");
                access(node, "isTheWorthyOne");
            }
            case "CursedRing$Events" -> {
                access(node, "onExperienceDrop");
                access(node, "onEnchantmentLevelSet");
                // First call gates insomnia and burning. Only the second advances worn time.
                replace(node, "onTicked", null, "isTheCursedOne", "hasAccess", 1, 2);
                retention(node, "lambda$onDrop$5");
                replace(node, "onDamageIncoming", null, "isTheWorthyOne", "isWorthyForMonsterBoost", 0, 1);
            }
            case "EnigmaticEventHandler" -> replace(node, "onFinalTarget", null, "isTheWorthyOne", "isWorthyForMonsterBoost", 0, 1);
            case "BlessStone" -> retention(node, "lambda$use$0");
            case "CursedRing" -> retention(node, "lambda$onUnequip$1");
            case "DimnessCharm" -> access(node, "canEquip");
            case "GuardianHeart" -> access(node, "inventoryTick");
            case "TwistedHeart" -> access(node, "canTaint");
            case "UnholyGrail" -> access(node, "finishUsingItem");
            case "TheCube" -> access(node, "getDamageLimit");
            case "SpecialLootModifier" -> access(node, "doApply");
            case "AddCurseLootModifier" -> replace(node, "doApply", null, "isTheCursedOne", "usesSevenCurseDrops", 0, 1);
            case "EnderRingKeyPacket" -> access(node, "lambda$handle$1");
            case "EnderChestInventoryButton" -> access(node, "renderWidget");
            case "TooltipHandler" -> access(node, "cursedOnly");
            case "ClientEventHandler" -> access(node, "onTooltip");
            case "LivingEntity" -> replace(node, "checkMalice", "auviotre.enigmatic.legacy.mixin.MixinLivingEntity",
                    "isTheCursedOne", "hasAccess", 0, 1);
            case "WitherBoss" -> replace(node, "mixDrop", "auviotre.enigmatic.legacy.mixin.entity.MixinWitherBoss",
                    "isTheCursedOne", "hasAccess", 0, 1);
        }
    }

    private static void access(ClassNode node, String name) {
        replace(node, name, null, "isTheCursedOne", "hasAccess", 0, 1);
    }

    private static void retention(ClassNode node, String name) {
        replace(node, name, null, "isCursedItem", "shouldEject", 0, 1);
    }

    private static void replace(ClassNode node, String name, String mergedBy, String from, String to,
                                int ordinal, int expected) {
        int matches = 0;
        boolean retention = from.equals("isCursedItem");
        for (MethodNode method : node.methods) {
            if (mergedBy == null) {
                if (!method.name.equals(name)) continue;
            } else {
                var origin = Annotations.getVisible(method, MixinMerged.class);
                if (!method.name.endsWith("$" + name) || origin == null
                        || !mergedBy.equals(Annotations.getValue(origin, "mixin"))) continue;
            }
            for (var insn : method.instructions) {
                if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC
                        || !call.owner.equals(HANDLER) || !call.name.equals(from)
                        || !call.desc.equals("(" + (retention ? ITEM : ENTITY) + ")Z")) continue;
                if (matches++ != ordinal) continue;
                if (retention) {
                    // The first parameter of each static Curios callback is the wearer.
                    method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 0));
                    call.desc = "(" + ITEM + ENTITY + ")Z";
                    method.maxStack++;
                }
                call.owner = BENEFITS;
                call.name = to;
                call.itf = false;
            }
        }
        if (matches != expected) throw new IllegalStateException(node.name + "." + name
                + ": expected " + expected + " " + from + " call(s), found " + matches);
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> targets, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}

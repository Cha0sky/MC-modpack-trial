package local.stargarden.compat.equipment.mixin;

import dev.ftb.mods.ftblibrary.snbt.SNBTCompoundTag;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import local.stargarden.compat.equipment.EquipmentPages;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TeamData.class, remap = false)
abstract class TeamDataMixin {
    @Shadow @Final private Long2LongMap started;
    @Shadow @Final private Long2LongMap completed;

    @Inject(method = "areDependenciesComplete", at = @At("HEAD"), cancellable = true)
    private void stargarden$allowIndependentCollection(Quest quest, CallbackInfoReturnable<Boolean> cir) {
        // Keep the dependency arrows drawn by the recipe atlas even if the player found the final item first.
        if (EquipmentPages.recipeDiagram(quest)) cir.setReturnValue(true);
    }

    @Inject(method = "deserializeNBT", at = @At("TAIL"))
    private void stargarden$repairEmptyEquipmentTasks(SNBTCompoundTag nbt, CallbackInfo ci) {
        TeamData data = (TeamData)(Object)this;
        boolean changed = false;
        for (ChapterGroup group : data.getFile().getChapterGroups()) {
            boolean repairedGroup = false;
            for (Chapter chapter : group.getChapters()) {
                if (!EquipmentPages.includes(chapter)) continue;
                for (Quest quest : chapter.getQuests()) {
                    // These pages previously had no tasks. A real new task timestamp is proof of collection.
                    if (!quest.getTasks().isEmpty() && data.isCompleted(quest) && !quest.isCompletedRaw(data)) {
                        completed.remove(quest.id);
                        if (quest.getTasks().stream().noneMatch(data::isStarted)) started.remove(quest.id);
                        repairedGroup = true;
                    }
                }
                if (data.isCompleted(chapter) && !chapter.isCompletedRaw(data)) {
                    completed.remove(chapter.id);
                    repairedGroup = true;
                }
            }
            if (repairedGroup) {
                if (data.isCompleted(group) && !group.isCompletedRaw(data)) completed.remove(group.id);
                changed = true;
            }
        }
        if (changed) {
            if (data.isCompleted(data.getFile()) && !data.getFile().isCompletedRaw(data)) completed.remove(data.getFile().id);
            data.clearCachedProgress();
            data.markDirty();
        }
    }
}

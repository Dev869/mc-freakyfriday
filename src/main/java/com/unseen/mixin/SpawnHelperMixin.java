package com.unseen.mixin;

import com.unseen.Config;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces In Control!'s {@code spawn.json}, which cannot be used here — In Control! has no Fabric
 * build. Cancelling natural MONSTER spawning outright is safe because the Stalker is only ever
 * placed explicitly by the Director, never by the natural spawner.
 */
@Mixin(SpawnHelper.class)
public class SpawnHelperMixin {

	@Inject(
			method = "spawnEntitiesInChunk(Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/SpawnHelper$Checker;Lnet/minecraft/world/SpawnHelper$Runner;)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private static void unseen$suppressVanillaHostiles(SpawnGroup group, ServerWorld world, WorldChunk chunk,
	                                                   SpawnHelper.Checker checker, SpawnHelper.Runner runner,
	                                                   CallbackInfo ci) {
		if (Config.get().suppressVanillaHostiles && group == SpawnGroup.MONSTER) {
			ci.cancel();
		}
	}
}

package com.unseen.mixin;

import com.unseen.Config;
import com.unseen.HollowDimension;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Decides what the natural spawner is allowed to do, which for this pack is very little.
 * <p>
 * Replaces In Control!'s {@code spawn.json}, which cannot be used here — In Control! has no Fabric
 * build. Cancelling natural MONSTER spawning outright is safe because the Stalker is only ever placed
 * explicitly by the Director, never by the natural spawner.
 */
@Mixin(SpawnHelper.class)
public class SpawnHelperMixin {

	/**
	 * The groups that only ever fill caves: bats, glow squid, axolotls.
	 * <p>
	 * Land animals need grass and light 9 to spawn, so they are already a surface-only phenomenon and
	 * need no rule. These three are the entire reason a cave currently feels inhabited, which is the
	 * one thing the dark must not feel.
	 */
	private static boolean unseen$isUndergroundLife(SpawnGroup group) {
		return group == SpawnGroup.AMBIENT
				|| group == SpawnGroup.AXOLOTLS
				|| group == SpawnGroup.UNDERGROUND_WATER_CREATURE;
	}

	@Inject(
			method = "spawnEntitiesInChunk(Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/SpawnHelper$Checker;Lnet/minecraft/world/SpawnHelper$Runner;)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private static void unseen$suppressSpawns(SpawnGroup group, ServerWorld world, WorldChunk chunk,
	                                          SpawnHelper.Checker checker, SpawnHelper.Runner runner,
	                                          CallbackInfo ci) {
		Config cfg = Config.get();
		if (cfg.suppressVanillaHostiles && group == SpawnGroup.MONSTER) {
			ci.cancel();
			return;
		}
		if (!cfg.suppressUndergroundLife) {
			return;
		}
		// Nothing is born in the Hollow. Every group, including the harmless ones — a cow down there
		// would be the only company you have, and that is not the feeling.
		if (HollowDimension.isHollow(world) || unseen$isUndergroundLife(group)) {
			ci.cancel();
		}
	}
}

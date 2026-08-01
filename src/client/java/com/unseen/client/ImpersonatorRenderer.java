package com.unseen.client;

import com.unseen.entity.ImpersonatorEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Renders the impersonator as the player whose face it took.
 * <p>
 * The skin is looked up from the player list when the victim is online, which is the case that matters:
 * in multiplayer it should be indistinguishable from your friend at a distance. When they are offline
 * it falls back to the default skin derived from their UUID, so it is still <em>their</em> default
 * rather than a generic one.
 */
public class ImpersonatorRenderer extends MobEntityRenderer<ImpersonatorEntity, PlayerEntityModel<ImpersonatorEntity>> {

	public ImpersonatorRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5f);
	}

	@Override
	public Identifier getTexture(ImpersonatorEntity entity) {
		UUID victim = entity.getVictimId().orElse(null);
		if (victim == null) {
			return DefaultSkinHelper.getTexture();
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getNetworkHandler() != null) {
			PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(victim);
			if (entry != null) {
				return entry.getSkinTextures().texture();
			}
		}
		return DefaultSkinHelper.getSkinTextures(victim).texture();
	}
}

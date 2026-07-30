package com.unseen;

import com.unseen.block.WardrobeBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlocks {
	public static final Block WARDROBE = register("wardrobe",
			new WardrobeBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).strength(2.0f).nonOpaque()));

	/** Gore dressing for the mansion and the Hollow. Soft, slows you down, and unpleasant to walk over. */
	public static final Block VISCERA = register("viscera",
			new Block(AbstractBlock.Settings.copy(Blocks.SLIME_BLOCK)
					.strength(0.4f)
					.sounds(net.minecraft.sound.BlockSoundGroup.SLIME)
					.slipperiness(0.75f)));

	private ModBlocks() {
	}

	private static Block register(String path, Block block) {
		Identifier id = UnseenMod.id(path);
		Block registered = Registry.register(Registries.BLOCK, id, block);
		Registry.register(Registries.ITEM, id, new BlockItem(registered, new Item.Settings()));
		return registered;
	}

	static void init() {
		// Findable in creative without a whole custom tab.
		net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
				.modifyEntriesEvent(net.minecraft.item.ItemGroups.FUNCTIONAL)
				.register(entries -> { entries.add(WARDROBE); entries.add(VISCERA); });
	}
}

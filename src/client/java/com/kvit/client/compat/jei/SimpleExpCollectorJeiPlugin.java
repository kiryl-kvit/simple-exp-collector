package com.kvit.client.compat.jei;

import com.kvit.ExpCollectorContent;
import com.kvit.SimpleExpCollector;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

@JeiPlugin
public final class SimpleExpCollectorJeiPlugin implements IModPlugin {
	private static final Identifier UID = Identifier.fromNamespaceAndPath(SimpleExpCollector.MOD_ID, "jei_plugin");

	@Override
	public @NonNull Identifier getPluginUid() {
		return UID;
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		jeiRuntime.getIngredientManager()
			.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.of(new ItemStack(ExpCollectorContent.expCollectorItem())));
	}
}

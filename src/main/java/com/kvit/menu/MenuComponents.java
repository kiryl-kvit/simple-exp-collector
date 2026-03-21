package com.kvit.menu;

import net.minecraft.network.chat.Component;

public final class MenuComponents {
	private MenuComponents() {
	}

	public static Component plain(Component component) {
		return component.copy().withStyle(style -> style.withItalic(false));
	}
}

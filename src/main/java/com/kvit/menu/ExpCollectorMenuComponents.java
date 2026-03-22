package com.kvit.menu;

import net.minecraft.network.chat.Component;

public final class ExpCollectorMenuComponents {
	private ExpCollectorMenuComponents() {
	}

	public static Component plain(Component component) {
		return component.copy().withStyle(style -> style.withItalic(false));
	}
}

/*
 * This file is part of the Immersive Titles project, licensed under the
 * MIT License
 *
 * Copyright (C) 2026  DevGBX9 and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.devgx9.wanderers_titles.client;

//#if MC >= 26.3
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
//#endif

/**
 * What the mod list of Mod Menu opens when the player clicks this mod.
 *
 * A mod with nothing to configure has nothing to put in a config screen, and this one has nothing to
 * configure: what it has is a screen, the collection of biomes, and that is the screen the mod list
 * opens. So the two are the same screen, and the mod list is simply a second way in - the first
 * being the mod's own key (see {@link TitleOverlay}).
 *
 * The one thing that is not the same about the two ways in is what is behind them. The key is only
 * read in a world, where the world's own registry holds the biomes and the world's own file holds
 * what was found in it; the mod list can be opened from the title screen, where neither exists. The
 * screen is built for whichever of the two is true, and says which one it is (see
 * {@link BiomeCollectionScreen}), rather than refusing to open or showing an empty list as if it
 * were a broken one.
 *
 * Mod Menu is not a dependency of the mod: it is compiled against and declared as an optional
 * dependency in the build (see common.gradle), and the entrypoint below is only ever read by Mod
 * Menu itself, so a player without it never loads this class.
 */
public class ModMenuIntegration
		//#if MC >= 26.3
		implements ModMenuApi
		//#endif
{
	//#if MC >= 26.3
	/**
	 * The screen the mod list opens: the collection of the world in front of the player, with the
	 * discoveries of that world, or the biomes the mod knows while there is no world.
	 */
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory()
	{
		return parent -> collectionOf(Minecraft.getInstance(), parent);
	}

	/**
	 * The collection for {@code client}, as a screen that closes back into {@code parent} - the mod
	 * list it was opened from, whether that list was opened in a world or on the title screen.
	 */
	private static Screen collectionOf(Minecraft client, Screen parent)
	{
		return new BiomeCollectionScreen(client.level, TitleOverlay.discoveredBiomes(), parent);
	}
	//#endif
}

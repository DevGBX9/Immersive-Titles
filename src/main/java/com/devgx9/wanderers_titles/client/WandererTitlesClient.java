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

import com.devgx9.wanderers_titles.WandererTitles;
import com.devgx9.wanderers_titles.client.title.TitleOverlay;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint of the mod, referenced by the {@code client} entrypoint in fabric.mod.json.
 *
 * It is the mod's only entrypoint: Immersive Titles is client side, so there is nothing to run on a
 * server, and the game never loads this class there.
 *
 * The title reveals need Fabric API - its HUD, key mapping and client lifecycle events - plus the
 * client rendering classes of the Minecraft versions the build matrix carries (26.3, the core project,
 * 26.2, 26.1.2 and the whole 1.21 series, built out of the same sources), so their implementation lives
 * right next to this class in {@code TitleOverlay} and is wired up below. Fabric API is declared as a
 * dependency in fabric.mod.json, i.e. it is a mod of its own next to this one, and the overlay names
 * the HUD hook, the level event, the input handlers and the key category of each version inside
 * itself, so the guard below is only the floor of what the overlay exists for: 1.21.
 *
 * That floor is the oldest version of the series and it is what the guard says. A guard that names a
 * newer version here would leave every version below it initialized but empty - listed in the mod
 * list, with no keys and nothing drawn - which is exactly the bug this line is written to not have.
 */
public class WandererTitlesClient implements ClientModInitializer
{
	@Override
	public void onInitializeClient()
	{
		WandererTitles.readMetadata();

		//#if MC >= 1.21
		TitleOverlay.initialize();
		//#endif

		WandererTitles.LOGGER.info("{} v{} client initialized", WandererTitles.MOD_NAME, WandererTitles.MOD_VERSION);
	}
}

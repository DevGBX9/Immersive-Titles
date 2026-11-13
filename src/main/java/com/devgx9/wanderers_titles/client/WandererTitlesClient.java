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
 * client rendering classes of the 26.x Minecraft versions the build matrix carries (26.3, the core
 * project, 26.2 and 26.1.2, built out of the same sources), so their implementation lives right next to
 * this class in {@code TitleOverlay} and is wired up below. Fabric API is declared as a dependency
 * in fabric.mod.json, i.e. it is a mod of its own next to this one. Those APIs changed a lot over
 * time, so a version older than 26.1.2 would need a port of the overlay before it can be re-enabled
 * in the build matrix - which is why the guard below is the 26.1.2 floor and not the core version.
 */
public class WandererTitlesClient implements ClientModInitializer
{
	@Override
	public void onInitializeClient()
	{
		WandererTitles.readMetadata();

		//#if MC >= 26.1.2
		TitleOverlay.initialize();
		//#endif

		WandererTitles.LOGGER.info("{} v{} client initialized", WandererTitles.MOD_NAME, WandererTitles.MOD_VERSION);
	}
}

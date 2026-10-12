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
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint of the mod, referenced by the {@code client} entrypoint in fabric.mod.json.
 *
 * It is the mod's only entrypoint: Immersive Titles is client side, so there is nothing to run on a
 * server, and the game never loads this class there.
 *
 * The title reveals need the Fabric HUD and key mapping APIs plus the client rendering classes of
 * the core Minecraft version (26.3), so their implementation lives right next to this class in
 * {@code TitleOverlay} and is wired up below. Those APIs changed a lot over time, so an older
 * version would need a port of the overlay before it can be re-enabled in the build matrix.
 */
public class WandererTitlesClient implements ClientModInitializer
{
	@Override
	public void onInitializeClient()
	{
		WandererTitles.readMetadata();

		//#if MC >= 26.3
		TitleOverlay.initialize();
		//#endif

		WandererTitles.LOGGER.info("{} v{} client initialized", WandererTitles.MOD_NAME, WandererTitles.MOD_VERSION);
	}
}

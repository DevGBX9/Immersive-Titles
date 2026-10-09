/*
 * This file is part of the Wanderer's Titles project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2026  DevGBX9 and contributors
 *
 * Wanderer's Titles is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wanderer's Titles is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Wanderer's Titles.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.devgx9.wanderers_titles.client;

import com.devgx9.wanderers_titles.WandererTitles;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint of the mod, referenced by the {@code client} entrypoint in fabric.mod.json.
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
		//#if MC >= 26.3
		TitleOverlay.initialize();
		//#endif

		WandererTitles.LOGGER.info("{} v{} client initialized", WandererTitles.MOD_NAME, WandererTitles.MOD_VERSION);
	}
}

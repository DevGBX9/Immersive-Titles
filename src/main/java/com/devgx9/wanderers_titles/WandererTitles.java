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

package com.devgx9.wanderers_titles;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;

//#if MC >= 1.18.2
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
//#else
//$$ import org.apache.logging.log4j.LogManager;
//$$ import org.apache.logging.log4j.Logger;
//#endif

/**
 * Entrypoint of the mod.
 *
 * The biome triggered titles are not implemented yet (see README.md); the two key bound reveals
 * that do exist live in the client package, see {@code TitleOverlay}.
 */
public class WandererTitles implements ModInitializer
{
	public static final String MOD_ID = "wanderers_titles";

	public static final Logger LOGGER =
			//#if MC >= 1.18.2
			LogUtils.getLogger();
			//#else
			//$$ LogManager.getLogger();
			//#endif

	public static String MOD_VERSION = "unknown";
	public static String MOD_NAME = "unknown";

	@Override
	public void onInitialize()
	{
		ModMetadata metadata = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow(RuntimeException::new).getMetadata();
		MOD_NAME = metadata.getName();
		MOD_VERSION = metadata.getVersion().getFriendlyString();

		LOGGER.info("{} v{} initialized", MOD_NAME, MOD_VERSION);
	}
}

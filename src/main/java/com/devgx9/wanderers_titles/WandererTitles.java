/*
 * This file is part of the Immersive Titles project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2026  DevGBX9 and contributors
 *
 * Immersive Titles is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Immersive Titles is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Immersive Titles.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.devgx9.wanderers_titles;

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
 * Identity of the mod: its id, its logger, and the name and version it reports.
 *
 * Immersive Titles is a client side mod - a title is drawn by the client and the biome it names is
 * read on the client - so there is no common or server entrypoint at all: fabric.mod.json declares
 * the mod as client only and the mod's whole implementation hangs off {@code WandererTitlesClient}.
 * This class is what that side names its assets and writes its log lines with.
 */
public final class WandererTitles
{
	public static final String MOD_ID = "immersive_titles";

	public static final Logger LOGGER =
			//#if MC >= 1.18.2
			LogUtils.getLogger();
			//#else
			//$$ LogManager.getLogger();
			//#endif

	/** The name and version of the installed jar, as it reports them itself. Read once, on start. */
	public static String MOD_NAME = "unknown";
	public static String MOD_VERSION = "unknown";

	private WandererTitles()
	{
	}

	/**
	 * Reads the name and version the mod reports out of its own metadata, i.e. out of the jar that
	 * is actually installed, so that the mod list, the log and the jar cannot disagree. Called from
	 * the client entrypoint, once, before anything logs with them.
	 */
	public static void readMetadata()
	{
		ModMetadata metadata = FabricLoader.getInstance().getModContainer(MOD_ID)
				.orElseThrow(RuntimeException::new)
				.getMetadata();
		MOD_NAME = metadata.getName();
		MOD_VERSION = metadata.getVersion().getFriendlyString();
	}
}

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

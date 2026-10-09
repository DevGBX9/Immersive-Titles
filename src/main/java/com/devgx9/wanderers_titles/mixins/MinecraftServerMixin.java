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

package com.devgx9.wanderers_titles.mixins;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Placeholder mixin.
 *
 * It does nothing on purpose: it only keeps the mixin setup (config processing, version specific
 * method names via the preprocessor) exercised and verified by the build until the real
 * Wanderer's Titles client side implementation lands.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin
{
	@Inject(
			//#if MC >= 1.16.0
			method = "runServer",
			//#else
			//$$ method = "run",
			//#endif
			at = @At("HEAD")
	)
	private void onServerRun(CallbackInfo ci)
	{
	}
}

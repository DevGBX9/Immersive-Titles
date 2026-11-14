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

package com.devgx9.wanderers_titles.client.util;

//#if MC >= 1.21.11
import net.minecraft.resources.Identifier;
//#else
//$$ import net.minecraft.resources.ResourceLocation;
//#endif
import net.minecraft.resources.ResourceKey;

/**
 * The id of a registry key, which the game calls {@code identifier()} from 1.21.11 and {@code location()}
 * before it.
 *
 * It is the one place those two names meet: every reader of a biome or structure key in the mod asks
 * for the id through here, and the code that names a discovery is the same on every version.
 */
public final class Ids
{
	private Ids() {
	}

	//#if MC >= 1.21.11
	/** The id a key is registered under. */
	public static Identifier of(ResourceKey<?> key) {
		return key.identifier();
	}
	//#else
	//$$ /** The id a key is registered under. */
	//$$ public static ResourceLocation of(ResourceKey<?> key) {
	//$$ 	return key.location();
	//$$ }
	//#endif
}

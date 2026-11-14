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

package com.devgx9.wanderers_titles.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

//#if MC >= 26.0
import net.minecraft.client.gui.GuiGraphicsExtractor;
//#else
//$$ import net.minecraft.client.gui.GuiGraphics;
//#endif
//#if MC >= 1.21.11
import net.minecraft.resources.Identifier;
//#else
//$$ import net.minecraft.resources.ResourceLocation;
//#endif

/**
 * The seven things the mod draws, named once for every version it is built for.
 *
 * The game renamed its drawing API twice over the range this mod is built for: the object a frame is
 * drawn with is a {@code GuiGraphicsExtractor} under 26.x and a {@code GuiGraphics} before it, the id
 * it draws is an {@code Identifier} from 1.21.11 and a {@code ResourceLocation} before it, the blit is
 * handed a render pipeline from 1.21.6 and not before, and the tooltip is put up by a call of another
 * name before that. None of that is anything the mod's own code is about, so it is all settled here:
 * {@code TitleOverlay} and {@code BiomeCollectionScreen} call these and are the same two classes on
 * every version, with no `//#if` of their own around a single line they draw.
 *
 * Everything else they use - {@code fill}, the scissor and the pose - the game names the same on every
 * one of those versions, so those are called straight on the frame's object and not wrapped here.
 */
public final class Draw
{
	private Draw() {
	}

//#if MC >= 1.21.11
//#if MC >= 26.0
	/** The mod's own id, e.g. {@code immersive_titles:icons/up}. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(com.devgx9.wanderers_titles.WandererTitles.MOD_ID, path);
	}

	/** An id of the game's own, e.g. {@code minecraft:widget/scroller}. */
	public static Identifier vanilla(String path) {
		return Identifier.withDefaultNamespace(path);
	}

	/** One line of text, scaled as the caller has already set the pose up. */
	public static void text(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color,
			boolean shadow) {
		graphics.text(font, text, x, y, color, shadow);
	}

	/** One line of text that is already a plain string, e.g. a mark of the mod's own. */
	public static void text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color,
			boolean shadow) {
		graphics.text(font, text, x, y, color, shadow);
	}

	/** One sprite of the gui atlas, at its own size. */
	public static void sprite(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int width, int height) {
		graphics.blitSprite(
				net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
	}

	/** A picture out of a texture file, drawn at a size of its own. */
	public static void picture(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, float u, float v,
			int width, int height, int sampleWidth, int sampleHeight, int textureWidth, int textureHeight) {
		graphics.blit(
				net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height,
				sampleWidth, sampleHeight, textureWidth, textureHeight);
	}

	/** The game's own tooltip, at the point the mouse is. */
	public static void tooltip(GuiGraphicsExtractor graphics, Component text, int mouseX, int mouseY) {
		graphics.setTooltipForNextFrame(text, mouseX, mouseY);
	}
//#else
//#if MC >= 1.21.6
//$$ 	/** The mod's own id, e.g. {@code immersive_titles:icons/up}. */
//$$ 	public static Identifier id(String path) {
//$$ 		return Identifier.fromNamespaceAndPath(com.devgx9.wanderers_titles.WandererTitles.MOD_ID, path);
//$$ 	}
//$$
//$$ 	/** An id of the game's own, e.g. {@code minecraft:widget/scroller}. */
//$$ 	public static Identifier vanilla(String path) {
//$$ 		return Identifier.withDefaultNamespace(path);
//$$ 	}
//$$
//$$ 	/** One line of text, scaled as the caller has already set the pose up. */
//$$ 	public static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int color,
//$$ 			boolean shadow) {
//$$ 		graphics.drawString(font, text, x, y, color, shadow);
//$$ 	}
//$$
//$$ 	/** One line of text that is already a plain string, e.g. a mark of the mod's own. */
//$$ 	public static void text(GuiGraphics graphics, Font font, String text, int x, int y, int color,
//$$ 			boolean shadow) {
//$$ 		graphics.drawString(font, text, x, y, color, shadow);
//$$ 	}
//$$
//$$ 	/** One sprite of the gui atlas, at its own size. */
//$$ 	public static void sprite(GuiGraphics graphics, Identifier sprite, int x, int y, int width, int height) {
//$$ 		graphics.blitSprite(
//$$ 				net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
//$$ 	}
//$$
//$$ 	/** A picture out of a texture file, drawn at a size of its own. */
//$$ 	public static void picture(GuiGraphics graphics, Identifier texture, int x, int y, float u, float v,
//$$ 			int width, int height, int sampleWidth, int sampleHeight, int textureWidth, int textureHeight) {
//$$ 		graphics.blit(
//$$ 				net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height,
//$$ 				sampleWidth, sampleHeight, textureWidth, textureHeight);
//$$ 	}
//$$
//$$ 	/** The game's own tooltip, at the point the mouse is. */
//$$ 	public static void tooltip(GuiGraphics graphics, Component text, int mouseX, int mouseY) {
//$$ 		graphics.setTooltipForNextFrame(text, mouseX, mouseY);
//$$ 	}
//#else
//$$ 	/** The mod's own id, e.g. {@code immersive_titles:icons/up}. */
//$$ 	public static Identifier id(String path) {
//$$ 		return Identifier.fromNamespaceAndPath(com.devgx9.wanderers_titles.WandererTitles.MOD_ID, path);
//$$ 	}
//$$
//$$ 	/** An id of the game's own, e.g. {@code minecraft:widget/scroller}. */
//$$ 	public static Identifier vanilla(String path) {
//$$ 		return Identifier.withDefaultNamespace(path);
//$$ 	}
//$$
//$$ 	/** One line of text, scaled as the caller has already set the pose up. */
//$$ 	public static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int color,
//$$ 			boolean shadow) {
//$$ 		graphics.drawString(font, text, x, y, color, shadow);
//$$ 	}
//$$
//$$ 	/** One line of text that is already a plain string, e.g. a mark of the mod's own. */
//$$ 	public static void text(GuiGraphics graphics, Font font, String text, int x, int y, int color,
//$$ 			boolean shadow) {
//$$ 		graphics.drawString(font, text, x, y, color, shadow);
//$$ 	}
//$$
//$$ 	/** One sprite of the gui atlas, at its own size. */
//$$ 	public static void sprite(GuiGraphics graphics, Identifier sprite, int x, int y, int width, int height) {
//$$ 		graphics.blitSprite(sprite, x, y, width, height);
//$$ 	}
//$$
//$$ 	/** A picture out of a texture file, drawn at a size of its own. */
//$$ 	public static void picture(GuiGraphics graphics, Identifier texture, int x, int y, float u, float v,
//$$ 			int width, int height, int sampleWidth, int sampleHeight, int textureWidth, int textureHeight) {
//$$ 		graphics.blit(
//$$ 				texture, x, y, u, v, width, height, sampleWidth, sampleHeight, textureWidth, textureHeight);
//$$ 	}
//$$
//$$ 	/** The game's own tooltip, at the point the mouse is. */
//$$ 	public static void tooltip(GuiGraphics graphics, Component text, int mouseX, int mouseY) {
//$$ 		graphics.renderTooltip(net.minecraft.client.Minecraft.getInstance().font, text, mouseX, mouseY);
//$$ 	}
//#endif
//#endif
//#else
//#if MC >= 1.21.6
//$$ 	/** The mod's own id, e.g. {@code immersive_titles:icons/up}. */
//$$ 	public static ResourceLocation id(String path) {
//$$ 		return ResourceLocation.fromNamespaceAndPath(com.devgx9.wanderers_titles.WandererTitles.MOD_ID, path);
//$$ 	}
//$$
//$$ 	/** An id of the game's own, e.g. {@code minecraft:widget/scroller}. */
//$$ 	public static ResourceLocation vanilla(String path) {
//$$ 		return ResourceLocation.withDefaultNamespace(path);
//$$ 	}
//$$
//$$ 	/** One line of text, scaled as the caller has already set the pose up. */
//$$ 	public static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int color,
//$$ 			boolean shadow) {
//$$ 		graphics.drawString(font, text, x, y, color, shadow);
//$$ 	}
//$$
//$$ 	/** One line of text that is already a plain string, e.g. a mark of the mod's own. */
//$$ 	public static void text(GuiGraphics graphics, Font font, String text, int x, int y, int color,
//$$ 			boolean shadow) {
//$$ 		graphics.drawString(font, text, x, y, color, shadow);
//$$ 	}
//$$
//$$ 	/** One sprite of the gui atlas, at its own size. */
//$$ 	public static void sprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width,
//$$ 			int height) {
//$$ 		graphics.blitSprite(
//$$ 				net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
//$$ 	}
//$$
//$$ 	/** A picture out of a texture file, drawn at a size of its own. */
//$$ 	public static void picture(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u,
//$$ 			float v, int width, int height, int sampleWidth, int sampleHeight, int textureWidth,
//$$ 			int textureHeight) {
//$$ 		graphics.blit(
//$$ 				net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height,
//$$ 				sampleWidth, sampleHeight, textureWidth, textureHeight);
//$$ 	}
//$$
//$$ 	/** The game's own tooltip, at the point the mouse is. */
//$$ 	public static void tooltip(GuiGraphics graphics, Component text, int mouseX, int mouseY) {
//$$ 		graphics.setTooltipForNextFrame(text, mouseX, mouseY);
//$$ 	}
//#else
//$$ 	/** The mod's own id, e.g. {@code immersive_titles:icons/up}. */
//$$ 	public static ResourceLocation id(String path) {
//$$ 		return ResourceLocation.fromNamespaceAndPath(com.devgx9.wanderers_titles.WandererTitles.MOD_ID, path);
//$$ 	}
//$$
//$$ 	/** An id of the game's own, e.g. {@code minecraft:widget/scroller}. */
//$$ 	public static ResourceLocation vanilla(String path) {
//$$ 		return ResourceLocation.withDefaultNamespace(path);
//$$ 	}
//$$
//$$ 	/** One line of text, scaled as the caller has already set the pose up. */
//$$ 	public static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int color,
//$$ 			boolean shadow) {
//$$ 		graphics.drawString(font, text, x, y, color, shadow);
//$$ 	}
//$$
//$$ 	/** One line of text that is already a plain string, e.g. a mark of the mod's own. */
//$$ 	public static void text(GuiGraphics graphics, Font font, String text, int x, int y, int color,
//$$ 			boolean shadow) {
//$$ 		graphics.drawString(font, text, x, y, color, shadow);
//$$ 	}
//$$
//$$ 	/** One sprite of the gui atlas, at its own size. */
//$$ 	public static void sprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width,
//$$ 			int height) {
//$$ 		graphics.blitSprite(sprite, x, y, width, height);
//$$ 	}
//$$
//$$ 	/** A picture out of a texture file, drawn at a size of its own. */
//$$ 	public static void picture(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u,
//$$ 			float v, int width, int height, int sampleWidth, int sampleHeight, int textureWidth,
//$$ 			int textureHeight) {
//$$ 		graphics.blit(
//$$ 				texture, x, y, u, v, width, height, sampleWidth, sampleHeight, textureWidth, textureHeight);
//$$ 	}
//$$
//$$ 	/** The game's own tooltip, at the point the mouse is. */
//$$ 	public static void tooltip(GuiGraphics graphics, Component text, int mouseX, int mouseY) {
//$$ 		graphics.renderTooltip(net.minecraft.client.Minecraft.getInstance().font, text, mouseX, mouseY);
//$$ 	}
//#endif
//#endif
}

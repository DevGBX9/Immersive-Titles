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
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

/**
 * The screen that arranges the mod's two titles: the large plate and its name, and the small corner
 * title, moved and scaled against a preview of the real drawing.
 *
 * The preview is the game's own drawing at a smaller size, not a mock: the stage is the GUI-scaled
 * screen scaled uniformly into the room between the header and the buttons, and the plate, the name
 * and the corner title are drawn inside it by the same {@link TitleOverlay} helpers and the same
 * {@link TitleGeometry} the game uses. The customization is edited on a copy (see
 * {@link TitleCustomization#copy()}), so Done and Escape discard the arrangement and only Save writes
 * it and makes it live.
 *
 * The screen is reached from the gear in the Collection (see {@link BiomeCollectionScreen}), which is
 * the parent this screen returns to.
 */
public final class TitleCustomizationScreen extends Screen
{
	private static final int HANDLE_SIZE = 5;
	private static final int HANDLE_HIT_RADIUS = 7;
	private static final float WHEEL_STEP = 1.05F;
	private static final int SELECTION_RED = 0xFF3B30;
	private static final long SAVED_MESSAGE_MS = 2000L;

	private static final Identifier FRAME = Identifier.fromNamespaceAndPath(
			WandererTitles.MOD_ID, "textures/title/frame_0100.png");
	private static final Identifier BUTTON_SPRITE = Identifier.fromNamespaceAndPath(
			WandererTitles.MOD_ID, "widget/button");
	private static final Identifier LIT_BUTTON_SPRITE = Identifier.fromNamespaceAndPath(
			WandererTitles.MOD_ID, "widget/button_highlighted");
	private static final Component SAMPLE = Component.translatable(
			"screen.immersive_titles.customize.sample").withStyle(ChatFormatting.BOLD);

	private final Screen parent;
	private final TitleCustomization working;
	private final TitleGeometry.Parts parts;
	private Button saveButton;
	private Button resetButton;
	private Button doneButton;
	private Component message;
	private int messageColor;
	private long messageUntilMs;
	private int stageLeft, stageTop, stageWidth, stageHeight;
	private float stageScale, virtualLeft, virtualTop;

	public TitleCustomizationScreen(Screen parent)
	{
		super(Component.translatable("screen.immersive_titles.customize.title"));
		this.parent = parent;
		this.working = TitleCustomization.live().copy();
		this.parts = TitleGeometry.split(SAMPLE);
	}

	@Override
	protected void init()
	{
		int buttonY = height - 8 - 20;
		int roomTop = height >= 210 ? 44 : 24;
		int roomBottom = buttonY - 16;
		int availableWidth = Math.max(1, width - 2 * 8);
		int availableHeight = Math.max(1, roomBottom - roomTop);
		float fit = Math.min(availableWidth / (float) width, availableHeight / (float) height);
		stageWidth = Math.max(1, (int) Math.floor(width * fit));
		stageHeight = Math.max(1, (int) Math.floor(height * fit));
		stageLeft = 8 + (availableWidth - stageWidth) / 2;
		stageTop = roomTop + (availableHeight - stageHeight) / 2;
		stageScale = Math.min(stageWidth / (float) width, stageHeight / (float) height);
		virtualLeft = stageLeft + (stageWidth - width * stageScale) / 2.0F;
		virtualTop = stageTop + (stageHeight - height * stageScale) / 2.0F;

		int gap = 6;
		int buttonWidth = Math.max(1, Math.min(100, (width - 2 * 8 - 2 * gap) / 3));
		int buttonX = (width - (buttonWidth * 3 + gap * 2)) / 2;

		saveButton = Button.builder(Component.translatable("screen.immersive_titles.customize.save"),
				button -> save()).bounds(buttonX, buttonY, buttonWidth, 20).build();
		resetButton = Button.builder(Component.translatable("screen.immersive_titles.customize.reset"),
				button -> reset()).bounds(buttonX + buttonWidth + gap, buttonY, buttonWidth, 20).build();
		doneButton = Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds(buttonX + 2 * (buttonWidth + gap), buttonY, buttonWidth, 20).build();
		addRenderableWidget(saveButton);
		addRenderableWidget(resetButton);
		addRenderableWidget(doneButton);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
	{
		if (messageUntilMs != 0L && Util.getMillis() > messageUntilMs)
		{
			message = null;
			messageUntilMs = 0L;
		}

		int buttonY = height - 8 - 20;

		drawCentered(graphics, title, 8, 0xFFFFFFFF);

		if (height >= 210)
		{
			drawCentered(graphics, Component.translatable(
					"screen.immersive_titles.customize.hint.move"), 20, 0xFF8A8A8A);
			drawCentered(graphics, Component.translatable(
					"screen.immersive_titles.customize.hint.save"), 30, 0xFF8A8A8A);
		}

		graphics.fill(stageLeft - 1, stageTop - 1, stageLeft + stageWidth + 1,
				stageTop + stageHeight + 1, 0xFF3A3A42);
		graphics.fill(stageLeft, stageTop, stageLeft + stageWidth, stageTop + stageHeight,
				0xFF0E0E12);

		TitleGeometry.Plate plate = TitleGeometry.plate(width, height, working);
		TitleGeometry.Name name = TitleGeometry.name(plate, font, parts.name(), working);
		TitleGeometry.Corner corner = TitleGeometry.corner(width, height, font, parts.kicker(),
				parts.name(), working);

		graphics.enableScissor(stageLeft, stageTop, stageLeft + stageWidth, stageTop + stageHeight);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(virtualLeft, virtualTop);
		pose.scale(stageScale, stageScale);
		TitleOverlay.drawPlate(graphics, FRAME, plate);
		TitleOverlay.drawName(graphics, font, parts.name(), name, 255);
		TitleOverlay.drawCorner(graphics, font, parts.kicker(), parts.name(), corner, 0xFFFFFFFF);
		pose.popMatrix();
		graphics.disableScissor();

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		drawButtonFace(graphics, saveButton);
		drawButtonFace(graphics, resetButton);
		drawButtonFace(graphics, doneButton);
		drawButtonFocus(graphics, saveButton);
		drawButtonFocus(graphics, resetButton);
		drawButtonFocus(graphics, doneButton);

		if (message != null)
		{
			drawCentered(graphics, message, buttonY - 12, messageColor);
		}
	}

	/**
	 * Writes {@code text} centred at {@code y}. This is the collection's own form of the call, which
	 * exists on both of the Minecraft versions the mod is built for.
	 */
	private void drawCentered(GuiGraphicsExtractor graphics, Component text, int y, int color)
	{
		graphics.text(font, text, (width - font.width(text)) / 2, y, color, true);
	}

	/**
	 * Writes the working copy to the file and, on success, makes it the live customization. The
	 * screen stays open and the message above the buttons says whether it was written; a failure
	 * leaves the live values and the file as they were, and stays until the next edit.
	 */
	private void save()
	{
		if (working.save())
		{
			TitleCustomization.replace(working);
			message = Component.translatable("screen.immersive_titles.customize.saved");
			messageColor = 0xFF55FF55;
			messageUntilMs = Util.getMillis() + SAVED_MESSAGE_MS;
		}
		else
		{
			message = Component.translatable("screen.immersive_titles.customize.save_failed");
			messageColor = 0xFFFF5555;
			messageUntilMs = 0L;
		}
	}

	/** Returns the working copy to the default placement and size; the file is written only by Save. */
	private void reset()
	{
		working.reset();
		message = null;
		messageUntilMs = 0L;
	}

	@Override
	public void onClose()
	{
		minecraft.gui.setScreen(parent);
	}

	/**
	 * Paints the pack's button face over the game's own, then writes the label over it again: the game
	 * drew the button and its label before this screen painted the face, so both have to be drawn here
	 * to keep the pack's dark face and the white label together.
	 */
	private void drawButtonFace(GuiGraphicsExtractor graphics, Button button)
	{
		Identifier sprite = button.isHoveredOrFocused() ? LIT_BUTTON_SPRITE : BUTTON_SPRITE;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
				button.getX(), button.getY(), button.getWidth(), button.getHeight());

		Component label = button.getMessage();
		graphics.text(font, label, button.getX() + (button.getWidth() - font.width(label)) / 2,
				button.getY() + (button.getHeight() - font.lineHeight) / 2, 0xFFFFFFFF, true);
	}

	/** Draws a 1 px silver border around a button the keyboard is on, so the focus is visible. */
	private void drawButtonFocus(GuiGraphicsExtractor graphics, Button button)
	{
		if (!button.isFocused())
		{
			return;
		}

		int left = button.getX() - 1;
		int top = button.getY() - 1;
		int right = button.getX() + button.getWidth() + 1;
		int bottom = button.getY() + button.getHeight() + 1;

		graphics.fill(left, top, right, top + 1, 0xFFC9CCD1);
		graphics.fill(left, bottom - 1, right, bottom, 0xFFC9CCD1);
		graphics.fill(left, top + 1, left + 1, bottom - 1, 0xFFC9CCD1);
		graphics.fill(right - 1, top + 1, right, bottom - 1, 0xFFC9CCD1);
	}
}

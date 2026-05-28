package com.trade.gui;

import com.trade.TradeConfig;
import com.trade.network.TradeSelectPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

@Environment(EnvType.CLIENT)
public class TradeScreen extends HandledScreen<TradeScreenHandler> {
    private static final int BG_WIDTH = 276;
    private static final int BG_HEIGHT = 186;

    private static final int LEFT_PANEL_X = 6;
    private static final int LEFT_PANEL_Y = 6;
    private static final int LEFT_PANEL_WIDTH = 88;
    private static final int LEFT_PANEL_HEIGHT = BG_HEIGHT - 12;

    private static final int RIGHT_PANEL_X = 96;
    private static final int RIGHT_TOP_Y = 6;
    private static final int RIGHT_TOP_HEIGHT = 48;
    private static final int RIGHT_BOTTOM_Y = RIGHT_TOP_Y + RIGHT_TOP_HEIGHT + 4;
    private static final int RIGHT_BOTTOM_HEIGHT = BG_HEIGHT - RIGHT_BOTTOM_Y - 6;

    private static final int GROUP_BUTTON_Y = LEFT_PANEL_Y + 2;
    private static final int GROUP_PREV_X = LEFT_PANEL_X + LEFT_PANEL_WIDTH - 24;
    private static final int GROUP_NEXT_X = LEFT_PANEL_X + LEFT_PANEL_WIDTH - 12;

    private static final int LIST_CONTENT_Y = LEFT_PANEL_Y + 14;
    private static final int LIST_ROW_HEIGHT = 16;
    private static final int LIST_MAX_ROWS = 10;

    private int listScrollOffset;
    private int currentGroupIndex;

    public TradeScreen(TradeScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = BG_WIDTH;
        this.backgroundHeight = BG_HEIGHT;
        this.playerInventoryTitleY = 0;
    }

    private void clampGroupIndex() {
        int max = Math.max(0, TradeConfig.getTradeGroups().size() - 1);
        if (currentGroupIndex < 0) {
            currentGroupIndex = 0;
        } else if (currentGroupIndex > max) {
            currentGroupIndex = max;
        }
    }

    private List<TradeConfig.TradeEntry> getCurrentGroupTrades() {
        List<TradeConfig.TradeGroup> groups = TradeConfig.getTradeGroups();
        if (groups.isEmpty()) {
            return List.of();
        }
        clampGroupIndex();
        return groups.get(currentGroupIndex).trades;
    }

    private int getCurrentGroupStartOffset() {
        List<TradeConfig.TradeGroup> groups = TradeConfig.getTradeGroups();
        if (groups.isEmpty()) {
            return 0;
        }
        clampGroupIndex();

        int offset = 0;
        for (int i = 0; i < currentGroupIndex; i++) {
            offset += groups.get(i).trades.size();
        }
        return offset;
    }

    private String getCurrentGroupName() {
        List<TradeConfig.TradeGroup> groups = TradeConfig.getTradeGroups();
        if (groups.isEmpty()) {
            return "";
        }
        clampGroupIndex();
        return groups.get(currentGroupIndex).group;
    }

    private void clampScrollOffset() {
        int maxOffset = Math.max(0, getCurrentGroupTrades().size() - LIST_MAX_ROWS);
        if (listScrollOffset < 0) {
            listScrollOffset = 0;
        } else if (listScrollOffset > maxOffset) {
            listScrollOffset = maxOffset;
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x;
        int y = this.y;

        context.fill(x, y, x + BG_WIDTH, y + BG_HEIGHT, 0xFF2B2B2B);

        context.fill(x + LEFT_PANEL_X, y + LEFT_PANEL_Y, x + LEFT_PANEL_X + LEFT_PANEL_WIDTH, y + LEFT_PANEL_Y + LEFT_PANEL_HEIGHT, 0xFF1F1F1F);
        context.fill(x + RIGHT_PANEL_X, y + RIGHT_TOP_Y, x + BG_WIDTH - 6, y + RIGHT_TOP_Y + RIGHT_TOP_HEIGHT, 0xFF1F1F1F);
        context.fill(x + RIGHT_PANEL_X, y + RIGHT_BOTTOM_Y, x + BG_WIDTH - 6, y + RIGHT_BOTTOM_Y + RIGHT_BOTTOM_HEIGHT, 0xFF1F1F1F);

        context.fill(x + LEFT_PANEL_X + LEFT_PANEL_WIDTH, y + 6, x + LEFT_PANEL_X + LEFT_PANEL_WIDTH + 1, y + BG_HEIGHT - 6, 0xFF4D4D4D);

        context.fill(x + RIGHT_PANEL_X + 8, y + 16, x + RIGHT_PANEL_X + 8 + 18, y + 16 + 18, 0xFF4D4D4D);
        context.fill(x + RIGHT_PANEL_X + 10, y + 18, x + RIGHT_PANEL_X + 10 + 14, y + 18 + 14, 0xFF222222);

        context.fill(x + RIGHT_PANEL_X + 92, y + 16, x + RIGHT_PANEL_X + 92 + 18, y + 16 + 18, 0xFF4D4D4D);
        context.fill(x + RIGHT_PANEL_X + 94, y + 18, x + RIGHT_PANEL_X + 94 + 14, y + 18 + 14, 0xFF222222);

        context.drawText(this.textRenderer, "->", x + RIGHT_PANEL_X + 56, y + 21, 0xFFBBBBBB, false);

        for (int i = 0; i < this.handler.slots.size(); i++) {
            int sx = this.handler.slots.get(i).x;
            int sy = this.handler.slots.get(i).y;
            context.fill(x + sx, y + sy, x + sx + 18, y + sy + 18, 0xAA5A5A5A);
            context.fill(x + sx + 1, y + sy + 1, x + sx + 17, y + sy + 17, 0xAA2A2A2A);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        clampGroupIndex();
        clampScrollOffset();

        int localMouseX = mouseX - this.x;
        int localMouseY = mouseY - this.y;

        String groupName = this.textRenderer.trimToWidth(getCurrentGroupName(), LEFT_PANEL_WIDTH - 30);
        if (!groupName.isEmpty()) {
            context.drawText(this.textRenderer, groupName, LEFT_PANEL_X + 4, LEFT_PANEL_Y + 2, 0xD0D0D0, false);
        }

        drawGroupControls(context, localMouseX, localMouseY);
        drawTradeList(context, localMouseX, localMouseY);

        TradeConfig.TradeEntry trade = handler.getSelectedTrade();
        if (trade != null) {
            String xp = Text.translatable("screen.legittrade.xp_reward", trade.xpReward).getString();
            context.drawText(this.textRenderer, xp, RIGHT_PANEL_X + 8, RIGHT_TOP_Y + 36, 0x55FF55, false);
        } else {
            context.drawText(this.textRenderer, Text.translatable("screen.legittrade.no_trade_selected"), RIGHT_PANEL_X + 8, RIGHT_TOP_Y + 20, 0xAAAAAA, false);
        }

        context.drawText(this.textRenderer, this.playerInventoryTitle, RIGHT_PANEL_X + 8, RIGHT_BOTTOM_Y + 24, 0xFFFFFF, false);
    }

    private void drawGroupControls(DrawContext context, int localMouseX, int localMouseY) {
        int groupCount = TradeConfig.getTradeGroups().size();
        boolean canPrev = groupCount > 0 && currentGroupIndex > 0;
        boolean canNext = groupCount > 0 && currentGroupIndex < groupCount - 1;

        drawNavButton(context, GROUP_PREV_X, GROUP_BUTTON_Y, false, canPrev, isInPrevGroupLocal(localMouseX, localMouseY));
        drawNavButton(context, GROUP_NEXT_X, GROUP_BUTTON_Y, true, canNext, isInNextGroupLocal(localMouseX, localMouseY));
    }

    private void drawNavButton(DrawContext context, int x, int y, boolean right, boolean enabled, boolean hovered) {
        int outer = enabled ? 0xFF000000 : 0xFF222222;
        int fill = enabled ? (hovered ? 0xFF8B8B8B : 0xFF6B6B6B) : 0xFF4A4A4A;

        context.fill(x, y, x + 10, y + 10, outer);
        context.fill(x + 1, y + 1, x + 9, y + 9, fill);
        context.fill(x + 1, y + 1, x + 9, y + 2, 0xFFFFFFFF);
        context.fill(x + 1, y + 1, x + 2, y + 9, 0xFFFFFFFF);
        context.fill(x + 8, y + 1, x + 9, y + 9, 0xFF3A3A3A);
        context.fill(x + 1, y + 8, x + 9, y + 9, 0xFF3A3A3A);

        int arrowColor = enabled ? 0xFFFFFFFF : 0xFF7A7A7A;
        drawHorizontalArrowGlyph(context, x + 3, y + 3, right, arrowColor);
    }

    private void drawHorizontalArrowGlyph(DrawContext context, int x, int y, boolean right, int color) {
        if (right) {
            context.fill(x, y, x + 1, y + 5, color);
            context.fill(x + 1, y + 1, x + 2, y + 4, color);
            context.fill(x + 2, y + 2, x + 3, y + 3, color);
        } else {
            context.fill(x + 2, y, x + 3, y + 5, color);
            context.fill(x + 1, y + 1, x + 2, y + 4, color);
            context.fill(x, y + 2, x + 1, y + 3, color);
        }
    }

    private void drawTradeList(DrawContext context, int localMouseX, int localMouseY) {
        List<TradeConfig.TradeEntry> groupTrades = getCurrentGroupTrades();
        if (groupTrades.isEmpty()) {
            context.drawText(this.textRenderer, Text.translatable("screen.legittrade.no_trades"), LEFT_PANEL_X + 4, LIST_CONTENT_Y, 0x999999, false);
            return;
        }

        int hovered = getHoveredTradeIndex(localMouseX, localMouseY);
        int selected = handler.getSelectedTradeIndex();
        int groupStart = getCurrentGroupStartOffset();

        int end = Math.min(groupTrades.size(), listScrollOffset + LIST_MAX_ROWS);
        for (int i = listScrollOffset; i < end; i++) {
            int row = i - listScrollOffset;
            int rowY = LIST_CONTENT_Y + row * LIST_ROW_HEIGHT;
            int globalIndex = groupStart + i;

            int color = 0xFF2A2A2A;
            if (globalIndex == selected) {
                color = 0xFF335577;
            } else if (globalIndex == hovered) {
                color = 0xFF3A3A3A;
            }
            context.fill(LEFT_PANEL_X + 2, rowY - 1, LEFT_PANEL_X + LEFT_PANEL_WIDTH - 2, rowY + LIST_ROW_HEIGHT - 2, color);

            TradeConfig.TradeEntry trade = groupTrades.get(i);
            boolean affordable = handler.canAffordTradeAt(globalIndex);
            ItemStack inputIcon = trade.createInputPreviewStack();
            ItemStack outputIcon = trade.createOutputStack();

            int inputIconX = LEFT_PANEL_X + 4;
            int inputCountX = LEFT_PANEL_X + 22;
            int arrowX = LEFT_PANEL_X + 36;
            int outputIconX = LEFT_PANEL_X + 46;
            int outputCountX = LEFT_PANEL_X + 64;

            if (!inputIcon.isEmpty()) {
                context.drawItem(inputIcon, inputIconX, rowY - 1);
            }
            context.drawText(this.textRenderer, "->", arrowX, rowY + 2, affordable ? 0xBBBBBB : 0xCC6666, false);
            if (!outputIcon.isEmpty()) {
                context.drawItem(outputIcon, outputIconX, rowY - 1);
            }

            int lineColor = affordable ? 0xFFFFFF : 0xFF8888;
            String inputCount = compactCount(trade.inputCount);
            String outputCount = compactCount(trade.outputCount);
            int countY = rowY + 4;

            context.drawText(this.textRenderer, inputCount, inputCountX, countY, lineColor, true);
            context.drawText(this.textRenderer, outputCount, outputCountX, countY, lineColor, true);

            if (!affordable) {
                context.fill(LEFT_PANEL_X + 2, rowY + LIST_ROW_HEIGHT - 3, LEFT_PANEL_X + LEFT_PANEL_WIDTH - 2, rowY + LIST_ROW_HEIGHT - 2, 0x99AA4444);
            }
        }
    }

    private String compactCount(int count) {
        if (count > 99) {
            return "99+";
        }
        return Integer.toString(Math.max(0, count));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawMouseoverTooltip(DrawContext context, int x, int y) {
        super.drawMouseoverTooltip(context, x, y);

        int localMouseX = x - this.x;
        int localMouseY = y - this.y;

        int hoveredTradeIndex = getHoveredTradeIndex(localMouseX, localMouseY);
        if (hoveredTradeIndex >= 0 && hoveredTradeIndex < TradeConfig.getTrades().size()) {
            TradeConfig.TradeEntry trade = TradeConfig.getTrades().get(hoveredTradeIndex);
            String inName = (trade.getInputItem() != null) ? trade.getInputItem().getName().getString() : trade.input;
            String outName = (trade.getOutputItem() != null) ? trade.getOutputItem().getName().getString() : trade.output;
            context.drawTooltip(this.textRenderer, Text.literal(trade.inputCount + " " + inName + " -> " + trade.outputCount + " " + outName), x, y);
            return;
        }

        TradeConfig.TradeEntry selectedTrade = handler.getSelectedTrade();
        if (this.focusedSlot != null && this.focusedSlot == this.handler.getSlot(0) && selectedTrade != null) {
            if (!this.focusedSlot.getStack().isEmpty() && !handler.getOutputPreview().isEmpty()) {
                Text tooltip = Text.translatable(
                    "tooltip.legittrade.trade_preview",
                    selectedTrade.outputCount,
                    handler.getOutputPreview().getName(),
                    selectedTrade.xpReward
                );
                context.drawTooltip(this.textRenderer, tooltip, x, y);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (isInLeftPanel(mouseX, mouseY)) {
            if (amount > 0) {
                listScrollOffset--;
            } else if (amount < 0) {
                listScrollOffset++;
            }
            clampScrollOffset();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.client != null && this.client.interactionManager != null && this.client.player != null) {
            int localMouseX = (int) mouseX - this.x;
            int localMouseY = (int) mouseY - this.y;

            if (isInLeftPanel(mouseX, mouseY)) {
                if (isInPrevGroupLocal(localMouseX, localMouseY)) {
                    currentGroupIndex--;
                    clampGroupIndex();
                    listScrollOffset = 0;
                    return true;
                }
                if (isInNextGroupLocal(localMouseX, localMouseY)) {
                    currentGroupIndex++;
                    clampGroupIndex();
                    listScrollOffset = 0;
                    return true;
                }

                int index = getClickedTradeIndex(mouseY);
                if (index >= 0 && index < handler.getTradeCount()) {
                    boolean shiftHeld = hasShiftDown();
                    TradeSelectPacket.sendToServer(this.handler.syncId, index, shiftHeld);
                    this.handler.selectTrade(this.client.player, index, shiftHeld);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int getClickedTradeIndex(double mouseY) {
        int localY = (int) mouseY - this.y;
        if (localY < LIST_CONTENT_Y || localY >= LIST_CONTENT_Y + LIST_MAX_ROWS * LIST_ROW_HEIGHT) {
            return -1;
        }

        List<TradeConfig.TradeEntry> groupTrades = getCurrentGroupTrades();
        if (groupTrades.isEmpty()) {
            return -1;
        }

        int row = (localY - LIST_CONTENT_Y) / LIST_ROW_HEIGHT;
        int indexInGroup = listScrollOffset + row;
        if (indexInGroup < 0 || indexInGroup >= groupTrades.size()) {
            return -1;
        }

        return getCurrentGroupStartOffset() + indexInGroup;
    }

    private int getHoveredTradeIndex(int localMouseX, int localMouseY) {
        int left = LEFT_PANEL_X + 2;
        int right = LEFT_PANEL_X + LEFT_PANEL_WIDTH - 2;
        if (localMouseX < left || localMouseX >= right) {
            return -1;
        }
        if (localMouseY < LIST_CONTENT_Y || localMouseY >= LIST_CONTENT_Y + LIST_MAX_ROWS * LIST_ROW_HEIGHT) {
            return -1;
        }

        List<TradeConfig.TradeEntry> groupTrades = getCurrentGroupTrades();
        int row = (localMouseY - LIST_CONTENT_Y) / LIST_ROW_HEIGHT;
        int indexInGroup = listScrollOffset + row;
        if (indexInGroup < 0 || indexInGroup >= groupTrades.size()) {
            return -1;
        }

        return getCurrentGroupStartOffset() + indexInGroup;
    }

    private boolean isInLeftPanel(double mouseX, double mouseY) {
        int lx = this.x + LEFT_PANEL_X;
        int ly = this.y + LEFT_PANEL_Y;
        return mouseX >= lx && mouseX < lx + LEFT_PANEL_WIDTH && mouseY >= ly && mouseY < ly + LEFT_PANEL_HEIGHT;
    }

    private boolean isInPrevGroupLocal(int localMouseX, int localMouseY) {
        return localMouseX >= GROUP_PREV_X && localMouseX < GROUP_PREV_X + 10
            && localMouseY >= GROUP_BUTTON_Y && localMouseY < GROUP_BUTTON_Y + 10;
    }

    private boolean isInNextGroupLocal(int localMouseX, int localMouseY) {
        return localMouseX >= GROUP_NEXT_X && localMouseX < GROUP_NEXT_X + 10
            && localMouseY >= GROUP_BUTTON_Y && localMouseY < GROUP_BUTTON_Y + 10;
    }
}

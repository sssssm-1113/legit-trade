package com.trade.gui;

import com.trade.TradeBlocks;
import com.trade.TradeConfig;
import com.trade.network.TradePackets;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;

import java.util.List;

public class TradeScreenHandler extends ScreenHandler {
    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    private static final int PLAYER_INV_START = 2;
    private static final int PLAYER_INV_END = 38;

    private static final int RIGHT_PANEL_X = 96;
    private static final int BG_HEIGHT = 186;
    private static final int HOTBAR_Y = BG_HEIGHT - 36;
    private static final int PLAYER_INV_Y = HOTBAR_Y - 58;

    public static final int SELECT_TRADE_BASE_BUTTON_ID = 0;

    private final SimpleInventory inputInventory;
    private final SimpleInventory outputInventory;
    private final ScreenHandlerContext context;
    private int selectedTradeIndex;
    private int lastXpReward;
    private boolean suppressContentChanged;

    public TradeScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, ScreenHandlerContext.EMPTY);
    }

    public TradeScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(TradePackets.TRADE_SCREEN_HANDLER, syncId);
        this.inputInventory = new SimpleInventory(1) {
            @Override
            public void markDirty() {
                super.markDirty();
                if (!TradeScreenHandler.this.suppressContentChanged) {
                    TradeScreenHandler.this.onContentChanged(this);
                }
            }
        };
        this.outputInventory = new SimpleInventory(1);
        this.context = context;

        this.addSlot(new TradeInputSlot(this, RIGHT_PANEL_X + 8, 16));
        this.addSlot(new TradeOutputSlot(this, RIGHT_PANEL_X + 92, 16));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, RIGHT_PANEL_X + 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, RIGHT_PANEL_X + 8 + col * 18, HOTBAR_Y));
        }

        refreshOutputPreview();
    }

    private List<TradeConfig.TradeEntry> getTrades() {
        return TradeConfig.getTrades();
    }

    public int getTradeCount() {
        return getTrades().size();
    }

    public int getSelectedTradeIndex() {
        int count = getTradeCount();
        if (count <= 0) {
            selectedTradeIndex = 0;
            return 0;
        }
        if (selectedTradeIndex < 0 || selectedTradeIndex >= count) {
            selectedTradeIndex = 0;
        }
        return selectedTradeIndex;
    }

    public TradeConfig.TradeEntry getSelectedTrade() {
        List<TradeConfig.TradeEntry> trades = getTrades();
        if (trades.isEmpty()) {
            selectedTradeIndex = 0;
            return null;
        }
        return trades.get(getSelectedTradeIndex());
    }

    public TradeConfig.TradeEntry getTradeAt(int index) {
        List<TradeConfig.TradeEntry> trades = getTrades();
        if (index < 0 || index >= trades.size()) {
            return null;
        }
        return trades.get(index);
    }

    public boolean canAffordTradeAt(int index) {
        TradeConfig.TradeEntry trade = getTradeAt(index);
        if (trade == null || trade.inputCount <= 0 || trade.getInputItem() == null) {
            return false;
        }

        int total = 0;

        ItemStack input = inputInventory.getStack(INPUT_SLOT);
        if (trade.matchesInputStack(input)) {
            total += input.getCount();
        }

        for (int i = PLAYER_INV_START; i < PLAYER_INV_END; i++) {
            ItemStack stack = this.slots.get(i).getStack();
            if (trade.matchesInputStack(stack)) {
                total += stack.getCount();
                if (total >= trade.inputCount) {
                    return true;
                }
            }
        }

        return total >= trade.inputCount;
    }

    private boolean canExecuteTrade(TradeConfig.TradeEntry trade) {
        if (trade == null || trade.inputCount <= 0 || trade.outputCount <= 0 || trade.xpReward < 0) {
            return false;
        }

        if (trade.getInputItem() == null || trade.getOutputItem() == null) {
            return false;
        }

        ItemStack input = inputInventory.getStack(INPUT_SLOT);
        if (!trade.matchesInputStack(input)) {
            return false;
        }

        if (trade.inputCount > input.getMaxCount()) {
            return false;
        }

        return input.getCount() >= trade.inputCount;
    }

    private boolean canInsertIntoPlayerInventory(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        int remaining = stack.getCount();

        for (int i = PLAYER_INV_START; i < PLAYER_INV_END; i++) {
            ItemStack playerStack = this.slots.get(i).getStack();
            if (playerStack.isEmpty()) {
                remaining = Math.max(0, remaining - stack.getMaxCount());
            } else if (ItemStack.canCombine(playerStack, stack)) {
                int free = playerStack.getMaxCount() - playerStack.getCount();
                if (free > 0) {
                    remaining = Math.max(0, remaining - free);
                }
            }

            if (remaining <= 0) {
                return true;
            }
        }

        return false;
    }

    private ItemStack createOutputStack(TradeConfig.TradeEntry trade) {
        if (trade == null) {
            return ItemStack.EMPTY;
        }
        return trade.createOutputStack();
    }

    private void refreshOutputPreview() {
        context.run((world, pos) -> {
            if (world.isClient) {
                return;
            }

            ItemStack preview = ItemStack.EMPTY;
            TradeConfig.TradeEntry trade = getSelectedTrade();
            if (canExecuteTrade(trade)) {
                preview = createOutputStack(trade);
            }

            outputInventory.setStack(0, preview);
            outputInventory.markDirty();
        });
    }

    public ItemStack getOutputPreview() {
        return outputInventory.getStack(0);
    }

    public boolean hasValidTrade() {
        return !outputInventory.getStack(0).isEmpty();
    }

    private void returnInputToPlayer(PlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        ItemStack remaining = stack.copy();
        if (!player.getInventory().insertStack(remaining) && !remaining.isEmpty()) {
            player.dropItem(remaining, false);
        }
    }

    private void autoFillSelectedTrade(PlayerEntity player, boolean fillToMax) {
        TradeConfig.TradeEntry trade = getSelectedTrade();
        if (trade == null || trade.inputCount <= 0 || trade.getInputItem() == null) {
            return;
        }

        int maxStackSize = trade.getInputItem().getMaxCount();
        ItemStack inputStack = inputInventory.getStack(INPUT_SLOT);

        if (!inputStack.isEmpty() && !trade.matchesInputStack(inputStack)) {
            ItemStack toReturn = inputStack.copy();
            inputInventory.setStack(INPUT_SLOT, ItemStack.EMPTY);
            returnInputToPlayer(player, toReturn);
            inputStack = ItemStack.EMPTY;
        }

        int currentCount = trade.matchesInputStack(inputStack) ? inputStack.getCount() : 0;
        int spaceAvailable = maxStackSize - currentCount;
        if (spaceAvailable <= 0) {
            inputInventory.markDirty();
            sendContentUpdates();
            return;
        }

        int toFill;
        if (fillToMax) {
            toFill = spaceAvailable;
        } else {
            toFill = Math.min(spaceAvailable, trade.inputCount);
        }
        int needed = toFill;

        for (int i = PLAYER_INV_START; i < PLAYER_INV_END && needed > 0; i++) {
            Slot sourceSlot = this.slots.get(i);
            ItemStack sourceStack = sourceSlot.getStack();
            if (!trade.matchesInputStack(sourceStack)) {
                continue;
            }

            int move = Math.min(needed, sourceStack.getCount());
            if (move <= 0) {
                continue;
            }

            if (inputStack.isEmpty()) {
                inputStack = sourceStack.copy();
                inputStack.setCount(move);
                inputInventory.setStack(INPUT_SLOT, inputStack);
            } else {
                inputStack.increment(move);
            }

            sourceStack.decrement(move);
            if (sourceStack.isEmpty()) {
                sourceSlot.setStack(ItemStack.EMPTY);
            } else {
                sourceSlot.markDirty();
            }

            needed -= move;
        }

        inputInventory.markDirty();
        sendContentUpdates();
    }

    private ItemStack executeTrade() {
        return executeTradeInternal(true);
    }

    private ItemStack executeTradeInternal(boolean syncAfterTrade) {
        lastXpReward = 0;

        TradeConfig.TradeEntry trade = getSelectedTrade();
        if (!canExecuteTrade(trade)) {
            if (syncAfterTrade) {
                refreshOutputPreview();
            }
            return ItemStack.EMPTY;
        }

        ItemStack result = createOutputStack(trade);
        if (result.isEmpty()) {
            if (syncAfterTrade) {
                refreshOutputPreview();
            }
            return ItemStack.EMPTY;
        }

        ItemStack input = inputInventory.getStack(INPUT_SLOT);
        input.decrement(trade.inputCount);
        if (input.isEmpty()) {
            inputInventory.setStack(INPUT_SLOT, ItemStack.EMPTY);
        }
        inputInventory.markDirty();

        lastXpReward = trade.xpReward;
        if (syncAfterTrade) {
            refreshOutputPreview();
            sendContentUpdates();
        }
        return result;
    }

    public boolean selectTrade(PlayerEntity player, int index) {
        return selectTrade(player, index, false);
    }

    public boolean selectTrade(PlayerEntity player, int index, boolean shiftHeld) {
        int tradeCount = getTradeCount();
        if (index < 0 || index >= tradeCount) {
            return false;
        }

        selectedTradeIndex = index;
        if (!player.getWorld().isClient) {
            autoFillSelectedTrade(player, shiftHeld);
            refreshOutputPreview();
        }
        sendContentUpdates();
        return true;
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id >= SELECT_TRADE_BASE_BUTTON_ID) {
            int index = id - SELECT_TRADE_BASE_BUTTON_ID;
            return selectTrade(player, index);
        }
        return false;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot.hasStack()) {
            ItemStack slotStack = slot.getStack();
            result = slotStack.copy();

            if (slotIndex == INPUT_SLOT) {
                if (!this.insertItem(slotStack, PLAYER_INV_START, PLAYER_INV_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex == OUTPUT_SLOT) {
                long totalXp = 0L;
                int craftedCount = 0;
                final int maxCraftIterations = 2048;

                suppressContentChanged = true;
                try {
                    while (craftedCount < maxCraftIterations) {
                        TradeConfig.TradeEntry trade = getSelectedTrade();
                        if (!canExecuteTrade(trade)) {
                            break;
                        }

                        ItemStack preview = createOutputStack(trade);
                        if (preview.isEmpty() || !canInsertIntoPlayerInventory(preview.copy())) {
                            break;
                        }

                        ItemStack crafted = executeTradeInternal(false);
                        if (crafted.isEmpty()) {
                            break;
                        }

                        ItemStack toInsert = crafted.copy();
                        if (!this.insertItem(toInsert, PLAYER_INV_START, PLAYER_INV_END, false)) {
                            break;
                        }

                        if (!toInsert.isEmpty()) {
                            break;
                        }

                        result = crafted.copy();
                        totalXp += Math.max(0, trade.xpReward);
                        craftedCount++;
                    }
                } finally {
                    suppressContentChanged = false;
                }

                if (craftedCount > 0) {
                    refreshOutputPreview();
                    sendContentUpdates();

                    if (!player.getWorld().isClient && totalXp > 0) {
                        player.addExperience((int) Math.min(Integer.MAX_VALUE, totalXp));
                    }
                    if (!player.getWorld().isClient) {
                        player.playSound(SoundEvents.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                    }
                    lastXpReward = 0;
                    return result;
                }

                if (!player.getWorld().isClient) {
                    player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                return ItemStack.EMPTY;
            } else {
                if (!this.insertItem(slotStack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }

        return result;
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        super.onContentChanged(inventory);
        refreshOutputPreview();
        sendContentUpdates();
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return ScreenHandler.canUse(this.context, player, TradeBlocks.TRADE_BLOCK);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        dropInventory(player, inputInventory);
        outputInventory.clear();
    }

    private static class TradeInputSlot extends Slot {
        private final TradeScreenHandler handler;

        public TradeInputSlot(TradeScreenHandler handler, int x, int y) {
            super(handler.inputInventory, 0, x, y);
            this.handler = handler;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            TradeConfig.TradeEntry trade = handler.getSelectedTrade();
            if (trade == null) {
                return false;
            }
            return trade.matchesInputStack(stack);
        }
    }

    private static class TradeOutputSlot extends Slot {
        private final TradeScreenHandler handler;

        public TradeOutputSlot(TradeScreenHandler handler, int x, int y) {
            super(handler.outputInventory, 0, x, y);
            this.handler = handler;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeItems(PlayerEntity player) {
            return handler.hasValidTrade();
        }

        @Override
        public ItemStack takeStack(int amount) {
            return handler.executeTrade();
        }

        @Override
        public void onTakeItem(PlayerEntity player, ItemStack stack) {
            if (player.getWorld().isClient) {
                return;
            }

            if (stack.isEmpty()) {
                player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            if (handler.lastXpReward > 0) {
                player.addExperience(handler.lastXpReward);
            }
            handler.lastXpReward = 0;
            player.playSound(SoundEvents.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
        }

        @Override
        public void markDirty() {
        }
    }
}

package com.trade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TradeConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("legittrade");
	private static final int MAX_STACK_COUNT = 64;
	public static final int MAX_GROUPS = 128;
	public static final int MAX_TRADES_PER_GROUP = 1024;
	public static final int MAX_ITEM_ID_LENGTH = 128;
	public static final int MAX_GROUP_NAME_LENGTH = 64;
	public static final int MAX_NBT_LENGTH = 4096;

	public enum NbtMatchMode {
		EXACT,
		CONTAINS,
		IGNORE
	}
	private static volatile List<TradeGroup> tradeGroups = Collections.emptyList();
	private static volatile List<TradeEntry> trades = Collections.emptyList();

	public static List<TradeGroup> getTradeGroups() {
		return tradeGroups;
	}

	public static List<TradeEntry> getTrades() {
		return trades;
	}

	public static void setTradeGroups(List<TradeGroup> newGroups) {
		List<TradeGroup> safeGroups = freezeGroups(newGroups);
		tradeGroups = Collections.unmodifiableList(safeGroups);

		List<TradeEntry> flattened = new ArrayList<>();
		for (TradeGroup group : safeGroups) {
			flattened.addAll(group.trades);
		}
		trades = Collections.unmodifiableList(flattened);
	}

	public static void setTrades(List<TradeEntry> newTrades) {
		setTradeGroups(List.of(new TradeGroup("Default", newTrades)));
	}

	public static final class TradeGroup {
		public final String group;
		public final List<TradeEntry> trades;

		public TradeGroup(String group, List<TradeEntry> trades) {
			this.group = (group == null || group.isBlank()) ? "Default" : group;
			this.trades = Collections.unmodifiableList(new ArrayList<>(trades != null ? trades : Collections.emptyList()));
		}
	}

	public static final class TradeEntry {
		public final String input;
		public final String output;
		public final String inputNbt;
		public final String outputNbt;
		public final NbtMatchMode nbtMatchMode;
		public final int inputCount;
		public final int outputCount;
		public final int xpReward;
		private final NbtCompound inputNbtCompound;
		private final NbtCompound outputNbtCompound;

		public TradeEntry(String input, String output, int inputCount, int outputCount, int xpReward) {
			this(input, output, null, null, NbtMatchMode.EXACT, inputCount, outputCount, xpReward);
		}

		public TradeEntry(String input, String output, String inputNbt, String outputNbt, int inputCount, int outputCount, int xpReward) {
			this(input, output, inputNbt, outputNbt, NbtMatchMode.EXACT, inputCount, outputCount, xpReward);
		}

		public TradeEntry(String input, String output, String inputNbt, String outputNbt, NbtMatchMode nbtMatchMode, int inputCount, int outputCount, int xpReward) {
			this.input = input;
			this.output = output;
			this.inputNbt = normalizeNbtString(inputNbt);
			this.outputNbt = normalizeNbtString(outputNbt);
			this.nbtMatchMode = nbtMatchMode != null ? nbtMatchMode : NbtMatchMode.EXACT;
			this.inputCount = inputCount;
			this.outputCount = outputCount;
			this.xpReward = xpReward;
			this.inputNbtCompound = parseNbtSafely(this.inputNbt);
			this.outputNbtCompound = parseNbtSafely(this.outputNbt);
		}

		private static String normalizeNbtString(String nbt) {
			if (nbt == null) {
				return null;
			}
			String trimmed = nbt.trim();
			return trimmed.isEmpty() ? null : trimmed;
		}

		private static NbtCompound parseNbtSafely(String nbt) {
			if (nbt == null) {
				return null;
			}
			try {
				return StringNbtReader.parse(nbt);
			} catch (CommandSyntaxException ignored) {
				return null;
			}
		}

		public Item getInputItem() {
			Identifier id = Identifier.tryParse(input);
			if (id == null || !Registries.ITEM.containsId(id)) {
				return null;
			}
			return Registries.ITEM.get(id);
		}

		public Item getOutputItem() {
			Identifier id = Identifier.tryParse(output);
			if (id == null || !Registries.ITEM.containsId(id)) {
				return null;
			}
			return Registries.ITEM.get(id);
		}

		public boolean matchesInputStack(ItemStack stack) {
			Item item = getInputItem();
			if (item == null || stack.isEmpty() || stack.getItem() != item) {
				return false;
			}
			if (nbtMatchMode == NbtMatchMode.IGNORE) {
				return true;
			}
			if (inputNbtCompound == null) {
				return true;
			}
			NbtCompound stackNbt = stack.getNbt();
			if (stackNbt == null) {
				return false;
			}
			if (nbtMatchMode == NbtMatchMode.EXACT) {
				return stackNbt.equals(inputNbtCompound);
			}
			// CONTAINS mode
			return containsNbt(stackNbt, inputNbtCompound);
		}

		private static boolean containsNbt(NbtCompound stackNbt, NbtCompound requiredNbt) {
			for (String key : requiredNbt.getKeys()) {
				if (!stackNbt.contains(key)) {
					return false;
				}
				if (!stackNbt.get(key).equals(requiredNbt.get(key))) {
					return false;
				}
			}
			return true;
		}

		public ItemStack createInputPreviewStack() {
			Item item = getInputItem();
			if (item == null) {
				return ItemStack.EMPTY;
			}
			ItemStack stack = new ItemStack(item, 1);
			if (inputNbtCompound != null) {
				stack.setNbt(inputNbtCompound.copy());
			}
			return stack;
		}

		public ItemStack createOutputStack() {
			Item outputItem = getOutputItem();
			if (outputItem == null) {
				return ItemStack.EMPTY;
			}
			int safeOutputCount = Math.min(outputCount, outputItem.getMaxCount());
			if (safeOutputCount <= 0) {
				return ItemStack.EMPTY;
			}
			ItemStack stack = new ItemStack(outputItem, safeOutputCount);
			if (outputNbtCompound != null) {
				stack.setNbt(outputNbtCompound.copy());
			}
			return stack;
		}

		public boolean isValid() {
			if (input == null || output == null) {
				return false;
			}
			Identifier inputId = Identifier.tryParse(input);
			Identifier outputId = Identifier.tryParse(output);
			if (inputId == null || outputId == null) {
				return false;
			}
			if (!Registries.ITEM.containsId(inputId) || !Registries.ITEM.containsId(outputId)) {
				return false;
			}
			if (inputNbt != null && (inputNbt.length() > MAX_NBT_LENGTH || inputNbtCompound == null)) {
				return false;
			}
			if (outputNbt != null && (outputNbt.length() > MAX_NBT_LENGTH || outputNbtCompound == null)) {
				return false;
			}
			Item inputItem = Registries.ITEM.get(inputId);
			int inputMaxCount = inputItem.getMaxCount();
			return input.length() <= MAX_ITEM_ID_LENGTH
				&& output.length() <= MAX_ITEM_ID_LENGTH
				&& inputCount >= 1 && inputCount <= inputMaxCount
				&& outputCount >= 1 && outputCount <= MAX_STACK_COUNT
				&& xpReward >= 0;
		}
	}

	private static int clampTradeCount(int count) {
		if (count <= 0) {
			return 1;
		}
		return Math.min(count, MAX_STACK_COUNT);
	}

	private static List<TradeGroup> freezeGroups(List<TradeGroup> groups) {
		if (groups == null || groups.isEmpty()) {
			return Collections.emptyList();
		}
		List<TradeGroup> safe = new ArrayList<>(groups.size());
		for (TradeGroup group : groups) {
			if (group == null || group.trades.isEmpty()) {
				continue;
			}
			safe.add(new TradeGroup(group.group, group.trades));
		}
		return safe;
	}

	private static List<TradeGroup> toSingleDefaultGroup(List<TradeEntry> entries) {
		if (entries.isEmpty()) {
			return Collections.emptyList();
		}
		return List.of(new TradeGroup("Default", entries));
	}

	private static List<TradeGroup> toValidGroups(List<RawTradeGroup> rawGroups) {
		List<TradeGroup> groups = new ArrayList<>();
		if (rawGroups == null) {
			return groups;
		}

		for (RawTradeGroup rawGroup : rawGroups) {
			if (groups.size() >= MAX_GROUPS) {
				LOGGER.warn("Too many trade groups in config, ignoring groups beyond limit {}", MAX_GROUPS);
				break;
			}
			if (rawGroup == null) {
				continue;
			}
			String groupName = (rawGroup.group == null || rawGroup.group.isBlank()) ? "Default" : rawGroup.group;
			if (groupName.length() > MAX_GROUP_NAME_LENGTH) {
				LOGGER.warn("Trade group name too long, skipping group '{}': max {} characters", groupName, MAX_GROUP_NAME_LENGTH);
				continue;
			}
			List<TradeEntry> validTrades = toValidTrades(rawGroup.trades, groupName);
			if (!validTrades.isEmpty()) {
				groups.add(new TradeGroup(groupName, validTrades));
			}
		}
		return groups;
	}

	private static List<TradeEntry> toValidTrades(List<RawTradeEntry> rawList, String groupName) {
		List<TradeEntry> validTrades = new ArrayList<>();
		if (rawList == null) {
			return validTrades;
		}

		Set<String> seen = new HashSet<>();
		for (RawTradeEntry raw : rawList) {
			if (validTrades.size() >= MAX_TRADES_PER_GROUP) {
				LOGGER.warn("Too many trades in group '{}', ignoring entries beyond limit {}", groupName, MAX_TRADES_PER_GROUP);
				break;
			}
			if (raw == null) {
				continue;
			}
			TradeEntry entry = raw.toTradeEntry();
			if (!entry.isValid()) {
				LOGGER.warn("Invalid trade entry in group '{}': {} -> {} (count: {}/{}, xp: {})",
					groupName, raw.input, raw.output, raw.inputCount, raw.outputCount, raw.xpReward);
				continue;
			}

			String key = entry.input + "|" + entry.output + "|" + entry.inputCount + "|" + entry.outputCount + "|" + entry.xpReward + "|" + entry.inputNbt + "|" + entry.outputNbt;
			if (!seen.add(key)) {
				LOGGER.warn("Duplicate trade entry ignored in group '{}': {} -> {} (count: {}/{}, xp: {})",
					groupName, entry.input, entry.output, entry.inputCount, entry.outputCount, entry.xpReward);
				continue;
			}

			validTrades.add(entry);
		}
		return validTrades;
	}

	private static final class RawTradeEntry {
		String input;
		String output;
		String inputNbt;
		String outputNbt;
		String nbt;
		String nbtMatchMode;
		int inputCount = 1;
		int outputCount = 1;
		int xpReward = 0;

		TradeEntry toTradeEntry() {
			String finalOutputNbt = (outputNbt != null && !outputNbt.isBlank()) ? outputNbt : nbt;
			NbtMatchMode mode = parseNbtMatchMode(nbtMatchMode);
			return new TradeEntry(input, output, inputNbt, finalOutputNbt, mode, clampTradeCount(inputCount), clampTradeCount(outputCount), xpReward);
		}

		private static NbtMatchMode parseNbtMatchMode(String mode) {
			if (mode == null || mode.isBlank()) {
				return NbtMatchMode.EXACT;
			}
			switch (mode.toLowerCase()) {
				case "contains":
					return NbtMatchMode.CONTAINS;
				case "ignore":
					return NbtMatchMode.IGNORE;
				default:
					return NbtMatchMode.EXACT;
			}
		}
	}

	private static final class RawTradeGroup {
		String group = "Default";
		List<RawTradeEntry> trades = Collections.emptyList();
	}

	private static Path getConfigPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("legittrade.json");
	}

	public static List<TradeGroup> parseTradeGroups(String json) {
		if (json == null || json.isBlank()) {
			return Collections.emptyList();
		}

		try {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			JsonElement root = gson.fromJson(json, JsonElement.class);
			if (root == null || !root.isJsonArray()) {
				return Collections.emptyList();
			}

			JsonArray array = root.getAsJsonArray();
			if (array.isEmpty() || !array.get(0).isJsonObject()) {
				return Collections.emptyList();
			}

			JsonObject first = array.get(0).getAsJsonObject();
			if (first.has("group") || first.has("trades")) {
				List<RawTradeGroup> rawGroups = gson.fromJson(array, new TypeToken<List<RawTradeGroup>>() {}.getType());
				return toValidGroups(rawGroups);
			}

			List<RawTradeEntry> rawList = gson.fromJson(array, new TypeToken<List<RawTradeEntry>>() {}.getType());
			return toSingleDefaultGroup(toValidTrades(rawList, "Default"));
        } catch (Exception e) {
            LOGGER.error("Failed to parse trade config JSON", e);
            return Collections.emptyList();
        }
	}

	public static void load() {
		Path configPath = getConfigPath();
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		try {
			if (!Files.exists(configPath)) {
				Files.createDirectories(configPath.getParent());
				List<TradeGroup> defaults = getDefaultTradeGroups();
				Files.writeString(configPath, gson.toJson(defaults));
				setTradeGroups(defaults);
				return;
			}

			String json = Files.readString(configPath);
			List<TradeGroup> validGroups = parseTradeGroups(json);

			if (validGroups.isEmpty()) {
				LOGGER.warn("No valid trade groups loaded, using defaults");
				setTradeGroups(getDefaultTradeGroups());
			} else {
				setTradeGroups(validGroups);
				LOGGER.info("Loaded {} trade groups and {} trades", getTradeGroups().size(), getTrades().size());
			}
		} catch (Exception e) {
			LOGGER.error("Failed to load trade config", e);
			setTradeGroups(getDefaultTradeGroups());
		}
	}

	private static List<TradeGroup> getDefaultTradeGroups() {
		List<TradeEntry> buildingTrades = new ArrayList<>();
		buildingTrades.add(new TradeEntry("minecraft:dirt", "minecraft:sand", 64, 1, 1));
		return List.of(new TradeGroup("Building", buildingTrades));
	}
}

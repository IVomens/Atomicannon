package dev.schematicannon.cannon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.schematicannon.schematic.ItemRequirement;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/** Port of Create's MaterialChecklist: tallies what a schematic needs and writes it into a book. */
public class MaterialChecklist {
	public static final int MAX_ENTRIES_PER_PAGE = 5;

	public final Object2IntMap<Item> gathered = new Object2IntArrayMap<>();
	public final Object2IntMap<Item> required = new Object2IntArrayMap<>();
	public final Object2IntMap<Item> damageRequired = new Object2IntArrayMap<>();
	public boolean blocksNotLoaded;

	public void warnBlockNotLoaded() {
		blocksNotLoaded = true;
	}

	public void require(ItemRequirement requirement) {
		if (requirement.isEmpty() || requirement.isInvalid())
			return;
		for (ItemRequirement.StackRequirement stack : requirement.getRequiredItems()) {
			if (stack.usage == ItemRequirement.ItemUseType.DAMAGE)
				putOrIncrement(damageRequired, stack.stack);
			if (stack.usage == ItemRequirement.ItemUseType.CONSUME)
				putOrIncrement(required, stack.stack);
		}
	}

	private static void putOrIncrement(Object2IntMap<Item> map, ItemStack stack) {
		Item item = stack.getItem();
		if (item == Items.AIR)
			return;
		map.put(item, map.getOrDefault(item, 0) + stack.getCount());
	}

	public void collect(ItemStack stack) {
		Item item = stack.getItem();
		if (required.containsKey(item) || damageRequired.containsKey(item))
			gathered.put(item, gathered.getOrDefault(item, 0) + stack.getCount());
	}

	public int getRequiredAmount(Item item) {
		int amount = required.getOrDefault(item, 0);
		if (damageRequired.containsKey(item))
			amount += (int) Math.ceil(damageRequired.getInt(item) / (float) new ItemStack(item).getMaxDamage());
		return amount;
	}

	public ItemStack createWrittenBook() {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> pages = new ArrayList<>();

		MutableComponent textComponent;
		if (blocksNotLoaded) {
			textComponent = Component.literal("\n" + ChatFormatting.RED);
			textComponent = textComponent.append(Component.translatable("schematicannon.materialChecklist.blocksNotLoaded"));
			pages.add(Filterable.passThrough(textComponent));
		}

		List<Item> keys = new ArrayList<>(required.keySet());
		for (Item item : damageRequired.keySet())
			if (!keys.contains(item))
				keys.add(item);
		keys.sort(Comparator.comparing(item -> item.getDescriptionId()));

		List<Item> completed = new ArrayList<>();
		int itemsWritten = 0;
		textComponent = Component.empty();
		for (Item item : keys) {
			int amount = getRequiredAmount(item);
			if (gathered.containsKey(item))
				amount -= gathered.getInt(item);

			if (amount <= 0) {
				completed.add(item);
				continue;
			}

			if (itemsWritten == MAX_ENTRIES_PER_PAGE) {
				itemsWritten = 0;
				textComponent.append(Component.literal("\n >>>").withStyle(ChatFormatting.BLUE));
				pages.add(Filterable.passThrough(textComponent));
				textComponent = Component.empty();
			}

			itemsWritten++;
			textComponent.append(entry(new ItemStack(item), amount, true));
		}

		for (Item item : completed) {
			if (itemsWritten == MAX_ENTRIES_PER_PAGE) {
				itemsWritten = 0;
				textComponent.append(Component.literal("\n >>>").withStyle(ChatFormatting.DARK_GREEN));
				pages.add(Filterable.passThrough(textComponent));
				textComponent = Component.empty();
			}

			itemsWritten++;
			textComponent.append(entry(new ItemStack(item), getRequiredAmount(item), false));
		}

		pages.add(Filterable.passThrough(textComponent));

		String title = Component.translatable("schematicannon.materialChecklist").getString();
		book.set(DataComponents.WRITTEN_BOOK_CONTENT,
			new WrittenBookContent(Filterable.passThrough(title), "Schematicannon", 0, pages, true));
		book.set(DataComponents.CUSTOM_NAME, Component.translatable("schematicannon.materialChecklist")
			.withStyle(ChatFormatting.BLUE));
		return book;
	}

	/**
	 * Writes (or updates) a clipboard checklist: one line per item still needed, with its icon and remaining amount.
	 * Items the attached inventories already fully provide drop off the list. Lines the player had ticked stay ticked,
	 * and the player's own plain-text notes are kept at the end.
	 */
	public dev.schematicannon.clipboard.ClipboardContent createClipboard(dev.schematicannon.clipboard.ClipboardContent previous) {
		java.util.Set<Item> previouslyChecked = new java.util.HashSet<>();
		List<dev.schematicannon.clipboard.ClipboardContent.Entry> notes = new ArrayList<>();
		for (var page : previous.pages())
			for (var e : page) {
				if (e.icon().isEmpty()) {
					if (!e.text().startsWith("ℹ"))
						notes.add(e);
				} else if (e.checked())
					previouslyChecked.add(e.icon().getItem());
			}

		List<dev.schematicannon.clipboard.ClipboardContent.Entry> entries = new ArrayList<>();
		if (blocksNotLoaded)
			entries.add(dev.schematicannon.clipboard.ClipboardContent.Entry.text(
				"ℹ " + Component.translatable("schematicannon.clipboard.notLoaded").getString()));

		List<Item> keys = new ArrayList<>(required.keySet());
		for (Item item : damageRequired.keySet())
			if (!keys.contains(item))
				keys.add(item);
		keys.sort(Comparator.comparing(item -> item.getDescriptionId()));

		for (Item item : keys) {
			int remaining = getRequiredAmount(item) - gathered.getOrDefault(item, 0);
			if (remaining <= 0)
				continue; // the attached inventories already cover this one
			ItemStack icon = new ItemStack(item);
			String text = remaining + "x " + icon.getHoverName().getString();
			entries.add(new dev.schematicannon.clipboard.ClipboardContent.Entry(text, previouslyChecked.contains(item), icon, remaining));
		}
		if (entries.isEmpty() || (blocksNotLoaded && entries.size() == 1))
			entries.add(dev.schematicannon.clipboard.ClipboardContent.Entry.text(
				"ℹ " + Component.translatable("schematicannon.clipboard.allGathered").getString()));
		entries.addAll(notes);

		List<List<dev.schematicannon.clipboard.ClipboardContent.Entry>> pages = new ArrayList<>();
		for (int i = 0; i < entries.size(); i += dev.schematicannon.clipboard.ClipboardContent.ENTRIES_PER_PAGE)
			pages.add(new ArrayList<>(entries.subList(i,
				Math.min(entries.size(), i + dev.schematicannon.clipboard.ClipboardContent.ENTRIES_PER_PAGE))));
		return new dev.schematicannon.clipboard.ClipboardContent(pages, 0).sanitized();
	}

	private Component entry(ItemStack item, int amount, boolean unfinished) {
		int stacks = amount / 64;
		int remainder = amount % 64;
		MutableComponent tc = Component.empty();
		tc.append(Component.translatable(item.getItem().getDescriptionId())
			.withStyle(ChatFormatting.BLACK));
		if (!unfinished)
			tc.append(" ✔");
		tc.withStyle(unfinished ? ChatFormatting.BLACK : ChatFormatting.DARK_GREEN);
		return tc.append(Component.literal("\n" + " x" + amount).withStyle(ChatFormatting.BLACK))
			.append(Component.literal(" | " + stacks + "▤ +" + remainder + "\n").withStyle(ChatFormatting.GRAY));
	}
}

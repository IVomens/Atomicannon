package dev.schematicannon.clipboard;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * What is written on a clipboard: pages of checklist entries. Entries made by the Schematicannon carry an item icon
 * and the amount still needed; entries typed by players are just text.
 */
public record ClipboardContent(List<List<Entry>> pages, int page) {
	public static final int MAX_PAGES = 50;
	public static final int ENTRIES_PER_PAGE = 10;
	public static final int MAX_TEXT = 64;

	public static final ClipboardContent EMPTY = new ClipboardContent(List.of(List.of()), 0);

	public record Entry(String text, boolean checked, ItemStack icon, int amount) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("text").forGetter(Entry::text),
			Codec.BOOL.optionalFieldOf("checked", false).forGetter(Entry::checked),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("icon", ItemStack.EMPTY).forGetter(Entry::icon),
			Codec.INT.optionalFieldOf("amount", 0).forGetter(Entry::amount)
		).apply(i, Entry::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(MAX_TEXT * 4), Entry::text,
			ByteBufCodecs.BOOL, Entry::checked,
			ItemStack.OPTIONAL_STREAM_CODEC, Entry::icon,
			ByteBufCodecs.VAR_INT, Entry::amount,
			Entry::new);

		public static Entry text(String text) {
			return new Entry(text, false, ItemStack.EMPTY, 0);
		}

		public Entry withChecked(boolean checked) {
			return new Entry(text, checked, icon, amount);
		}

		public Entry withText(String text) {
			return new Entry(text, checked, icon, amount);
		}
	}

	public static final Codec<ClipboardContent> CODEC = RecordCodecBuilder.create(i -> i.group(
		Entry.CODEC.listOf().listOf().fieldOf("pages").forGetter(ClipboardContent::pages),
		Codec.INT.optionalFieldOf("page", 0).forGetter(ClipboardContent::page)
	).apply(i, ClipboardContent::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardContent> STREAM_CODEC = StreamCodec.composite(
		Entry.STREAM_CODEC.apply(ByteBufCodecs.list(ENTRIES_PER_PAGE)).apply(ByteBufCodecs.list(MAX_PAGES)), ClipboardContent::pages,
		ByteBufCodecs.VAR_INT, ClipboardContent::page,
		ClipboardContent::new);

	public boolean isBlank() {
		for (List<Entry> p : pages)
			if (!p.isEmpty())
				return false;
		return true;
	}

	/** Mutable deep copy of the pages, for editing. */
	public List<List<Entry>> mutablePages() {
		List<List<Entry>> copy = new ArrayList<>();
		for (List<Entry> p : pages)
			copy.add(new ArrayList<>(p));
		if (copy.isEmpty())
			copy.add(new ArrayList<>());
		return copy;
	}

	/** Clamps sizes so a client can never send an oversized clipboard. */
	public ClipboardContent sanitized() {
		List<List<Entry>> out = new ArrayList<>();
		for (List<Entry> p : pages) {
			if (out.size() >= MAX_PAGES)
				break;
			List<Entry> page = new ArrayList<>();
			for (Entry e : p) {
				if (page.size() >= ENTRIES_PER_PAGE)
					break;
				String text = e.text().length() > MAX_TEXT ? e.text().substring(0, MAX_TEXT) : e.text();
				page.add(new Entry(text, e.checked(), e.icon().copyWithCount(1), Math.max(0, e.amount())));
			}
			out.add(page);
		}
		if (out.isEmpty())
			out.add(new ArrayList<>());
		return new ClipboardContent(out, Math.max(0, Math.min(page, out.size() - 1)));
	}
}

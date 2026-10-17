package dev.schematicannon.clipboard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.schematicannon.client.ClientHooks;
import dev.schematicannon.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** A clipboard with checklist pages. Right-click to read and edit; the Schematicannon can write material lists on it. */
public class ClipboardItem extends Item {

	public ClipboardItem(Properties properties) {
		super(properties);
	}

	public static ClipboardContent content(ItemStack stack) {
		return stack.getOrDefault(ModComponents.CLIPBOARD.get(), ClipboardContent.EMPTY);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide())
			ClientHooks.openClipboard(hand);
		return InteractionResult.SUCCESS;
	}

	/**
	 * Applies a player's edit. Players may change text and checkmarks and add or remove plain entries, but item icons
	 * and amounts written by the Schematicannon can only be kept (matched by page and line), never invented.
	 */
	public static void applyEdit(ItemStack stack, ClipboardContent edited) {
		ClipboardContent current = content(stack);
		ClipboardContent clean = edited.sanitized();
		List<List<ClipboardContent.Entry>> merged = new ArrayList<>();
		for (int p = 0; p < clean.pages().size(); p++) {
			List<ClipboardContent.Entry> page = new ArrayList<>();
			List<ClipboardContent.Entry> old = p < current.pages().size() ? current.pages().get(p) : List.of();
			for (int i = 0; i < clean.pages().get(p).size(); i++) {
				ClipboardContent.Entry e = clean.pages().get(p).get(i);
				ClipboardContent.Entry before = i < old.size() ? old.get(i) : null;
				boolean sameItem = before != null && !before.icon().isEmpty() && ItemStack.isSameItem(before.icon(), e.icon());
				page.add(sameItem ? new ClipboardContent.Entry(e.text(), e.checked(), before.icon(), before.amount())
					: new ClipboardContent.Entry(e.text(), e.checked(), ItemStack.EMPTY, 0));
			}
			merged.add(page);
		}
		stack.set(ModComponents.CLIPBOARD.get(), new ClipboardContent(merged, clean.page()));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		ClipboardContent content = content(stack);
		if (content.isBlank()) {
			builder.accept(Component.translatable("schematicannon.clipboard.empty").withStyle(ChatFormatting.GRAY));
			return;
		}
		int total = 0, checked = 0;
		for (var page : content.pages())
			for (var e : page) {
				total++;
				if (e.checked())
					checked++;
			}
		builder.accept(Component.translatable("schematicannon.clipboard.progress", checked, total).withStyle(ChatFormatting.GRAY));
	}
}

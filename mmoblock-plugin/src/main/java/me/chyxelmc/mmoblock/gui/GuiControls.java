package me.chyxelmc.mmoblock.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

/** Ready-made controls for paged, scrolling, and tabbed interfaces. */
public final class GuiControls {
    public static GuiItem nextPage(final ItemStack icon) {
        return GuiItem.of(icon, click -> click.view().nextPage());
    }

    public static GuiItem previousPage(final ItemStack icon) {
        return GuiItem.of(icon, click -> click.view().previousPage());
    }

    public static GuiItem scrollForward(final ItemStack icon, final int amount) {
        return GuiItem.of(icon, click -> click.view().scroll(Math.max(1, amount)));
    }

    public static GuiItem scrollBackward(final ItemStack icon, final int amount) {
        return GuiItem.of(icon, click -> click.view().scroll(-Math.max(1, amount)));
    }

    public static GuiItem tab(final ItemStack icon, final GuiEngine engine, final Gui target) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(target, "target");
        return GuiItem.of(icon, click -> engine.open(click.player(), target));
    }

    public static GuiItem tab(final ItemStack icon, final TabGui tabs, final int tab) {
        Objects.requireNonNull(tabs, "tabs");
        return GuiItem.of(icon, click -> tabs.setTab(tab));
    }

    public static List<GuiItem> tabs(
            final GuiEngine engine,
            final List<Gui> targets,
            final List<ItemStack> icons
    ) {
        if (targets.size() != icons.size()) {
            throw new IllegalArgumentException("Every tab needs one icon and one target GUI");
        }
        return java.util.stream.IntStream.range(0, targets.size())
                .mapToObj(index -> tab(icons.get(index), engine, targets.get(index)))
                .toList();
    }

    private GuiControls() {
    }
}

package me.chyxelmc.mmoblock.gui.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Typed synchronous event bus supporting superclass subscriptions and deterministic priority. */
public final class GuiEventBus {
    private final List<Registration<?>> registrations = new CopyOnWriteArrayList<>();

    public <E extends GuiEvent> GuiEventSubscription subscribe(final Class<E> type, final GuiEventPriority priority, final boolean receiveCancelled, final Consumer<E> listener) {
        final Registration<E> registration = new Registration<>(type, priority, receiveCancelled, listener);
        this.registrations.add(registration);
        return () -> this.registrations.remove(registration);
    }

    public <E extends GuiEvent> GuiEventSubscription subscribe(final Class<E> type, final Consumer<E> listener) {
        return subscribe(type, GuiEventPriority.NORMAL, false, listener);
    }

    public <E extends GuiEvent> E publish(final E event) {
        final List<Registration<?>> matching = new ArrayList<>();
        for (final Registration<?> registration : this.registrations) if (registration.type.isInstance(event)) matching.add(registration);
        matching.sort(Comparator.comparing(registration -> registration.priority));
        for (final Registration<?> registration : matching) {
            if (event instanceof CancellableGuiEvent cancellable && cancellable.cancelled() && !registration.receiveCancelled) continue;
            registration.invoke(event);
        }
        return event;
    }

    private record Registration<E extends GuiEvent>(Class<E> type, GuiEventPriority priority, boolean receiveCancelled, Consumer<E> listener) {
        private void invoke(final GuiEvent event) { this.listener.accept(this.type.cast(event)); }
    }
}

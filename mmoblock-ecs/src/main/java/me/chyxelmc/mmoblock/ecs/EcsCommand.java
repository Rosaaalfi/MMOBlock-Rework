package me.chyxelmc.mmoblock.ecs;

/**
 * Command submitted by external OOP/Bukkit code to be executed on the ECS tick.
 */
@FunctionalInterface
public interface EcsCommand {

    void execute(EntityManager entityManager);
}

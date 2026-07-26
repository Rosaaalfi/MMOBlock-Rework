package me.chyxelmc.mmoblock.runtime.block;

public record ReconcileResult(int reboundInteractions, int cleanedMissingDefinitions, int rescheduledRespawns, int failedRebinds) {
}

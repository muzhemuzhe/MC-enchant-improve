package dev.differentenchant.automation;

import net.minecraft.server.level.ServerPlayer;

/** Synchronous server interaction scope; not persisted and never guessed from nearby players. */
public final class AssemblyActor {
    private static final ThreadLocal<ServerPlayer> CURRENT=new ThreadLocal<>();
    public static ServerPlayer current(){return CURRENT.get();}
    public static ServerPlayer enter(ServerPlayer player){var previous=CURRENT.get();CURRENT.set(player);return previous;}
    public static void restore(ServerPlayer previous){if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    private AssemblyActor(){}
}

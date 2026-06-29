/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.event;

import baritone.Baritone;
import baritone.api.event.events.*;
import baritone.api.event.events.type.EventState;
import baritone.api.event.listener.IEventBus;
import baritone.api.event.listener.IGameEventListener;
import baritone.api.utils.Helper;
import baritone.api.utils.Pair;
import baritone.cache.CachedChunk;
import baritone.cache.WorldProvider;
import baritone.pathing.path.PathExecutor;
import baritone.utils.BlockStateInterface;
import baritone.utils.RenderDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Brady
 * @since 7/31/2018
 */
public final class GameEventHandler implements IEventBus, Helper {

    private final Baritone baritone;

    private final List<IGameEventListener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean renderReady;
    private volatile ClientLevel renderWorld;
    private long renderCalls;
    private long lastRenderDiagnosticNanos;
    private int expectedListenerCount = -1;

    public GameEventHandler(Baritone baritone) {
        this.baritone = baritone;
    }

    @Override
    public final void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.IN) {
            try {
                baritone.bsi = new BlockStateInterface(baritone.getPlayerContext(), true);
            } catch (Exception ex) {
                ex.printStackTrace();
                baritone.bsi = null;
            }
        } else {
            baritone.bsi = null;
        }
        listeners.forEach(l -> l.onTick(event));
    }

    @Override
    public void onPostTick(TickEvent event) {
        listeners.forEach(l -> l.onPostTick(event));
    }

    @Override
    public final void onPlayerUpdate(PlayerUpdateEvent event) {
        listeners.forEach(l -> l.onPlayerUpdate(event));
    }

    @Override
    public final void onSendChatMessage(ChatEvent event) {
        listeners.forEach(l -> l.onSendChatMessage(event));
    }

    @Override
    public void onPreTabComplete(TabCompleteEvent event) {
        listeners.forEach(l -> l.onPreTabComplete(event));
    }

    @Override
    public void onChunkEvent(ChunkEvent event) {
        EventState state = event.getState();
        ChunkEvent.Type type = event.getType();

        Level world = baritone.getPlayerContext().world();

        // Whenever the server sends us to another dimension, chunks are unloaded
        // technically after the new world has been loaded, so we perform a check
        // to make sure the chunk being unloaded is already loaded.
        boolean isPreUnload = state == EventState.PRE
                && type == ChunkEvent.Type.UNLOAD
                && world.getChunkSource().getChunk(event.getX(), event.getZ(), null, false) != null;

        if (event.isPostPopulate() || isPreUnload) {
            baritone.getWorldProvider().ifWorldLoaded(worldData -> {
                LevelChunk chunk = world.getChunk(event.getX(), event.getZ());
                worldData.getCachedWorld().queueForPacking(chunk);
            });
        }


        listeners.forEach(l -> l.onChunkEvent(event));
    }

    @Override
    public void onBlockChange(BlockChangeEvent event) {
        if (Baritone.settings().repackOnAnyBlockChange.value) {
            final boolean keepingTrackOf = event.getBlocks().stream()
                    .map(Pair::second).map(BlockState::getBlock)
                    .anyMatch(CachedChunk.BLOCKS_TO_KEEP_TRACK_OF::contains);

            if (keepingTrackOf) {
                baritone.getWorldProvider().ifWorldLoaded(worldData -> {
                    final Level world = baritone.getPlayerContext().world();
                    ChunkPos pos = event.getChunkPos();
                    worldData.getCachedWorld().queueForPacking(world.getChunk(pos.x(), pos.z()));
                });
            }
        }

        listeners.forEach(l -> l.onBlockChange(event));
    }

    @Override
    public final void onRenderPass(RenderEvent event) {
        Minecraft minecraft = baritone.getPlayerContext().minecraft();
        if (!Baritone.settings().renderBaritoneVisuals.value
                || !renderReady
                || !minecraft.isRunning()
                || minecraft.level == null
                || minecraft.player == null
                || minecraft.level != renderWorld
                || baritone.getPlayerContext().world() != renderWorld
                || baritone.getPlayerContext().player() == null) {
            return;
        }

        renderCalls++;
        listeners.forEach(l -> l.onRenderPass(event));

        if (Baritone.settings().renderDiagnostics.value) {
            long now = System.nanoTime();
            if (now - lastRenderDiagnosticNanos >= 30_000_000_000L) {
                lastRenderDiagnosticNanos = now;
                System.out.println("[Baritone render] " + RenderDiagnostics.snapshot(
                        renderCalls,
                        listeners.size(),
                        pathLength(baritone.getPathingBehavior().getCurrent()),
                        pathLength(baritone.getPathingBehavior().getNext())
                ));
            }
        }
    }

    @Override
    public final void onWorldEvent(WorldEvent event) {
        WorldProvider cache = baritone.getWorldProvider();

        if (event.getState() == EventState.PRE) {
            renderReady = false;
            renderWorld = null;
            baritone.getSelectionManager().removeAllSelections();
        }

        if (event.getState() == EventState.POST) {
            cache.closeWorld();
            if (event.getWorld() != null) {
                cache.initWorld(event.getWorld());
            }
        }

        listeners.forEach(l -> l.onWorldEvent(event));

        if (event.getState() == EventState.POST) {
            if (RenderDiagnostics.liveBuffers() != 0) {
                System.err.println("[Baritone render] World changed with " + RenderDiagnostics.liveBuffers() + " native render buffers still live");
            }
            if (event.getWorld() != null) {
                if (expectedListenerCount == -1) {
                    expectedListenerCount = listeners.size();
                } else if (expectedListenerCount != listeners.size()) {
                    System.err.println("[Baritone render] Event handler count changed across world loads: expected="
                            + expectedListenerCount + ", actual=" + listeners.size());
                }
                renderWorld = event.getWorld();
                renderReady = baritone.getPlayerContext().world() == event.getWorld();
            }
        }
    }

    @Override
    public final void onSendPacket(PacketEvent event) {
        listeners.forEach(l -> l.onSendPacket(event));
    }

    @Override
    public final void onReceivePacket(PacketEvent event) {
        listeners.forEach(l -> l.onReceivePacket(event));
    }

    @Override
    public void onPlayerRotationMove(RotationMoveEvent event) {
        listeners.forEach(l -> l.onPlayerRotationMove(event));
    }

    @Override
    public void onPlayerSprintState(SprintStateEvent event) {
        listeners.forEach(l -> l.onPlayerSprintState(event));
    }

    @Override
    public void onBlockInteract(BlockInteractEvent event) {
        listeners.forEach(l -> l.onBlockInteract(event));
    }

    @Override
    public void onPlayerDeath() {
        listeners.forEach(IGameEventListener::onPlayerDeath);
    }

    @Override
    public void onPathEvent(PathEvent event) {
        listeners.forEach(l -> l.onPathEvent(event));
    }

    @Override
    public final void registerEventListener(IGameEventListener listener) {
        if (this.listeners.contains(listener)) {
            throw new IllegalStateException("Baritone event listener registered twice: " + listener.getClass().getName());
        }
        this.listeners.add(listener);
    }

    public int getListenerCount() {
        return listeners.size();
    }

    private static int pathLength(PathExecutor executor) {
        return executor == null || executor.getPath() == null ? 0 : executor.getPath().positions().size();
    }
}

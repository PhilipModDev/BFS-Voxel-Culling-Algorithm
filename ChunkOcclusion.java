package com.dawnfall.engine.Server.world.chunkUtil;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint3;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.dawnfall.engine.Client.rendering.mesh.ChunkMesh;
import com.dawnfall.engine.DawnStar;
import com.dawnfall.engine.Server.entity.Player;
import com.dawnfall.engine.Server.util.IO.OptionProvider;
import com.dawnfall.engine.Server.util.math.Direction;
import com.dawnfall.engine.Server.world.World;

import java.util.*;

//Chunk culling algorithm by PhilipModDev.
public class ChunkOcclusion {
    private static final World world = World.getWorld();
    private static final float STEPS = 0.05f;
    private final int MAX_CULLING = (int) Math.pow(20,3) / 2;
    private final ChunkManager chunkManager;

    public ChunkOcclusion(ChunkManager chunkManager){
        this.chunkManager = chunkManager;
        this.player = DawnStar.getInstance().getWorldRenderer().player;
        rayCastPoints = this.chunkManager.generateSpherePoints(world.RENDER_DISTANCE);
        DawnStar.getInstance().shapeRenderer.setColor(Color.RED);
    }
    public synchronized void occlusionGridCheck() {
        if (OptionProvider.DEBUG_MODE){
            Array<ChunkMesh> updateBuffer = chunkManager.worldRenderer.getCurrentBuffer();
            for (ChunkMesh chunkMesh: updateBuffer) {
                Chunk chunk = chunkMesh.getChunk();
                if (!chunkManager.isChunkVisible(chunk)){
                    chunk.setChunkVisible(false);
                }
                chunk.checked = false;
            }
            cullOcclusionChunks();
        }else {
            Chunk playerChunk = world.getChunkAtPlayer();
            if (playerChunk != null) {
                Queue<Chunk> queue = new ArrayDeque<>();
                Direction fromFace;
                queue.add(playerChunk);
                if (chunkManager.isPlayerChangeChunk()) {
                    Array<ChunkMesh> updateBuffer = chunkManager.worldRenderer.getCurrentBuffer();
                    for (ChunkMesh chunkMesh : updateBuffer) {
                        Chunk chunk = chunkMesh.getChunk();
                        if (!chunkManager.isChunkVisible(chunk)) {
                            chunk.setChunkVisible(false);
                        }
                        chunk.checked = false;
                    }
                    int steps = 0;
                    while (!queue.isEmpty()) {
                        Chunk chunkA = queue.poll();
                        if (!chunkA.meshBuilt || chunkA.isSolid) continue;
                        if (chunkA != playerChunk && chunkA.checked) continue;
                        chunkA.checked = true;
                        for (Direction direction : Direction.values()) {
                            fromFace = Direction.getOpposite(direction);
                            Chunk chunkB = world.getChunkAtDirection(chunkA,direction);
                            if (chunkB != null && chunkB.meshBuilt) {
                                if (chunkA.visibilityInfo.isFaceVisible(direction)) { // point directions.
                                    if (chunkB.visibilityInfo.isFaceVisible(fromFace)) { // back direction.
                                        chunkB.setChunkVisible(true);
                                        if (steps > MAX_CULLING) return;
                                        else steps++;
                                        queue.add(chunkB);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    public static class VisibilityInfo {
        private static final int FACE_LENGTH = Direction.values().length;
        private final BitSet FACES = new BitSet(FACE_LENGTH * FACE_LENGTH);
        public void setFaceVisible(Direction directionA,Direction directionB) {
            FACES.set(directionA.ordinal() + directionB.ordinal() * FACE_LENGTH);
            FACES.set(directionB.ordinal() + directionA.ordinal() * FACE_LENGTH);
        }
        public void setFacesVisible(Set<Direction> directions) {
            for (Direction direction : directions) {
                for (Direction direction1: directions) {
                    setFaceVisible(direction,direction1);
                }
            }
        }
        public void setAll(boolean pVisible) {
            this.FACES.set(0, this.FACES.size(), pVisible);
        }
        public boolean isVisibleBetween(Direction directionA,Direction directionB) {
            return FACES.get(directionB.ordinal() + directionA.ordinal() * FACE_LENGTH);
        }
        public boolean isFaceVisible(Direction direction) {
            return this.FACES.get(direction.ordinal() + direction.ordinal() * FACE_LENGTH);
        }
    }
}

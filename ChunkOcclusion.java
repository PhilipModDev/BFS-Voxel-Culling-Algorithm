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
//Creator of idea Tyron.
//Based on the Vintage Story culling system. https://github.com/tyronx/occlusionculling/blob/master/ChunkCuller.cs
public class ChunkOcclusion {
    private static final World world = World.getWorld();
    private static final float STEPS = 0.05f;
    private final int MAX_CULLING = (int) Math.pow(20,3) / 2;
    private final Vector3 point = new Vector3();
    private final Vector3 currentPos = new Vector3();
    private final ArrayList<ChunkPos> rayCastPoints;
    private final Ray ray = new Ray();
    private final ChunkManager chunkManager;
    private final Player player;

    public ChunkOcclusion(ChunkManager chunkManager){
        this.chunkManager = chunkManager;
        this.player = DawnStar.getInstance().getWorldRenderer().player;
        rayCastPoints = this.chunkManager.generateSpherePoints(world.RENDER_DISTANCE);
        DawnStar.getInstance().shapeRenderer.setColor(Color.RED);
    }
    private void cullOcclusionChunks(){
        if (rayCastPoints != null){
            GridPoint3 currentChunk = this.chunkManager.playerChunk;
            if (currentChunk == null) return;
            currentPos.set(currentChunk.x,currentChunk.y,currentChunk.z);
            for (ChunkPos chunkPos : rayCastPoints) {
                if (chunkPos == null) continue;
                point.set(chunkPos.x + currentChunk.x,chunkPos.y + currentChunk.y,chunkPos.z + currentChunk.z);
                //Starts the ray cast.
                rayTraversable(currentPos,point);
            }
        }
    }
    private void rayTraversable(Vector3 startPoint, Vector3 pointEnd){
        ray.origin.set(startPoint);
        //Sets the ray's direction.
        ray.direction.set(startPoint).sub(pointEnd).nor().scl(STEPS);
        //Calculates the distance between the start point and the end point.
        float distance = pointEnd.dst(startPoint);
        //Casts the ray towards the direction of the end point.
        for (float i = 0; i < distance; i += STEPS) {
            Chunk chunk = world.getChunkAt(ray.origin);
            if (chunk != null && chunk.visibilityInfo != null) {
                chunk.setChunkVisible(true);
                Direction to = Direction.vec3ToDirection(ray.direction);
                Direction from = Direction.getOpposite(to);
                if (chunk.visibilityInfo.isFaceVisible(to)){
                    Chunk chunkA =  world.getChunkAtDirection(chunk,to);
                    if (chunkA != null && chunkA.meshBuilt){
                        if (chunkA.visibilityInfo.isFaceVisible(from)){
                            //Adds the ray's current position with the ray update direction.
                            ray.origin.add(ray.direction);
                            continue;
                        }
                    }
                }
            }
            return;
        }
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
//                        Direction playerDirection = Direction.vec3ToDirection(player.getCamera().direction);
//                        if (direction.equals(Direction.getOpposite(playerDirection))) continue;
                            //Gets chunk at direction.
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

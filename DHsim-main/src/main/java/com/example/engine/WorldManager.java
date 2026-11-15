package com.example.engine;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class WorldManager {

    public static final class TreeData {
        public final HouseInstance instance;
        public final float x, z;
        public final float originalScale;
        public float currentScale;
        public boolean depleted;
        public boolean reserved = false;
        public float yaw;
        public float yOffset;

        public TreeData(HouseInstance inst, float x, float z, float yaw,
                        float originalScale, float yOffset) {
            this.instance = inst;
            this.x = x; this.z = z;
            this.yaw = yaw;
            this.yOffset = yOffset;
            this.originalScale = originalScale;
            this.currentScale = originalScale;
        }
    }

    public final List<TreeData> trees = new ArrayList<>();
    public final List<Tribe> tribes = new ArrayList<>();

    public ObjModel protoWalls, protoRoof, protoDoor, protoWindows;
    public float protoScale = 1f;

    public Runnable onTerrainDirty;

    private final Random rng = new Random(1337);

    public void registerTrees(List<TreeData> list) { trees.addAll(list); }

    public static float dist2D(float ax, float az, float bx, float bz) {
        float dx = ax - bx, dz = az - bz;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    public TreeData findNearestTree(float fromX, float fromZ, float maxDist) {
        TreeData best = null;
        float bestD = maxDist;
        for (TreeData t : trees) {
            if (t.depleted || t.reserved) continue;
            float d = dist2D(fromX, fromZ, t.x, t.z);
            if (d < bestD) { bestD = d; best = t; }
        }
        return best;
    }

    public void chopTree(TreeData t, float progress) {
        float s = t.originalScale * Math.max(0.05f, 1f - progress * 0.95f);
        t.currentScale = s;
        t.instance.setTransform(t.x, t.yOffset, t.z, t.yaw, s);
    }

    public int finishChop(TreeData t) {
        if (t.depleted) return 0;
        t.depleted = true;
        t.instance.setTransform(t.x, t.yOffset, t.z, t.yaw, 0.001f);
        return 3 + rng.nextInt(3);
    }

    public HouseInstance buildHouseForTribe(Tribe tribe) {
        if (protoWalls == null || protoRoof == null || protoDoor == null || protoWindows == null) {
            return null;
        }
        if (tribe.wood < 10) return null;

        for (int attempt = 0; attempt < 30; attempt++) {
            float angle = rng.nextFloat() * (float) (Math.PI * 2);
            float r = 12f + tribe.houses.size() * 3f + rng.nextFloat() * 6f;
            float hx = tribe.center.x + (float) Math.cos(angle) * r;
            float hz = tribe.center.z + (float) Math.sin(angle) * r;

            if (Math.abs(hx) > 140f || Math.abs(hz) > 140f) continue;

            float hy = Ground.getHeightAt(hx, hz);
            if (hy < Ground.WATER_LEVEL + 0.8f) continue;
            if (hy > 10f) continue;

            boolean bad = false;
            for (Tribe t : tribes) {
                for (HouseInstance h : t.houses) {
                    float dx = h.x - hx, dz = h.z - hz;
                    if (dx * dx + dz * dz < 12f * 12f) { bad = true; break; }
                }
                if (bad) break;
            }
            if (bad) continue;

            // Prevent house from being built inside tree branches
            for (TreeData t : trees) {
                if (t.depleted) continue;
                float dx = t.x - hx, dz = t.z - hz;
                if (dx * dx + dz * dz < 15f * 15f) { bad = true; break; }
            }
            if (bad) continue;

            tribe.wood -= 10;
            Ground.addPad(hx, hz, 6f, 4f);
            if (onTerrainDirty != null) onTerrainDirty.run();

            float yaw = (float) Math.atan2(-hx, -hz);
            HouseInstance inst = new HouseInstance(protoWalls, protoRoof, protoDoor, protoWindows,
                    hx, hy, hz, yaw, protoScale);
            tribe.houses.add(inst);
            return inst;
        }
        return null;
    }
}

package com.example.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

/**
 * Lifecycle + Tribe building.
 *
 * Anti-runaway-population guards:
 *  - MAX_POPULATION hard cap (impregnate() refuses if at/above cap).
 *  - Birth cooldown on females after giving birth.
 *  - Lifespan: adults die of old age.
 *  - Starvation: if hunger stays at 0 for 30 s, the adult dies.
 *  - Tribe-aware house building (only builds if the TRIBE needs a house).
 */
public final class Eater {

    public enum Type { TEEN, FAT, FEMALE, PREGNANT, BABY, YOUNG_BOY, MUSCULAR }

    private enum State {
        SEARCH, WALK, EAT, REST,
        WANDER, SEEK_FEMALE, SATISFY,
        GESTATING, PREGNANT_WAIT,
        SEEK_MOM, SUCKLE,
        SEEK_TREE, CHOP,
        SEEK_BUILD, BUILD
    }

    private static Texture[] typeTextures;
    private static final float[] TYPE_HEIGHTS = {
            1.50f, 1.65f, 1.50f, 1.50f, 0.85f, 1.15f, 1.75f
    };

    public static void initTextures(Texture[] textures) { typeTextures = textures; }

    // ---- Balance ----
    public static final int MAX_POPULATION = 60;
    private static final float BIRTH_COOLDOWN = 120f;
    private static final float ADULT_LIFESPAN_MIN = 900f;   // 15 min
    private static final float ADULT_LIFESPAN_MAX = 1500f;  // 25 min
    private static final float STARVE_TIMER = 30f;

    private static final String SFX_EAT           = "mp3/eating-champ-with-burp.mp3";
    private static final String SFX_MEOW          = "mp3/miau-muzhskoi.mp3";
    private static final String SFX_KISS          = "mp3/zvuk-poceluya.mp3";
    private static final String SFX_BIRTH         = "mp3/scream_female_giving_birth-c.mp3";
    private static final String SFX_BABY_CRY      = "mp3/baby-cry_fyco-2nu.mp3";
    private static final String SFX_CROWD         = "mp3/032635556b5d512.mp3";
    private static final String SFX_FIGHT         = "mp3/fight-hit-moan-shouts_my4oxhv_.mp3";
    private static final String SFX_SUCKLE        = "mp3/a9c8eebae9d0e23.mp3";
    private static final String SFX_MAN_SCREAM    = "mp3/man-screaming-screaming-man.mp3";
    private static final String SFX_FEMALE_SCREAM = "mp3/female-scream-of-horror.mp3";
    private static final String SFX_CHOP          = "mp3/fight-hit-moan-shouts_my4oxhv_.mp3";

    private static final float EAT_TIME            = 8.0f;
    private static final float REST_MIN            = 30.0f;
    private static final float REST_MAX            = 60.0f;
    private static final float GLUTTON_REST_MIN    = 5.0f;
    private static final float GLUTTON_REST_MAX    = 10.0f;
    private static final float YB_REST_MIN         = 15.0f;
    private static final float YB_REST_MAX         = 30.0f;
    private static final float SEARCH_RADIUS       = 60.0f;
    private static final float TREE_SEARCH_RADIUS  = 50.0f;
    private static final float EAT_DISTANCE        = 1.0f;
    private static final float TREE_DISTANCE       = 1.5f;
    private static final float MALE_WALK_SPEED     = 2.4f;
    private static final float CHASE_SPEED         = 3.5f;
    private static final float FEMALE_WALK_SPEED   = 1.6f;
    private static final float BABY_WALK_SPEED     = 0.9f;
    private static final float SEEK_FEMALE_RADIUS  = 18.0f;
    private static final float SATISFY_DISTANCE    = 1.3f;
    private static final float SATISFY_TIME        = 3.0f;
    private static final float GESTATION_TIME      = 60.0f;
    private static final float PREGNANT_TIME       = 30.0f;
    private static final float BABY_GROW_TIME      = 120.0f;
    private static final float YOUNG_BOY_GROW_TIME = 180.0f;
    private static final int   MATURITY_MEALS      = 3;
    private static final float GLUTTON_CHANCE      = 0.5f;
    private static final float SUCKLE_TIME         = 5.0f;
    private static final float SUCKLE_RANGE        = 1.5f;
    private static final int   SUCKLE_YB_ODDS      = 50;
    private static final long  CROWD_COOLDOWN_MS   = 10_000L;

    private static final float CHOP_TIME           = 5.0f;
    private static final float BUILD_TIME          = 3.0f;
    private static final float HUNGER_DROP_RATE    = 3.0f;

    private static long lastCrowdSoundMs = 0L;

    private Type type;
    private State state;
    private Sprite sprite;
    private float currentHeight = 1.5f;

    private final Random rng;
    private final Vector3f pos = new Vector3f();
    private Tribe tribe;
    private float hunger = 100f;
    private float starveAccum = 0f;

    private float age = 0f;
    private float lifespan = 0f;
    private float birthCooldown = 0f;
    private boolean dead = false;

    private float timer, stateTimer, growTimer, pregnancyTimer;

    private Poop target;
    private Eater targetFemale;
    private WorldManager.TreeData targetTree;
    private final Vector3f walkTo = new Vector3f();

    private int mealsEaten;
    private boolean gluttonous;
    private int fatThreshold;
    private boolean mature;

    private int kissCount;
    private int muscleThreshold;

    private Eater mother;
    private float suckleTimer;
    private boolean suckleAllowed;
    private float cryTimer = -1f;

    private Audio.Handle eatSound;
    private Vector3f camPos = new Vector3f();
    private List<Eater> worldEaters;

    public Eater(Type type, float x, float z, Tribe tribe, Random rng) {
        this.type = type;
        this.rng = rng;
        this.pos.set(x, Ground.getHeightAt(x, z), z);
        this.tribe = tribe;
        this.gluttonous = rng.nextFloat() < GLUTTON_CHANCE;
        this.fatThreshold = 5 + rng.nextInt(6);
        this.muscleThreshold = 4 + rng.nextInt(2);
        this.lifespan = ADULT_LIFESPAN_MIN + rng.nextFloat() * (ADULT_LIFESPAN_MAX - ADULT_LIFESPAN_MIN);
        initStateForType();
        rebuildSprite();
        syncSpritePosition();
    }

    public void setTribe(Tribe t) { this.tribe = t; }
    public Tribe getTribe() { return tribe; }
    public Type type() { return type; }
    public Sprite sprite() { return sprite; }
    public Vector3f position() { return pos; }
    public boolean isDead() { return dead; }

    public void setMother(Eater m, boolean keepSuckleInYoungBoy) {
        this.mother = m;
        this.suckleAllowed = keepSuckleInYoungBoy;
        if (m != null) {
            this.suckleTimer = 30f + rng.nextFloat() * 100f;
            this.cryTimer    = 2f  + rng.nextFloat() * 8f;
        }
    }

    public void render(ShaderProgram shader, Matrix4f view, Matrix4f projection,
                       Vector3f cameraPos, float time) {
        if (sprite != null && !dead) sprite.render(shader, view, projection, cameraPos, time);
    }

    public void renderShadow(ShaderProgram shadowShader, Matrix4f lightVP,
                             Vector3f lightDir, float time) {
        if (sprite != null && !dead) sprite.renderShadow(shadowShader, lightVP, lightDir, time);
    }

    public void update(float dt, List<Poop> poops, List<Eater> allEaters,
                       List<Eater> newbornsOut, WorldManager world, Vector3f cameraPos) {
        if (dead) return;
        this.camPos = cameraPos;
        this.worldEaters = allEaters;

        // Aging & death
        age += dt;
        if (birthCooldown > 0) birthCooldown -= dt;

        boolean isAdult = type == Type.TEEN || type == Type.FAT
                || type == Type.FEMALE || type == Type.MUSCULAR;
        if (isAdult) {
            hunger -= HUNGER_DROP_RATE * dt;
            if (hunger <= 0f) {
                hunger = 0f;
                starveAccum += dt;
                if (starveAccum >= STARVE_TIMER) {
                    dead = true;
                    return;
                }
            } else {
                starveAccum = 0f;
            }

            if (age >= lifespan) {
                dead = true;
                return;
            }
        }

        if (cryTimer > 0f) {
            cryTimer -= dt;
            if (cryTimer <= 0f) { Audio.playOnce(SFX_BABY_CRY, volumeFor()); cryTimer = -1f; }
        }

        switch (type) {
            case TEEN, FAT -> updateAdultMale(dt, poops, allEaters, world);
            case MUSCULAR  -> updateMuscular(dt, world);
            case FEMALE    -> updateFemale(dt, newbornsOut, world);
            case PREGNANT  -> updatePregnant(dt, newbornsOut, allEaters.size());
            case BABY      -> updateBaby(dt);
            case YOUNG_BOY -> updateYoungBoy(dt, poops, allEaters);
        }

        pos.y = Ground.getHeightAt(pos.x, pos.z);
        syncSpritePosition();
    }

    private void syncSpritePosition() {
        if (sprite != null) sprite.setPosition(pos.x, pos.y + currentHeight * 0.5f, pos.z);
    }

    private float volumeFor() {
        float dx = camPos.x - pos.x, dy = camPos.y - pos.y, dz = camPos.z - pos.z;
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Math.max(0.05f, Math.min(1f, 10f / (10f + d)));
    }

    private int countTribeMembers(List<Eater> allEaters) {
        if (tribe == null) return 0;
        int n = 0;
        for (Eater e : allEaters) if (!e.dead && e.tribe == tribe) n++;
        return n;
    }

    // ====================================================================
    private void updateAdultMale(float dt, List<Poop> poops, List<Eater> allEaters,
                                  WorldManager world) {
        switch (state) {
            case SEARCH -> {
                timer -= dt;
                if (timer > 0f) break;

                if (hunger < 40f) {
                    Poop best = null;
                    float bestD2 = SEARCH_RADIUS * SEARCH_RADIUS;
                    boolean fatOnly = (type == Type.FAT);
                    for (Poop p : poops) {
                        if (p.eaten) continue;
                        if (fatOnly && !p.big) continue;
                        float dx = p.x - pos.x, dz = p.z - pos.z;
                        float d2 = dx * dx + dz * dz;
                        if (d2 < bestD2) { bestD2 = d2; best = p; }
                    }
                    if (best != null) {
                        target = best;
                        state = State.WALK;
                        break;
                    }
                }

                int tribePop = countTribeMembers(allEaters);
                if (tribe != null && tribe.canBuild() && tribe.needsHouse(tribePop)) {
                    state = State.SEEK_BUILD;
                    break;
                }

                WorldManager.TreeData t = world.findNearestTree(pos.x, pos.z, TREE_SEARCH_RADIUS);
                if (t != null) {
                    t.reserved = true;
                    targetTree = t;
                    state = State.SEEK_TREE;
                    break;
                }

                target = null;
                pickWalkTarget();
                state = State.WALK;
            }
            case WALK -> {
                if (target != null && target.eaten) { target = null; state = State.SEARCH; timer = 0f; break; }
                float dx, dz, stopAt;
                if (target != null) { dx = target.x - pos.x; dz = target.z - pos.z; stopAt = EAT_DISTANCE; }
                else                { dx = walkTo.x - pos.x; dz = walkTo.z - pos.z; stopAt = 0.4f; }
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                if (dist <= stopAt) {
                    if (target != null) startEating();
                    else { state = State.SEARCH; timer = 0.5f + rng.nextFloat(); }
                    break;
                }
                float step = Math.min(MALE_WALK_SPEED * dt, dist);
                pos.x += dx / dist * step;
                pos.z += dz / dist * step;
            }
            case EAT -> updateEating(dt);
            case REST -> updateRest(dt, allEaters);
            case SEEK_FEMALE -> updateSeekFemale(dt);
            case SATISFY -> updateSatisfy(dt);
            case SEEK_TREE -> updateSeekTree(dt, world);
            case CHOP -> updateChop(dt, world);
            case SEEK_BUILD -> updateSeekBuild(dt, world);
            case BUILD -> updateBuild(dt, world);
            default -> { state = State.SEARCH; timer = 0f; }
        }
    }

    private void startEating() {
        state = State.EAT;
        timer = EAT_TIME;
        hunger = 100f;
        starveAccum = 0f;
        eatSound = Audio.playLoop(SFX_EAT, EAT_TIME, volumeFor());
    }

    private void updateEating(float dt) {
        timer -= dt;
        if (sprite != null) {
            float progress = 1f - Math.max(timer, 0f) / EAT_TIME;
            sprite.setScale(1f + 0.12f * (float) Math.sin(progress * Math.PI * 16f));
        }
        if (timer > 0f) return;
        if (eatSound != null) { eatSound.stop(); eatSound = null; }
        if (sprite != null) sprite.setScale(1f);

        boolean brightEaten = false;
        if (target != null) { brightEaten = target.bright; target.eaten = true; target = null; }
        mealsEaten++;

        if (brightEaten) {
            float vol = volumeFor();
            Audio.playOnce(SFX_MAN_SCREAM, vol);
            Audio.playOnce(SFX_FEMALE_SCREAM, vol);
            becomeType(Type.FEMALE);
            return;
        }
        if (type == Type.TEEN && gluttonous && mealsEaten >= fatThreshold) becomeType(Type.FAT);
        if (mealsEaten >= MATURITY_MEALS) mature = true;

        Audio.playOnce(SFX_MEOW, volumeFor());
        state = State.REST;
        timer = gluttonous
                ? GLUTTON_REST_MIN + rng.nextFloat() * (GLUTTON_REST_MAX - GLUTTON_REST_MIN)
                : REST_MIN + rng.nextFloat() * (REST_MAX - REST_MIN);
    }

    private void updateRest(float dt, List<Eater> allEaters) {
        timer -= dt;
        if (mature && rng.nextFloat() < 0.3f * dt) {
            Eater best = null;
            float bestD2 = SEEK_FEMALE_RADIUS * SEEK_FEMALE_RADIUS;
            for (Eater e : allEaters) {
                if (e == this || e.dead || e.type != Type.FEMALE || e.isPregnantOrGestating()) continue;
                if (e.birthCooldown > 0) continue;
                float dx = e.pos.x - pos.x, dz = e.pos.z - pos.z;
                float d2 = dx * dx + dz * dz;
                if (d2 < bestD2) { bestD2 = d2; best = e; }
            }
            if (best != null) { targetFemale = best; state = State.SEEK_FEMALE; return; }
        }
        if (timer <= 0f) { state = State.SEARCH; timer = 0f; }
    }

    private void updateSeekFemale(float dt) {
        if (targetFemale == null || targetFemale.dead || targetFemale.type != Type.FEMALE
                || targetFemale.isPregnantOrGestating()) {
            targetFemale = null; state = State.REST; timer = 5f + rng.nextFloat() * 5f; return;
        }
        float dx = targetFemale.pos.x - pos.x, dz = targetFemale.pos.z - pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist > SEEK_FEMALE_RADIUS * 1.6f) {
            targetFemale = null; state = State.REST; timer = 5f + rng.nextFloat() * 5f; return;
        }
        if (dist <= SATISFY_DISTANCE) { state = State.SATISFY; stateTimer = SATISFY_TIME; return; }
        float step = Math.min(MALE_WALK_SPEED * dt, dist);
        pos.x += dx / dist * step; pos.z += dz / dist * step;
    }

    private void updateSatisfy(float dt) {
        stateTimer -= dt;
        if (stateTimer > 0f) return;
        if (targetFemale != null && !targetFemale.dead) {
            Audio.playOnce(SFX_KISS, volumeFor());
            targetFemale.impregnate();
            kissCount++;
            if (kissCount >= muscleThreshold) {
                Audio.playOnce(SFX_FIGHT, volumeFor());
                targetFemale = null;
                becomeType(Type.MUSCULAR);
                return;
            }
        }
        targetFemale = null;
        state = State.REST;
        timer = 8f + rng.nextFloat() * 8f;
    }

    private void updateSeekTree(float dt, WorldManager world) {
        if (targetTree == null || targetTree.depleted) {
            if (targetTree != null) targetTree.reserved = false;
            targetTree = null;
            state = State.SEARCH;
            timer = 0f;
            return;
        }
        float dx = targetTree.x - pos.x, dz = targetTree.z - pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist <= TREE_DISTANCE) {
            state = State.CHOP;
            stateTimer = CHOP_TIME;
            Audio.playOnce(SFX_CHOP, volumeFor());
            return;
        }
        float step = Math.min(MALE_WALK_SPEED * dt, dist);
        pos.x += dx / dist * step;
        pos.z += dz / dist * step;
    }

    private void updateChop(float dt, WorldManager world) {
        if (targetTree == null) { state = State.SEARCH; return; }
        stateTimer -= dt;
        float progress = 1f - Math.max(stateTimer, 0f) / CHOP_TIME;
        world.chopTree(targetTree, progress);

        if (sprite != null) {
            sprite.setScale(1f + 0.06f * (float) Math.sin(progress * Math.PI * 20f));
        }

        if (stateTimer <= 0f) {
            if (sprite != null) sprite.setScale(1f);
            int gained = world.finishChop(targetTree);
            if (tribe != null) tribe.wood += gained;
            targetTree.reserved = false;
            targetTree = null;
            state = State.SEARCH;
            timer = 0.5f;
        }
    }

    private void updateSeekBuild(float dt, WorldManager world) {
        if (tribe == null) { state = State.SEARCH; return; }
        float dx = tribe.center.x - pos.x;
        float dz = tribe.center.z - pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist <= 5f) { state = State.BUILD; stateTimer = BUILD_TIME; return; }
        float step = Math.min(MALE_WALK_SPEED * dt, dist);
        pos.x += dx / dist * step;
        pos.z += dz / dist * step;
    }

    private void updateBuild(float dt, WorldManager world) {
        stateTimer -= dt;
        if (sprite != null) {
            sprite.setScale(1f + 0.1f * (float) Math.sin(stateTimer * 8f));
        }
        if (stateTimer <= 0f) {
            if (sprite != null) sprite.setScale(1f);
            HouseInstance h = world.buildHouseForTribe(tribe);
            if (h != null) {
                System.out.println("[TRIBE " + tribe.id + "] House built at ("
                        + String.format("%.1f", h.x) + ", " + String.format("%.1f", h.z) + "), total: "
                        + tribe.houses.size() + ", wood: " + tribe.wood);
            }
            state = State.SEARCH;
            timer = 0.5f;
        }
    }

    // ====================================================================
    private void updateMuscular(float dt, WorldManager world) {
        if (state == State.SEEK_BUILD) { updateSeekBuild(dt, world); return; }
        if (state == State.BUILD) { updateBuild(dt, world); return; }
        if (state == State.SEEK_TREE) { updateSeekTree(dt, world); return; }
        if (state == State.CHOP) { updateChop(dt, world); return; }

        int tribePop = countTribeMembers(worldEaters);
        if (tribe != null && tribe.canBuild() && tribe.needsHouse(tribePop)) {
            state = State.SEEK_BUILD; return;
        }

        WorldManager.TreeData t = world.findNearestTree(pos.x, pos.z, TREE_SEARCH_RADIUS);
        if (t != null) { t.reserved = true; targetTree = t; state = State.SEEK_TREE; return; }

        if (state != State.WANDER) { state = State.WANDER; pickWalkTarget(); }
        float dx = walkTo.x - pos.x, dz = walkTo.z - pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.5f) pickWalkTarget();
        else {
            float step = Math.min(FEMALE_WALK_SPEED * dt, dist);
            pos.x += dx / dist * step;
            pos.z += dz / dist * step;
        }
    }

    // ====================================================================
    private void updateFemale(float dt, List<Eater> newbornsOut, WorldManager world) {
        switch (state) {
            case WANDER -> {
                float dx = walkTo.x - pos.x, dz = walkTo.z - pos.z;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                if (dist < 0.5f) pickWalkTarget();
                else {
                    float step = Math.min(FEMALE_WALK_SPEED * dt, dist);
                    pos.x += dx / dist * step;
                    pos.z += dz / dist * step;
                }
            }
            case GESTATING -> {
                pregnancyTimer -= dt;
                if (pregnancyTimer <= 0f) becomeType(Type.PREGNANT);
            }
            case SEEK_TREE -> updateSeekTree(dt, world);
            case CHOP -> updateChop(dt, world);
            case SEEK_BUILD -> updateSeekBuild(dt, world);
            case BUILD -> updateBuild(dt, world);
            default -> {
                int tribePop = countTribeMembers(worldEaters);
                if (tribe != null && tribe.canBuild() && tribe.needsHouse(tribePop)) {
                    state = State.SEEK_BUILD; return;
                }
                WorldManager.TreeData t = world.findNearestTree(pos.x, pos.z, TREE_SEARCH_RADIUS);
                if (t != null) {
                    t.reserved = true;
                    targetTree = t; state = State.SEEK_TREE; return;
                }
                state = State.WANDER; pickWalkTarget();
            }
        }
    }

    private void updatePregnant(float dt, List<Eater> newbornsOut, int currentPop) {
        pregnancyTimer -= dt;
        if (pregnancyTimer > 0f) return;

        Audio.playOnce(SFX_BIRTH, volumeFor());
        int n = 1 + rng.nextInt(8);
        int room = Math.max(0, MAX_POPULATION - currentPop);
        if (n > room) n = room;
        // Even at cap, allow one child so pregnancies don't get stuck.
        if (n == 0 && room == 0) n = 1;

        Eater mom = this;
        for (int i = 0; i < n; i++) {
            float a = rng.nextFloat() * (float) (Math.PI * 2);
            float r = 0.5f + rng.nextFloat() * 1.5f;
            Eater baby = new Eater(Type.BABY,
                    pos.x + (float) Math.cos(a) * r,
                    pos.z + (float) Math.sin(a) * r, tribe, rng);
            boolean keepSuckle = rng.nextInt(SUCKLE_YB_ODDS) == 0;
            baby.setMother(mom, keepSuckle);
            newbornsOut.add(baby);
        }
        becomeType(Type.FEMALE);
        birthCooldown = BIRTH_COOLDOWN;
    }

    private void impregnate() {
        if (type != Type.FEMALE || isPregnantOrGestating()) return;
        if (birthCooldown > 0f) return;
        if (worldEaters != null) {
            int alive = 0;
            for (Eater e : worldEaters) if (!e.dead) alive++;
            if (alive >= MAX_POPULATION) return; // refuse at cap
        }
        pregnancyTimer = GESTATION_TIME;
        state = State.GESTATING;
    }

    private boolean isPregnantOrGestating() {
        return type == Type.PREGNANT
                || state == State.GESTATING
                || state == State.PREGNANT_WAIT;
    }

    // ====================================================================
    private void updateBaby(float dt) {
        growTimer -= dt;
        if (growTimer <= 0f) {
            boolean keep = suckleAllowed;
            becomeType(Type.YOUNG_BOY);
            if (!keep) { mother = null; suckleAllowed = false; }
            return;
        }
        if (mother != null && suckleAllowed && !mother.dead) {
            suckleTimer -= dt;
            if (suckleTimer <= 0f && state == State.WANDER) state = State.SEEK_MOM;
        }
        switch (state) {
            case WANDER -> {
                float dx = walkTo.x - pos.x, dz = walkTo.z - pos.z;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                if (dist < 0.5f) pickWalkTarget();
                else {
                    float step = Math.min(BABY_WALK_SPEED * dt, dist);
                    pos.x += dx / dist * step; pos.z += dz / dist * step;
                }
            }
            case SEEK_MOM -> {
                if (mother == null || mother.dead) { state = State.WANDER; break; }
                float dx = mother.pos.x - pos.x, dz = mother.pos.z - pos.z;
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                if (d <= SUCKLE_RANGE) {
                    state = State.SUCKLE; stateTimer = SUCKLE_TIME;
                    Audio.playLoop(SFX_SUCKLE, SUCKLE_TIME, volumeFor());
                } else {
                    float step = Math.min(FEMALE_WALK_SPEED * dt, d);
                    pos.x += dx / d * step; pos.z += dz / d * step;
                }
            }
            case SUCKLE -> {
                stateTimer -= dt;
                if (stateTimer <= 0f) {
                    state = State.WANDER;
                    suckleTimer = 30f + rng.nextFloat() * 100f;
                    pickWalkTarget();
                }
            }
            default -> { state = State.WANDER; pickWalkTarget(); }
        }
    }

    // ====================================================================
    private void updateYoungBoy(float dt, List<Poop> poops, List<Eater> allEaters) {
        growTimer -= dt;
        if (growTimer <= 0f) {
            boolean keepSuckle = suckleAllowed && mother != null && !mother.dead;
            becomeType(Type.TEEN);
            if (!keepSuckle) { mother = null; suckleAllowed = false; }
            return;
        }
        if (mother != null && suckleAllowed && !mother.dead) {
            suckleTimer -= dt;
            if (suckleTimer <= 0f && (state == State.REST || state == State.WANDER))
                state = State.SEEK_MOM;
        }
        switch (state) {
            case SEARCH -> {
                timer -= dt;
                if (timer > 0f) break;
                Poop best = null;
                float bestD2 = SEARCH_RADIUS * SEARCH_RADIUS;
                for (Poop p : poops) {
                    if (p.eaten) continue;
                    float dx = p.x - pos.x, dz = p.z - pos.z;
                    float d2 = dx * dx + dz * dz;
                    if (d2 < bestD2) { bestD2 = d2; best = p; }
                }
                if (best != null) target = best;
                else { target = null; pickWalkTarget(); }
                state = State.WALK;
            }
            case WALK -> {
                if (target != null && target.eaten) { target = null; state = State.SEARCH; timer = 0f; break; }
                float dx, dz, stopAt;
                if (target != null) { dx = target.x - pos.x; dz = target.z - pos.z; stopAt = EAT_DISTANCE; }
                else                { dx = walkTo.x - pos.x; dz = walkTo.z - pos.z; stopAt = 0.4f; }
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                if (dist <= stopAt) {
                    if (target != null) {
                        state = State.EAT; timer = EAT_TIME;
                        eatSound = Audio.playLoop(SFX_EAT, EAT_TIME, volumeFor());
                    } else { state = State.SEARCH; timer = 0.5f + rng.nextFloat(); }
                    break;
                }
                float step = Math.min(MALE_WALK_SPEED * dt, dist);
                pos.x += dx / dist * step; pos.z += dz / dist * step;
            }
            case EAT -> {
                timer -= dt;
                if (sprite != null) {
                    float progress = 1f - Math.max(timer, 0f) / EAT_TIME;
                    sprite.setScale(1f + 0.12f * (float) Math.sin(progress * Math.PI * 16f));
                }
                if (timer <= 0f) {
                    if (eatSound != null) { eatSound.stop(); eatSound = null; }
                    if (sprite != null) sprite.setScale(1f);
                    if (target != null) { target.eaten = true; target = null; }
                    state = State.REST;
                    timer = YB_REST_MIN + rng.nextFloat() * (YB_REST_MAX - YB_REST_MIN);
                }
            }
            case REST -> {
                timer -= dt;
                if (timer <= 0f) { state = State.SEARCH; timer = 0f; break; }
                Eater chaseTarget = null;
                int count = 0;
                for (Eater other : allEaters) {
                    if (other == this || other.dead || other.type != Type.YOUNG_BOY) continue;
                    float dx = other.pos.x - pos.x, dz = other.pos.z - pos.z;
                    float d2 = dx * dx + dz * dz;
                    if (d2 < 400f) { count++; if (rng.nextInt(count) == 0) chaseTarget = other; }
                }
                if (chaseTarget != null) {
                    float dx = chaseTarget.pos.x - pos.x, dz = chaseTarget.pos.z - pos.z;
                    float d = (float) Math.sqrt(dx * dx + dz * dz);
                    if (d > 2f) {
                        float step = Math.min(CHASE_SPEED * dt, d);
                        pos.x += dx / d * step; pos.z += dz / d * step;
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastCrowdSoundMs > CROWD_COOLDOWN_MS) {
                        lastCrowdSoundMs = now;
                        Audio.playOnce(SFX_CROWD, volumeFor());
                    }
                }
            }
            case SEEK_MOM -> {
                if (mother == null || mother.dead) { state = State.WANDER; break; }
                float dx = mother.pos.x - pos.x, dz = mother.pos.z - pos.z;
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                if (d <= SUCKLE_RANGE) {
                    state = State.SUCKLE; stateTimer = SUCKLE_TIME;
                    Audio.playLoop(SFX_SUCKLE, SUCKLE_TIME, volumeFor());
                } else {
                    float step = Math.min(FEMALE_WALK_SPEED * dt, d);
                    pos.x += dx / d * step; pos.z += dz / d * step;
                }
            }
            case SUCKLE -> {
                stateTimer -= dt;
                if (stateTimer <= 0f) {
                    state = State.REST;
                    timer = YB_REST_MIN + rng.nextFloat() * (YB_REST_MAX - YB_REST_MIN);
                    suckleTimer = 30f + rng.nextFloat() * 100f;
                }
            }
            default -> { state = State.SEARCH; timer = 0f; }
        }
    }

    // ====================================================================
    private void becomeType(Type newType) {
        this.type = newType;
        switch (newType) {
            case FEMALE -> {
                mealsEaten = 0; mature = false; kissCount = 0;
                gluttonous = rng.nextFloat() < GLUTTON_CHANCE;
                fatThreshold = 5 + rng.nextInt(6);
                lifespan = ADULT_LIFESPAN_MIN + rng.nextFloat() * (ADULT_LIFESPAN_MAX - ADULT_LIFESPAN_MIN);
                age = 0f;
                state = State.WANDER; pickWalkTarget();
            }
            case PREGNANT -> { state = State.PREGNANT_WAIT; pregnancyTimer = PREGNANT_TIME; }
            case BABY -> { growTimer = BABY_GROW_TIME; state = State.WANDER; pickWalkTarget(); }
            case YOUNG_BOY -> { growTimer = YOUNG_BOY_GROW_TIME; state = State.WANDER; pickWalkTarget(); }
            case TEEN -> {
                mealsEaten = 0; mature = false;
                gluttonous = rng.nextFloat() < GLUTTON_CHANCE;
                fatThreshold = 5 + rng.nextInt(6);
                lifespan = ADULT_LIFESPAN_MIN + rng.nextFloat() * (ADULT_LIFESPAN_MAX - ADULT_LIFESPAN_MIN);
                age = 0f;
                state = State.SEARCH; timer = 0.5f;
            }
            case MUSCULAR -> {
                lifespan = ADULT_LIFESPAN_MIN + rng.nextFloat() * (ADULT_LIFESPAN_MAX - ADULT_LIFESPAN_MIN);
                age = 0f;
                state = State.WANDER; pickWalkTarget();
            }
            case FAT -> { }
        }
        rebuildSprite();
        syncSpritePosition();
    }

    private void initStateForType() {
        switch (type) {
            case TEEN, FAT, MUSCULAR -> { state = State.SEARCH; timer = rng.nextFloat() * 1.5f; }
            case FEMALE -> { state = State.WANDER; pickWalkTarget(); }
            case PREGNANT -> { state = State.PREGNANT_WAIT; pregnancyTimer = PREGNANT_TIME; }
            case BABY -> { state = State.WANDER; growTimer = BABY_GROW_TIME; pickWalkTarget(); }
            case YOUNG_BOY -> { state = State.WANDER; growTimer = YOUNG_BOY_GROW_TIME; pickWalkTarget(); }
        }
    }

    private void rebuildSprite() {
        if (typeTextures == null) { sprite = null; return; }
        Texture tex = typeTextures[type.ordinal()];
        currentHeight = TYPE_HEIGHTS[type.ordinal()];
        if (tex == null) { sprite = null; return; }
        sprite = new Sprite(tex, new Vector3f(pos.x, pos.y + currentHeight * 0.5f, pos.z), currentHeight);
    }

    private void pickWalkTarget() {
        float a = rng.nextFloat() * (float) (Math.PI * 2);
        float r = 5f + rng.nextFloat() * 15f;
        walkTo.set(pos.x + (float) Math.cos(a) * r, 0f, pos.z + (float) Math.sin(a) * r);
    }
}

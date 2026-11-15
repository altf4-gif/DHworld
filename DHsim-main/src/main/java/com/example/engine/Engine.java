package com.example.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Engine {
    private static final String VERSION = "v40.6 (Straight Arms)";
    private static final int TEEN_INDEX = 12;

    private static final String SPRITE_VERT = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec2 aTexCoord;

            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProjection;
            uniform vec2 uUvOffset;
            uniform vec2 uUvScale;

            out vec2 vTexCoord;

            void main() {
                gl_Position = uProjection * uView * uModel * vec4(aPos, 0.0, 1.0);
                vTexCoord = uUvOffset + aTexCoord * uUvScale;
            }
            """;

    private static final String SPRITE_FRAG = """
            #version 330 core
            in vec2 vTexCoord;
            out vec4 FragColor;
            uniform sampler2D uTexture;
            uniform vec3 uLightTint;
            void main() {
                vec4 c = texture(uTexture, vTexCoord);
                if (c.a < 0.1) discard;
                if (c.r > 0.93 && c.g > 0.93 && c.b > 0.93) discard;
                FragColor = vec4(c.rgb * uLightTint, c.a);
            }
            """;

    private static final String SPRITE_SHADOW_VERT = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            uniform mat4 uModel;
            uniform mat4 uLightVP;
            void main() {
                gl_Position = uLightVP * uModel * vec4(aPos, 1.0);
            }
            """;

    private static final String SPRITE_SHADOW_FRAG = """
            #version 330 core
            out vec4 FragColor;
            void main() {
                FragColor = vec4(0.0, 0.0, 0.0, 0.5);
            }
            """;

    private static final String HOUSE_VERT = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProjection;
            out vec2 vTexCoord;
            out vec3 vWorld;
            void main() {
                vec4 world = uModel * vec4(aPos, 1.0);
                vWorld = world.xyz;
                vTexCoord = aTexCoord;
                gl_Position = uProjection * uView * world;
            }
            """;

    private static final String HOUSE_FRAG = """
            #version 330 core
            in vec2 vTexCoord;
            in vec3 vWorld;
            out vec4 FragColor;

            uniform sampler2D uTexture;
            uniform sampler2D uShadowMap;
            uniform mat4 uLightVP;
            uniform vec3 uSunDir;
            uniform vec3 uSunColor;
            uniform vec3 uAmbient;
            uniform vec3 uCamPos;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform float uWhiteKey;   // 1 = вырезать белое (дома/листва), 0 = нет (персонажи)

            float shadowCalc(vec4 lsp, vec3 n) {
                vec3 p = lsp.xyz / lsp.w;
                p = p * 0.5 + 0.5;
                if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
                float bias = max(0.006 * (1.0 - dot(n, uSunDir)), 0.0015);
                float current = p.z - bias;
                vec2 ts = 1.0 / textureSize(uShadowMap, 0);
                float sh = 0.0;
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        float d = texture(uShadowMap, p.xy + vec2(x, y) * ts).r;
                        sh += current > d ? 0.0 : 1.0;
                    }
                }
                return sh / 9.0;
            }

            void main() {
                vec4 tex = texture(uTexture, vTexCoord);
                if (tex.a < 0.1) discard;
                if (uWhiteKey > 0.5 && tex.r > 0.92 && tex.g > 0.92 && tex.b > 0.92) discard;

                vec3 n = normalize(cross(dFdx(vWorld), dFdy(vWorld)));
                if (!gl_FrontFacing) n = -n;
                float diff = max(dot(n, uSunDir), 0.0);

                float shadow = 1.0;
                if (diff > 0.0) shadow = shadowCalc(uLightVP * vec4(vWorld, 1.0), n);

                vec3 lit = tex.rgb * (uAmbient + uSunColor * diff * shadow);
                float dist = length(vWorld - uCamPos);
                float fog = 1.0 - exp(-pow(dist * uFogDensity, 2.0));
                lit = mix(lit, uFogColor, fog);
                FragColor = vec4(lit, tex.a);
            }
            """;

    private static final String SHADOW_VERT = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            uniform mat4 uModel;
            uniform mat4 uLightVP;
            out vec2 vTexCoord;
            void main() {
                vTexCoord = aTexCoord;
                gl_Position = uLightVP * uModel * vec4(aPos, 1.0);
            }
            """;

    private static final String SHADOW_FRAG = """
            #version 330 core
            in vec2 vTexCoord;
            out vec4 FragColor;
            uniform sampler2D uTexture;
            uniform float uWhiteKey;
            void main() {
                vec4 c = texture(uTexture, vTexCoord);
                if (c.a < 0.5) discard;
                if (uWhiteKey > 0.5 && c.r > 0.92 && c.g > 0.92 && c.b > 0.92) discard;
            }
            """;

    private static final String[] PEOPLE_IMAGES = {
            "black_male_tpose.png", "child_boy_tpose.png", "elderly_male_tpose.png",
            "female_slim_tpose.png", "hazmat_suit_yellow_tpose.png", "leather_coat_military_tpose.png",
            "male_bent_over_pose.png", "male_police_costume_tpose.png", "military_uniform_torn_tpose.png",
            "muscular_male_tpose.png", "overweight_male_tpose.png", "pregnant_female_tpose.png",
            "teenager_male_tpose.png", "teenager_sign_mars.png", "teenager_sign_muyakat.png",
            "teenager_sign_simulation.png", "young_boy_tpose.png"
    };

    private static final String[] PEOPLE_DESCRIPTIONS = new String[PEOPLE_IMAGES.length];
    static {
        for (int i = 0; i < PEOPLE_DESCRIPTIONS.length; i++) {
            PEOPLE_DESCRIPTIONS[i] = "Описание пока не добавлено.\n\nОтредактируйте массив PEOPLE_DESCRIPTIONS в Engine.java.";
        }
    }

    private static final String[] PEOPLE_NAMES = {
            "Человек в чёрном", "Ребёнок", "Пожилой мужчина", "Стройная женщина", "Хазмат",
            "Военная шинель", "Согнувшийся", "Полицейский", "Рваная форма", "Качок", "Толстяк",
            "Беременная", "Подросток", "Плакат 'Марс'", "Плакат 'Муякать'", "Плакат 'Симуляция'",
            "Мальчик"
    };

    private static ObjModel tryLoad(String folder, String base) {
        String[] suffixes = {"", "1", "2"};
        for (String s : suffixes) {
            String obj = folder + "/" + base + s + ".obj";
            String mtl = folder + "/" + base + s + ".mtl";
            if (new File(obj).exists()) {
                try {
                    return new ObjModel(obj, mtl, null);
                } catch (Exception e) {
                    System.err.println("[ERROR] " + obj + ": " + e.getMessage());
                }
            }
        }
        return null;
    }

    private static Vector3f findGoodTribesSpawn(float px, float pz, float minClear) {
        for (int attempt = 0; attempt < 200; attempt++) {
            float x = (attempt == 0) ? px : (float) (Math.random() - 0.5) * 140f;
            float z = (attempt == 0) ? pz : (float) (Math.random() - 0.5) * 140f;
            if (Math.abs(x) > 70f || Math.abs(z) > 70f) continue;
            float h = Ground.naturalHeightAt(x, z);
            if (h < Ground.WATER_LEVEL + minClear) continue;
            float lakeDist = (float) Math.sqrt((x - Ground.LAKE_X) * (x - Ground.LAKE_X) + (z - Ground.LAKE_Z) * (z - Ground.LAKE_Z));
            if (lakeDist < Ground.LAKE_RADIUS + 5f) continue;
            return new Vector3f(x, h, z);
        }
        return new Vector3f(px, Ground.naturalHeightAt(px, pz), pz);
    }

    public void run() {
        System.out.println("=== Engine " + VERSION + " ===");
        Window window = new Window(1280, 720, "LWJGL 3D Engine " + VERSION);
        Renderer renderer = new Renderer();
        renderer.init();
        window.setCursorDisabled(true);
        Camera camera = new Camera(window.aspectRatio());
        ShaderProgram spriteShader = new ShaderProgram(SPRITE_VERT, SPRITE_FRAG);
        ShaderProgram spriteShadowShader = new ShaderProgram(SPRITE_SHADOW_VERT, SPRITE_SHADOW_FRAG);
        ShaderProgram houseShader = new ShaderProgram(HOUSE_VERT, HOUSE_FRAG);
        ShaderProgram shadowShader = new ShaderProgram(SHADOW_VERT, SHADOW_FRAG);
        Sky sky = new Sky();
        Ground ground = new Ground();
        Water water = new Water();
        LineRenderer lineRenderer = new LineRenderer();
        UiRenderer ui = new UiRenderer();
        ShadowMap shadowMap = new ShadowMap();
        shadowMap.init();
        DayNight dayNight = new DayNight();
        Weather weather = new Weather();
        WorldManager world = new WorldManager();
        TribeMarker.init();
        Hud hud = new Hud();
        boolean[] terrainDirty = {false};
        float[] terrainTimer = {0f};
        world.onTerrainDirty = () -> terrainDirty[0] = true;
        Texture[] peopleTex = new Texture[PEOPLE_IMAGES.length];
        for (int i = 0; i < PEOPLE_IMAGES.length; i++) {
            try {
                peopleTex[i] = new Texture("img/P/" + PEOPLE_IMAGES[i]);
            } catch (Exception e) {
                peopleTex[i] = null;
            }
        }
        Texture[] typeTex = {
                peopleTex[12], peopleTex[10], peopleTex[3], peopleTex[11],
                peopleTex[1], peopleTex[16], peopleTex[9],
        };
        Eater.initTextures(typeTex);
        List<Sprite> sprites = new ArrayList<>();
        List<Person> peopleList = new ArrayList<>();
        final float startX = -4.0f, spacingX = 1.2f, spriteHeight = 1.5f;
        for (int i = 0; i < PEOPLE_IMAGES.length; i++) {
            if (peopleTex[i] == null) continue;
            peopleList.add(new Person(PEOPLE_NAMES[i], PEOPLE_DESCRIPTIONS[i], peopleTex[i]));
            if (i == TEEN_INDEX) continue;
            int row = i / 9, col = i % 9;
            float x = startX + col * spacingX + (row == 1 ? spacingX * 0.5f : 0f);
            float z = (row == 0) ? -2.0f : 0.0f;
            float y = Ground.getHeightAt(x, z);
            sprites.add(new Sprite(peopleTex[i], new Vector3f(x, y + spriteHeight * 0.5f, z), spriteHeight));
        }
        Encyclopedia encyclopedia = new Encyclopedia(peopleList);
        Vector3f redPos = findGoodTribesSpawn(-20f, -20f, 1.5f);
        Vector3f bluePos = findGoodTribesSpawn(40f, -40f, 1.5f);
        Vector3f greenPos = findGoodTribesSpawn(0f, 50f, 1.5f);
        Tribe redTribe = new Tribe(0, redPos, new Vector3f(1f, 0.2f, 0.2f));
        Tribe blueTribe = new Tribe(1, bluePos, new Vector3f(0.2f, 0.5f, 1f));
        Tribe greenTribe = new Tribe(2, greenPos, new Vector3f(0.2f, 1f, 0.2f));
        world.tribes.add(redTribe);
        world.tribes.add(blueTribe);
        world.tribes.add(greenTribe);
        Ground.addPad(redTribe.center.x, redTribe.center.z, 14f, 6f);
        Ground.addPad(blueTribe.center.x, blueTribe.center.z, 14f, 6f);
        Ground.addPad(greenTribe.center.x, greenTribe.center.z, 14f, 6f);
        ground.rebuild();
        ObjModel treeModel = tryLoad("mesh/TreeRL", "Tree");
        if (treeModel != null) treeModel.applyFallbackTexture(Texture.generateLeaves());
        ObjModel vWalls = tryLoad("mesh/VillageHouse", "walls");
        ObjModel vRoof = tryLoad("mesh/VillageHouse", "roof");
        ObjModel vDoor = tryLoad("mesh/VillageHouse", "door");
        ObjModel vWindows = tryLoad("mesh/VillageHouse", "windows");
        if (vWalls != null && vRoof != null && vDoor != null && vWindows != null) {
            world.protoWalls = vWalls;
            world.protoRoof = vRoof;
            world.protoDoor = vDoor;
            world.protoWindows = vWindows;
            world.protoScale = 6.0f / Math.max(vWalls.getHeight(), vRoof.getHeight());
        }
        ObjModel tankModel = tryLoad("mesh/Tank", "Tqa");
        ObjModel policeModel = tryLoad("mesh/Police", "hl");
        List<RiggedModel> riggedModels = new ArrayList<>();
        String mrhObjPath = "mesh/MrH/meshy-model-2026-11-13T06-34-41-877Z(1).obj";
        String mrhTexPath = "mesh/MrH/texture_21.png";
        if (new File(mrhObjPath).exists()) {
            try {
                riggedModels.add(new RiggedModel(mrhObjPath, mrhTexPath, 10f, 10f, 2.0f, 0.11f, 1.57f, 0.80f, 1.0f, false));
                System.out.println("  -> MrH loaded.");
            } catch (Exception e) {
                System.err.println("  [ERROR] Failed to load MrH: " + e.getMessage());
            }
        }
        List<HouseInstance> staticProps = new ArrayList<>();
        Random propRng = new Random(999);
        if (tankModel != null) {
            float scale = 3.5f / tankModel.getHeight();
            for (int i = 0; i < 2; i++) {
                float x = (propRng.nextFloat() - 0.5f) * 140f;
                float z = (propRng.nextFloat() - 0.5f) * 140f;
                if (Math.abs(x) < 20f && Math.abs(z) < 20f) { i--; continue; }
                if (Ground.getHeightAt(x, z) < Ground.WATER_LEVEL + 0.5f) { i--; continue; }
                if (Ground.getHeightAt(x, z) > 15f) { i--; continue; }
                float y = Ground.getHeightAt(x, z);
                float yaw = propRng.nextFloat() * (float) Math.PI * 2f;
                staticProps.add(new HouseInstance(tankModel, null, null, null, x, y, z, yaw, scale));
            }
        }
        if (policeModel != null) {
            float scale = 1.8f / policeModel.getHeight();
            for (int i = 0; i < 5; i++) {
                float x = (propRng.nextFloat() - 0.5f) * 140f;
                float z = (propRng.nextFloat() - 0.5f) * 140f;
                if (Math.abs(x) < 20f && Math.abs(z) < 20f) { i--; continue; }
                if (Ground.getHeightAt(x, z) < Ground.WATER_LEVEL + 0.5f) { i--; continue; }
                if (Ground.getHeightAt(x, z) > 15f) { i--; continue; }
                float y = Ground.getHeightAt(x, z);
                float yaw = propRng.nextFloat() * (float) Math.PI * 2f;
                staticProps.add(new HouseInstance(policeModel, null, null, null, x, y, z, yaw, scale));
            }
        }
        List<HouseInstance> staticTrees = new ArrayList<>();
        List<Vector3f> occupied = new ArrayList<>();
        Random rng = new Random(42);
        List<WorldManager.TreeData> treeDataList = new ArrayList<>();
        if (treeModel != null) {
            float scale = 6.5f / treeModel.getHeight();
            int placed = 0, attempts = 0;
            while (placed < 150 && attempts++ < 2000) {
                float x = (rng.nextFloat() - 0.5f) * 140f, z = (rng.nextFloat() - 0.5f) * 140f;
                if (Math.abs(x) < 15f && Math.abs(z) < 15f) continue;
                if (Ground.getHeightAt(x, z) < Ground.WATER_LEVEL + 0.5f) continue;
                if (Ground.getHeightAt(x, z) > 15f) continue;
                boolean close = false;
                for (Vector3f p : occupied) if (p.distanceSquared(x, 0, z) < 100f) { close = true; break; }
                if (close) continue;
                occupied.add(new Vector3f(x, 0, z));
                float yaw = rng.nextFloat() * (float) Math.PI * 2f;
                float y = Ground.getHeightAt(x, z) - 0.6f;
                HouseInstance inst = new HouseInstance(treeModel, null, null, null, x, y, z, yaw, scale);
                staticTrees.add(inst);
                treeDataList.add(new WorldManager.TreeData(inst, x, z, yaw, scale, y));
                placed++;
            }
            world.registerTrees(treeDataList);
        }
        Poop.init();
        List<Poop> poops = new ArrayList<>();
        Random worldRng = new Random(2024);
        int poopAttempts = 0;
        while (poops.size() < 90 && poopAttempts++ < 1200) {
            float x = (worldRng.nextFloat() - 0.5f) * 140f, z = (worldRng.nextFloat() - 0.5f) * 140f;
            if (Math.abs(x) < 14f && Math.abs(z) < 8f) continue;
            if (Ground.getHeightAt(x, z) < Ground.WATER_LEVEL + 0.5f) continue;
            if (Ground.getHeightAt(x, z) > 15f) continue;
            boolean blocked = false;
            for (Vector3f hc : occupied) if (hc.distanceSquared(x, 0f, z) < 36f) { blocked = true; break; }
            if (!blocked) poops.add(Poop.random(x, z, worldRng));
        }
        UiTexture poopIcon = PoopPreview.render(256, false);
        GL11.glViewport(0, 0, window.width(), window.height());
        UiTexture brightPoopIcon = PoopPreview.render(256, true);
        GL11.glViewport(0, 0, window.width(), window.height());
        List<Eater> eaters = new ArrayList<>();
        Random npcRng = new Random(777);
        if (typeTex[Eater.Type.TEEN.ordinal()] != null) {
            eaters.add(new Eater(Eater.Type.TEEN, redTribe.center.x + 2f, redTribe.center.z + 2f, redTribe, npcRng));
            eaters.add(new Eater(Eater.Type.TEEN, blueTribe.center.x + 2f, blueTribe.center.z + 2f, blueTribe, npcRng));
            eaters.add(new Eater(Eater.Type.TEEN, greenTribe.center.x + 2f, greenTribe.center.z + 2f, greenTribe, npcRng));
        }
        camera.snapToGround();
        
        SpawnMenu spawnMenu = new SpawnMenu(typeTex, peopleTex, poopIcon, brightPoopIcon);
        
        float lastTime = (float) GLFW.glfwGetTime();
        boolean tabWasPressed = false, qWasPressed = false, escWasPressed = false;
        boolean upWasPressed = false, downWasPressed = false, leftWasPressed = false;
        boolean tWasPressed = false, mWasPressed = false;
        float poopRegenTimer = 0f, treeRegenTimer = 0f;
        Matrix4f lightView = new Matrix4f();
        Matrix4f lightProj = new Matrix4f().ortho(-140f, 140f, -140f, 140f, 1f, 500f);
        Matrix4f lightVP = new Matrix4f();
        try {
            while (!window.shouldClose()) {
                float now = (float) GLFW.glfwGetTime();
                float dt = Math.min(now - lastTime, 0.1f);
                lastTime = now;
                window.pollEvents();
                if (window.isKeyPressed(GLFW.GLFW_KEY_ESCAPE) && !escWasPressed) {
                    if (spawnMenu.isOpen()) {
                        spawnMenu.close();
                        window.setCursorDisabled(true);
                        camera.resetMouseLook();
                        leftWasPressed = true;
                    } else if (encyclopedia.isOpen()) {
                        encyclopedia.toggle();
                        window.setCursorDisabled(true);
                        camera.resetMouseLook();
                    } else {
                        return;
                    }
                }
                escWasPressed = window.isKeyPressed(GLFW.GLFW_KEY_ESCAPE);
                if (window.isKeyPressed(GLFW.GLFW_KEY_Q) && !qWasPressed) {
                    if (spawnMenu.isOpen()) {
                        spawnMenu.close();
                        window.setCursorDisabled(true);
                        camera.resetMouseLook();
                    } else {
                        if (encyclopedia.isOpen()) encyclopedia.toggle();
                        spawnMenu.open();
                        window.setCursorDisabled(false);
                        camera.resetMouseLook();
                        leftWasPressed = true;
                    }
                }
                qWasPressed = window.isKeyPressed(GLFW.GLFW_KEY_Q);
                if (window.isKeyPressed(GLFW.GLFW_KEY_TAB) && !tabWasPressed) {
                    if (spawnMenu.isOpen()) spawnMenu.close();
                    encyclopedia.toggle();
                    window.setCursorDisabled(!encyclopedia.isOpen());
                    camera.resetMouseLook();
                    leftWasPressed = true;
                }
                tabWasPressed = window.isKeyPressed(GLFW.GLFW_KEY_TAB);
                if (window.isKeyPressed(GLFW.GLFW_KEY_T) && !tWasPressed) dayNight.toggleSpeed();
                tWasPressed = window.isKeyPressed(GLFW.GLFW_KEY_T);
                if (window.isKeyPressed(GLFW.GLFW_KEY_M) && !mWasPressed) Audio.toggleMuted();
                mWasPressed = window.isKeyPressed(GLFW.GLFW_KEY_M);
                if (spawnMenu.isOpen()) {
                    double[] cp = window.cursorPos();
                    int idx = spawnMenu.hitTest(window.width(), window.height(), cp[0], cp[1]);
                    spawnMenu.setHovered(idx);
                    boolean left = window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                    if (left && !leftWasPressed && idx >= 0) {
                        SpawnMenu.Action a = spawnMenu.actionAt(idx);
                        if (a != null) spawnFromMenu(a, camera, poops, eaters, npcRng, world.tribes, riggedModels);
                    }
                    leftWasPressed = left;
                } else {
                    leftWasPressed = false;
                }
                if (encyclopedia.isOpen()) {
                    if (window.isKeyPressed(GLFW.GLFW_KEY_UP) && !upWasPressed) encyclopedia.prev();
                    upWasPressed = window.isKeyPressed(GLFW.GLFW_KEY_UP);
                    if (window.isKeyPressed(GLFW.GLFW_KEY_DOWN) && !downWasPressed) encyclopedia.next();
                    downWasPressed = window.isKeyPressed(GLFW.GLFW_KEY_DOWN);
                } else {
                    upWasPressed = false;
                    downWasPressed = false;
                }
                camera.setAspect(window.aspectRatio());
                if (!spawnMenu.isOpen() && !encyclopedia.isOpen()) camera.update(window, dt);
                dayNight.update(dt);
                weather.update(dt);
                Vector3f sunDir = dayNight.sunDir();
                Vector3f sunColor = dayNight.sunColor();
                Vector3f ambient = dayNight.ambient();
                Vector3f fogColor = dayNight.fogColor();
                float fogDensity = dayNight.fogDensity();
                float sunIntensity = dayNight.sunIntensity() * weather.sunIntensityModifier();
                sunColor.mul(weather.sunIntensityModifier());
                ambient.mul(weather.ambientDampening());
                Vector3f eye = new Vector3f(sunDir).mul(200f);
                if (Math.abs(sunDir.y) > 0.99f) {
                    eye.x += 0.001f;
                    eye.z += 0.001f;
                }
                lightView.identity().lookAt(eye.x, eye.y, eye.z, 0, 0, 0, 0, 1, 0);
                lightVP.set(lightProj).mul(lightView);
                if (terrainDirty[0]) {
                    terrainTimer[0] += dt;
                    if (terrainTimer[0] >= 0.5f) {
                        ground.rebuild();
                        for (Poop p : poops) p.repositionY();
                        for (WorldManager.TreeData t : treeDataList) {
                            float newY = Ground.getHeightAt(t.x, t.z) - 0.6f;
                            t.yOffset = newY;
                            float s = t.depleted ? 0.001f : t.currentScale;
                            t.instance.setTransform(t.x, newY, t.z, t.yaw, s);
                        }
                        terrainDirty[0] = false;
                        terrainTimer[0] = 0f;
                    }
                } else {
                    terrainTimer[0] = 0f;
                }
                eaters.removeIf(Eater::isDead);
                List<Eater> newborns = new ArrayList<>();
                for (Eater e : eaters) e.update(dt, poops, eaters, newborns, world, camera.position());
                if (!newborns.isEmpty()) eaters.addAll(newborns);
                poops.removeIf(p -> p.eaten);
                for (RiggedModel m : riggedModels) m.update(dt);
                poopRegenTimer += dt;
                if (poopRegenTimer >= 15f) {
                    poopRegenTimer = 0f;
                    if (poops.size() < 30) {
                        for (int i = 0; i < 10; i++) {
                            float x = (worldRng.nextFloat() - 0.5f) * 140f;
                            float z = (worldRng.nextFloat() - 0.5f) * 140f;
                            if (Ground.getHeightAt(x, z) < Ground.WATER_LEVEL + 0.5f) continue;
                            if (Ground.getHeightAt(x, z) > 15f) continue;
                            poops.add(Poop.random(x, z, worldRng));
                        }
                    }
                }
                treeRegenTimer += dt;
                if (treeRegenTimer >= 30f) {
                    treeRegenTimer = 0f;
                    int revived = 0;
                    for (WorldManager.TreeData t : treeDataList) {
                        if (t.depleted && revived < 5) {
                            t.depleted = false;
                            t.currentScale = t.originalScale;
                            float y = Ground.getHeightAt(t.x, t.z) - 0.6f;
                            t.yOffset = y;
                            t.instance.setTransform(t.x, y, t.z, t.yaw, t.originalScale);
                            revived++;
                        }
                    }
                }
                shadowMap.bind();
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(true);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                if (sunIntensity > 0.05f) {
                    shadowShader.bind();
                    shadowShader.setUniform("uLightVP", lightVP);
                    shadowShader.setUniform1i("uTexture", 0);
                    shadowShader.setUniform1f("uWhiteKey", 1f);
                    for (HouseInstance t : staticTrees) t.render(shadowShader, camera.view(), camera.projection());
                    for (HouseInstance p : staticProps) p.render(shadowShader, camera.view(), camera.projection());
                    for (Tribe tribe : world.tribes) {
                        for (HouseInstance h : tribe.houses) h.render(shadowShader, camera.view(), camera.projection());
                    }
                    renderRiggedShadows(riggedModels, lightVP);
                    shadowShader.unbind();
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    spriteShadowShader.bind();
                    spriteShadowShader.setUniform("uLightVP", lightVP);
                    for (Sprite s : sprites) s.renderShadow(spriteShadowShader, lightVP, sunDir, now);
                    for (Eater e : eaters) e.renderShadow(spriteShadowShader, lightVP, sunDir, now);
                    spriteShadowShader.unbind();
                    GL11.glDisable(GL11.GL_BLEND);
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                }
                shadowMap.unbind(window.width(), window.height());
                renderer.clear();
                sky.render(camera, dayNight, weather, now);
                Vector3f camPos = camera.position();
                ground.render(camera.view(), camera.projection(), lightVP, shadowMap.textureId(),
                        sunDir, sunColor, ambient, camPos, fogColor, fogDensity);
                water.render(camera.view(), camera.projection(), dayNight, sunColor, camPos, now, fogColor, fogDensity);
                GL13.glActiveTexture(GL13.GL_TEXTURE1);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadowMap.textureId());
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                houseShader.bind();
                houseShader.setUniform("uLightVP", lightVP);
                houseShader.setUniform1i("uShadowMap", 1);
                houseShader.setUniform1i("uTexture", 0);
                houseShader.setUniform3f("uSunDir", sunDir.x, sunDir.y, sunDir.z);
                houseShader.setUniform3f("uSunColor", sunColor.x, sunColor.y, sunColor.z);
                houseShader.setUniform3f("uAmbient", ambient.x, ambient.y, ambient.z);
                houseShader.setUniform3f("uCamPos", camPos.x, camPos.y, camPos.z);
                houseShader.setUniform3f("uFogColor", fogColor.x, fogColor.y, fogColor.z);
                houseShader.setUniform1f("uFogDensity", fogDensity);
                houseShader.setUniform1f("uWhiteKey", 1f);
                for (Tribe tribe : world.tribes) {
                    for (HouseInstance h : tribe.houses) h.render(houseShader, camera.view(), camera.projection());
                }
                for (HouseInstance t : staticTrees) t.render(houseShader, camera.view(), camera.projection());
                for (HouseInstance p : staticProps) p.render(houseShader, camera.view(), camera.projection());
                renderRigged(riggedModels, camera.view(), camera.projection(),
                        lightVP, shadowMap.textureId(),
                        sunDir, sunColor, ambient, camPos, fogColor, fogDensity);
                houseShader.unbind();
                Poop.begin(camera.view(), camera.projection(), camPos, fogColor, fogDensity);
                Vector3f tint = new Vector3f(
                        ambient.x + sunColor.x * 0.4f,
                        ambient.y + sunColor.y * 0.4f,
                        ambient.z + sunColor.z * 0.4f
                );
                Poop.setLightTint(tint.x, tint.y, tint.z);
                for (Poop p : poops) p.render();
                Poop.end();
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glDepthMask(false);
                spriteShader.bind();
                spriteShader.setUniform3f("uLightTint", tint.x, tint.y, tint.z);
                for (Sprite s : sprites) s.render(spriteShader, camera.view(), camera.projection(), camera.position(), now);
                for (Eater e : eaters) e.render(spriteShader, camera.view(), camera.projection(), camera.position(), now);
                spriteShader.unbind();
                for (Eater e : eaters) {
                    if (e.getTribe() != null && !e.isDead()) {
                        TribeMarker.render(camera.view(), camera.projection(), e.position().x, e.position().y, e.position().z, 1.2f, e.getTribe().color);
                    }
                }
                GL11.glDepthMask(true);
                GL11.glDisable(GL11.GL_BLEND);
                ui.begin(window.width(), window.height());
                hud.render(ui, window.width(), window.height(), eaters.size() + riggedModels.size(), dayNight.timeOfDay(), weather.state().name(), Audio.isMuted());
                if (encyclopedia.isOpen()) encyclopedia.render(ui, window.width(), window.height());
                if (spawnMenu.isOpen()) spawnMenu.render(ui, window.width(), window.height());
                ui.end();
                window.swapBuffers();
            }
        } finally {
            for (Texture t : peopleTex) if (t != null) t.destroy();
            encyclopedia.destroy();
            spawnMenu.destroy();
            hud.destroy();
            if (poopIcon != null) poopIcon.destroy();
            if (brightPoopIcon != null) brightPoopIcon.destroy();
            if (treeModel != null) treeModel.destroy();
            if (vWalls != null) vWalls.destroy();
            if (vRoof != null) vRoof.destroy();
            if (vDoor != null) vDoor.destroy();
            if (vWindows != null) vWindows.destroy();
            if (tankModel != null) tankModel.destroy();
            if (policeModel != null) policeModel.destroy();
            for (RiggedModel m : riggedModels) m.destroy();
            riggedModels.clear();
            shadowMap.destroy();
            TribeMarker.destroy();
            lineRenderer.destroy();
            water.destroy();
            Poop.destroyAll();
            ui.destroy();
            spriteShader.destroy();
            spriteShadowShader.destroy();
            houseShader.destroy();
            shadowShader.destroy();
            sky.destroy();
            ground.destroy();
            window.destroy();
        }
    }

    /** Персонажи в теневом проходе. */
    private static void renderRiggedShadows(List<RiggedModel> models, Matrix4f lightVP) {
        if (models == null || models.isEmpty()) return;
        for (RiggedModel m : models) m.renderShadow(lightVP);
    }

    /** Персонажи в основном проходе (свой скининг-шейдер). */
    private static void renderRigged(List<RiggedModel> models,
                                     Matrix4f view, Matrix4f proj,
                                     Matrix4f lightVP, int shadowTex,
                                     Vector3f sunDir, Vector3f sunColor, Vector3f ambient,
                                     Vector3f camPos, Vector3f fogColor, float fogDensity) {
        if (models == null || models.isEmpty()) return;
        for (RiggedModel m : models)
            m.render(view, proj, lightVP, shadowTex, sunDir, sunColor, ambient, camPos, fogColor, fogDensity);
    }

    private static float[] spawnPoint(Camera camera) {
        Vector3f front = camera.frontVector();
        float yaw = (float) Math.atan2(front.z, front.x);
        Vector3f cam = camera.position();
        float x = cam.x + (float) Math.cos(yaw) * 10f;
        float z = cam.z + (float) Math.sin(yaw) * 10f;
        float y = Ground.getHeightAt(x, z);
        return new float[]{ x, y, z };
    }

    private static void spawnRigged(List<RiggedModel> list, String obj, String tex,
                                    float x, float z, float height) {
        if (!new File(obj).exists()) {
            System.err.println("[SPAWN] нет файла: " + obj);
            return;
        }
        try {
            // armCut = -1 -> авто-детект плеча по мешу
            list.add(new RiggedModel(obj, tex, x, z, height, -1f, 1.57f, 0.80f, 1.0f, false));
            System.out.println("[SPAWN] " + new File(obj).getName());
        } catch (Exception e) {
            System.err.println("[SPAWN] " + obj + ": " + e.getMessage());
        }
    }

    private static void spawnFromMenu(SpawnMenu.Action action, Camera camera,
                                      List<Poop> poops, List<Eater> eaters, Random rng,
                                      List<Tribe> tribes, List<RiggedModel> riggedModels) {
        boolean isCreature = action != SpawnMenu.Action.SPAWN_POOP
                && action != SpawnMenu.Action.SPAWN_BRIGHT_POOP
                && action != SpawnMenu.Action.SPAWN_POOP_CLUSTER;
        if (isCreature && (eaters.size() + riggedModels.size()) >= Eater.MAX_POPULATION) return;
        float[] sp = spawnPoint(camera);
        float sx = sp[0], sy = sp[1], sz = sp[2];
        if (sy < Ground.WATER_LEVEL + 0.5f) return;
        Tribe randomTribe = tribes.get(rng.nextInt(tribes.size()));
        
        switch (action) {
            case SPAWN_TEEN -> eaters.add(new Eater(Eater.Type.TEEN, sx, sz, randomTribe, rng));
            case SPAWN_FAT -> eaters.add(new Eater(Eater.Type.FAT, sx, sz, randomTribe, rng));
            case SPAWN_MUSCULAR -> eaters.add(new Eater(Eater.Type.MUSCULAR, sx, sz, randomTribe, rng));
            case SPAWN_FEMALE -> eaters.add(new Eater(Eater.Type.FEMALE, sx, sz, randomTribe, rng));
            case SPAWN_PREGNANT -> eaters.add(new Eater(Eater.Type.PREGNANT, sx, sz, randomTribe, rng));
            case SPAWN_BABY -> eaters.add(new Eater(Eater.Type.BABY, sx, sz, randomTribe, rng));
            case SPAWN_YOUNG_BOY -> eaters.add(new Eater(Eater.Type.YOUNG_BOY, sx, sz, randomTribe, rng));
            
            case SPAWN_POOP -> poops.add(Poop.random(sx, sz, rng));
            case SPAWN_BRIGHT_POOP -> poops.add(Poop.bright(sx, sz, rng));
            
            case SPAWN_POOP_CLUSTER -> {
                for (int i = 0; i < 5; i++) {
                    float a = rng.nextFloat() * (float) (Math.PI * 2);
                    float r = 0.5f + rng.nextFloat() * 3f;
                    float px = sx + (float) Math.cos(a) * r;
                    float pz = sz + (float) Math.sin(a) * r;
                    if (Ground.getHeightAt(px, pz) < Ground.WATER_LEVEL + 0.5f) continue;
                    poops.add(Poop.random(px, pz, rng));
                }
            }
            case SPAWN_COMBO -> {
                eaters.add(new Eater(Eater.Type.TEEN, sx, sz, randomTribe, rng));
                for (int i = 0; i < 5; i++) {
                    float a = rng.nextFloat() * (float) (Math.PI * 2);
                    float r = 1.5f + rng.nextFloat() * 5f;
                    float px = sx + (float) Math.cos(a) * r;
                    float pz = sz + (float) Math.sin(a) * r;
                    if (Ground.getHeightAt(px, pz) < Ground.WATER_LEVEL + 0.5f) continue;
                    poops.add(Poop.random(px, pz, rng));
                }
            }
            case SPAWN_TEEN_CROWD -> {
                for (int i = 0; i < 5; i++) {
                    if ((eaters.size() + riggedModels.size()) >= Eater.MAX_POPULATION) break;
                    float a = rng.nextFloat() * (float) (Math.PI * 2);
                    float r = 0.5f + rng.nextFloat() * 3f;
                    eaters.add(new Eater(Eater.Type.TEEN, sx + (float) Math.cos(a) * r, sz + (float) Math.sin(a) * r, randomTribe, rng));
                }
            }
            
            case SPAWN_MRH_NORMIS -> spawnRigged(riggedModels,
                    "mesh/MrH/meshy-model-2026-11-13T06-34-41-877Z(1).obj",
                    "mesh/MrH/texture_21.png", sx, sz, 2.0f);
            case SPAWN_MRH_FEMALE -> spawnRigged(riggedModels,
                    "mesh/MrH/ww/womento.obj", "mesh/MrH/ww/texture_21.png", sx, sz, 1.7f);
            case SPAWN_MRH_KACHOK -> spawnRigged(riggedModels,
                    "mesh/MrH/kk/kachok.obj", "mesh/MrH/kk/texture_84.png", sx, sz, 2.0f);
            case SPAWN_MRH_TOLSTIY -> spawnRigged(riggedModels,
                    "mesh/MrH/tt/tolstiy.obj", "mesh/MrH/tt/texture_115.png", sx, sz, 1.9f);
            case SPAWN_MRH_PUPS -> spawnRigged(riggedModels,
                    "mesh/MrH/uu/pups.obj", "mesh/MrH/uu/texture_21.png", sx, sz, 1.0f);
            case SPAWN_MRH_POLICE -> spawnRigged(riggedModels,
                    "mesh/MrH/pp/police.obj", "mesh/MrH/pp/texture_21.png", sx, sz, 1.95f);
            case SPAWN_MRH_COM -> spawnRigged(riggedModels,
                    "mesh/MrH/cc/com.obj", "mesh/MrH/cc/texture_52.png", sx, sz, 1.9f);
            case SPAWN_MRH_BEREM -> spawnRigged(riggedModels,
                    "mesh/MrH/BB/berem.obj", "mesh/MrH/BB/texture_142.png", sx, sz, 1.7f);
            case SPAWN_MRH_SOLDIER -> spawnRigged(riggedModels,
                    "mesh/MrH/aa/ca.obj", "mesh/MrH/aa/texture_79.png", sx, sz, 1.95f);
        }
    }
}

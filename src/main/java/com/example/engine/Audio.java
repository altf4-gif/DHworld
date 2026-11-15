package com.example.engine;

/** Звук в мини-игре не нужен, но RiggedModel ссылается на Audio.playOnce. */
public final class Audio {
    private Audio() { }
    public static void playOnce(String path, float volume) { }
}

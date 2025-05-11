package com.zeta.ssh;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class ED25519KeyGeneratorTest {

    @Test
    void generateAndSaveEd25519KeyPair() {

        Path pub = Paths.get(System.getProperty("user.home"), ".aa", "id_ed25519.pub");
        Path pri = Paths.get(System.getProperty("user.home"), ".aa", "id_ed25519");

        ED25519KeyGenerator.generateAndSaveEd25519KeyPair(pri,pub,null,"测试");
    }

    @Test
    void getPublicKey() {
        System.out.println(ED25519KeyGenerator.getPublicKey());
    }
}
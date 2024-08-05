package com.wild.utils;

import org.junit.jupiter.api.Test;

/**
 * @description:
 * @Author: yunhao_dev
 * @Date: 2024/8/4 11:59
 */
class SnowflakeIdWorkerTest {
    @Test
    void tilNextMillis() {
        SnowflakeIdWorker snowMaker = new SnowflakeIdWorker(0);
        for (int i = 0; i < 100; i++) {
            long id = snowMaker.nextId();
            System.out.println(id);
        }
    }

}
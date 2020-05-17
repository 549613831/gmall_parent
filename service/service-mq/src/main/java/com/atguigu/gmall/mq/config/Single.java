package com.atguigu.gmall.mq.config;

public class Single {
    public static final Single SINGLE = new Single();

    private Single() {

    }

    public Single getSingle() {
        return SINGLE;
    }
}

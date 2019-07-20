package es.upm.woa.group4.common;

public class Args {

    public static Object[] args(Object... args) {
        return args == null ? empty() : args;
    }

    public static Object[] empty() {
        return new Object[] {};
    }

}
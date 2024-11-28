package com.george.internalCommon.util;

// This class serves the SSE and generates keys
public class SsePrefixUtils {

    public static  final String sperator = "$";

    public  static String generatorSseKey(Long userId , String identity){
        return userId+sperator+identity;
    }
}

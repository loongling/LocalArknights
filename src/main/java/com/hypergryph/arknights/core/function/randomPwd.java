package com.hypergryph.arknights.core.function;

import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class randomPwd {
    private static final String lowStr = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String specialStr = "~!@#$%/";
    private static final String numStr = "0123456789";

    public randomPwd() {
    }

    private static char getRandomChar(String str) {
        SecureRandom random = new SecureRandom();
        return str.charAt(random.nextInt(str.length()));
    }

    private static char getLowChar() {return getRandomChar(lowStr);}
    private static char getSpecialChar() {return getRandomChar(specialStr);}
    private static char getNumChar() {return getRandomChar(numStr);}
    private static char getUpperChar() {return Character.toUpperCase(getLowChar());}

    private static char getRandomChar(int funNum) {
        switch (funNum) {
            case 0:
                return getLowChar();
            case 1:
                return getUpperChar();
            case 2:
                return getNumChar();
            default:
                return getSpecialChar();
        }
    }

    public static String getRandomPwd(int num) {
        List<Character> list = new ArrayList(num);
        list.add(getLowChar());
        list.add(getUpperChar());
        list.add(getNumChar());
        list.add(getSpecialChar());
        for(int i = 4; i < num; ++i) {
            SecureRandom random = new SecureRandom();
            int funNum = random.nextInt(4);
            list.add(getRandomChar(funNum));
        }
        Collections.shuffle(list);
        StringBuilder stringBuilder = new StringBuilder(list.size());
        Iterator var6 = list.iterator();
        while(var6.hasNext()) {
            Character c = (Character)var6.next();
            stringBuilder.append(c);
        }
        return stringBuilder.toString();
    }

    public static String randomKey(int num) {
        List<Character> list = new ArrayList(num);
        list.add(getLowChar());
        list.add(getUpperChar());
        list.add(getNumChar());
        for(int i = 4; i < num; ++i) {
            SecureRandom random = new SecureRandom();
            int funNum = random.nextInt(4);
            list.add(getRandomChar(funNum));
        }
        Collections.shuffle(list);
        StringBuilder stringBuilder = new StringBuilder(list.size());
        Iterator var6 = list.iterator();
        while(var6.hasNext()) {
            Character c = (Character)var6.next();
            stringBuilder.append(c);
        }
        return stringBuilder.toString();
    }
}

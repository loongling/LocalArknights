package com.hypergryph.arknights.game.autochessRoom;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

/** 卫戍协议 TCP 帧与基础类型。帧: [u32 长度][u32 opcode][payload] */
public final class AutoChessProtocol {

    public static final int HB_C2S = 1;
    public static final int HB_S2C = 2;
    public static final int AUTH = 5;
    public static final int SERVER_TIME = 6;
    public static final int ROOM_CLOSE = 604;
    public static final int EMOTE_C2S = 615;
    public static final int EMOTE_S2C = 616;
    public static final int ROOM_SNAPSHOT = 802;
    public static final int PLAYER_EVENT = 804;
    public static final int READY = 805;
    public static final int START = 807;
    public static final int PICK_BAND = 809;
    public static final int BAND_SYNC = 810;
    public static final int IDLE_ACTION = 815;
    public static final int PICK_MODE = 817;
    public static final int MODE_SYNC = 818;
    public static final int KICK = 823;
    public static final int NAME_CARD = 824;
    public static final int FINISH_CONFIRM = 831;
    public static final int FINISH_ACK = 832;
    public static final int ALL_STATE = 300;
    public static final int STEP = 302;
    public static final int BOARD_STATUS = 310;
    public static final int CHANGE_POS = 327;
    public static final int BROADCAST = 328;
    public static final int UNIT_ACTION = 335;
    public static final int CHAR_STATUS = 348;
    public static final int KILL_ENEMY = 371;
    public static final int ROUND_NEXT = 376;
    public static final int SETTLE = 379;

    private AutoChessProtocol() {
    }

    public static ByteBuf frame(int opcode, ByteBuf payload) {
        int len = payload == null ? 0 : payload.readableBytes();
        ByteBuf out = Unpooled.buffer(len + 8);
        out.writeInt(len);
        out.writeInt(opcode);
        if (len > 0) {
            out.writeBytes(payload);
        }
        return out;
    }

    public static String readStr(ByteBuf in) {
        int n = in.readUnsignedShort();
        byte[] b = new byte[n];
        in.readBytes(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    public static void writeStr(ByteBuf out, String s) {
        byte[] b = s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(b.length);
        out.writeBytes(b);
    }

    /** 读取 serverConfig 中 server.autochess.<key> */
    public static int port(String key, int def) {
        try {
            JSONObject server = ArknightsApplication.serverConfig.getJSONObject("server");
            if (server == null) {
                return def;
            }
            JSONObject ac = server.getJSONObject("autochess");
            if (ac == null || !ac.containsKey(key)) {
                return def;
            }
            return ac.getIntValue(key);
        } catch (Exception e) {
            return def;
        }
    }
}

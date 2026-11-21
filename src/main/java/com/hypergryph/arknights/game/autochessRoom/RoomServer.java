package com.hypergryph.arknights.game.autochessRoom;

import com.hypergryph.arknights.ArknightsApplication;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 卫戍协议房间服（config: server.autochess.room，默认 6001） */
@Component
public class RoomServer implements InitializingBean, DisposableBean {

    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private int port;

    private final Map<String, ChannelGroup> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        port = AutoChessProtocol.port("room", 6001);
        boss = new NioEventLoopGroup(1);
        worker = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new LengthFieldBasedFrameDecoder(1 << 20, 0, 4, 4, 4))
                                .addLast(new FrameEncoder())
                                .addLast(new Handler());
                    }
                });
        b.bind(port).sync();
        ArknightsApplication.LOGGER.info("[autochess] room server listening on " + port);
    }

    @Override
    public void destroy() {
        if (boss != null) {
            boss.shutdownGracefully();
        }
        if (worker != null) {
            worker.shutdownGracefully();
        }
    }

    /** 调试用：原样打印客户端发来的字节，不解析、不断开 */
    static class RawLogger extends io.netty.channel.ChannelInboundHandlerAdapter {
        private int n = 0;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            io.netty.buffer.ByteBuf buf = (io.netty.buffer.ByteBuf) msg;
            if (n < 4) {
                n++;
                int len = Math.min(buf.readableBytes(), 96);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < len; i++) {
                    sb.append(String.format("%02x ", buf.getByte(buf.readerIndex() + i)));
                }
                ArknightsApplication.LOGGER.info("[autochess][room] RAW#" + n
                        + " bytes=" + buf.readableBytes() + " : " + sb);
            } else {
                ArknightsApplication.LOGGER.info("[autochess][room] RAW#more bytes=" + buf.readableBytes());
            }
            buf.release();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ArknightsApplication.LOGGER.warn("[autochess][room] raw error: " + cause);
            ctx.close();
        }
    }

    static class FrameEncoder extends MessageToByteEncoder<Object[]> {
        @Override
        protected void encode(ChannelHandlerContext ctx, Object[] msg, ByteBuf out) {
            int op = (Integer) msg[0];
            ByteBuf payload = (ByteBuf) msg[1];
            int len = payload == null ? 0 : payload.readableBytes();
            out.writeInt(len);
            out.writeInt(op);
            if (len > 0) {
                out.writeBytes(payload);
                payload.release();
            }
        }
    }

    static void send(ChannelHandlerContext ctx, int op, ByteBuf payload) {
        ctx.writeAndFlush(new Object[]{op, payload});
    }

    class Handler extends SimpleChannelInboundHandler<ByteBuf> {

        private String roomId;
        private String uid;

        private int frameCount = 0;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf in) {
            // 调试：打印前几帧的原始字节，用于校准帧格式
            if (frameCount < 3) {
                frameCount++;
                int n = Math.min(in.readableBytes(), 64);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    sb.append(String.format("%02x ", in.getByte(in.readerIndex() + i)));
                }
                ArknightsApplication.LOGGER.info("[autochess][room] RAW frame#" + frameCount
                        + " len=" + in.readableBytes() + " : " + sb);
            }
            if (in.readableBytes() < 4) {
                return;
            }
            int op = in.readInt();
            ByteBuf body = in.readSlice(in.readableBytes());
            try {
                switch (op) {
                    case AutoChessProtocol.HB_C2S -> {
                        ByteBuf p = Unpooled.buffer(body.readableBytes());
                        p.writeBytes(body);
                        send(ctx, AutoChessProtocol.HB_S2C, p);
                    }
                    case AutoChessProtocol.AUTH -> {
                        uid = AutoChessProtocol.readStr(body);
                        String teamId = AutoChessProtocol.readStr(body);
                        String token = AutoChessProtocol.readStr(body);
                        roomId = teamId;
                        rooms.computeIfAbsent(teamId, k -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)).add(ctx.channel());
                        ArknightsApplication.LOGGER.info("[autochess][room] auth uid=" + uid + " team=" + teamId);
                        // 官服结构: u32 0 | u32 时间戳 | u32 0 | u16 0
                        ByteBuf p = Unpooled.buffer(14);
                        p.writeInt(0);
                        p.writeInt((int) (System.currentTimeMillis() / 1000L));
                        p.writeInt(0);
                        p.writeShort(0);
                        send(ctx, AutoChessProtocol.SERVER_TIME, p);

                        // 802 房间快照（按官服 194B 单人样本结构重建）
                        try {
                            ByteBuf snap = buildSnapshot(ctx, uid, teamId);
                            send(ctx, AutoChessProtocol.ROOM_SNAPSHOT, snap);
                            ArknightsApplication.LOGGER.info("[autochess][room] 802 sent uid=" + uid
                                    + " team=" + teamId + " len=" + snap.readableBytes());
                        } catch (Exception e) {
                            ArknightsApplication.LOGGER.error("[autochess][room] 802 build failed", e);
                        }
                    }
                    case AutoChessProtocol.READY, AutoChessProtocol.START,
                         AutoChessProtocol.IDLE_ACTION, AutoChessProtocol.FINISH_CONFIRM -> {
                        ArknightsApplication.LOGGER.info("[autochess][room] op=" + op + " uid=" + uid);
                        if (op == AutoChessProtocol.FINISH_CONFIRM) {
                            send(ctx, AutoChessProtocol.FINISH_ACK, null);
                        }
                    }
                    case AutoChessProtocol.PICK_BAND -> {
                        String band = AutoChessProtocol.readStr(body);
                        ArknightsApplication.LOGGER.info("[autochess][room] pick band=" + band);
                        ByteBuf p = Unpooled.buffer();
                        AutoChessProtocol.writeStr(p, band);
                        broadcast(roomId, AutoChessProtocol.BAND_SYNC, p);
                    }
                    case AutoChessProtocol.PICK_MODE -> {
                        String mode = AutoChessProtocol.readStr(body);
                        ArknightsApplication.LOGGER.info("[autochess][room] pick mode=" + mode);
                        ByteBuf p = Unpooled.buffer();
                        AutoChessProtocol.writeStr(p, mode);
                        broadcast(roomId, AutoChessProtocol.MODE_SYNC, p);
                    }
                    case AutoChessProtocol.EMOTE_C2S -> {
                        ByteBuf p = Unpooled.buffer(body.readableBytes());
                        p.writeBytes(body);
                        broadcast(roomId, AutoChessProtocol.EMOTE_S2C, p);
                    }
                    case 603 -> {   // ACTeamLeaveUp：离开/解散房间
                        ArknightsApplication.LOGGER.info("[autochess][room] leave/dissolve by " + uid);
                        ByteBuf p = Unpooled.buffer(1);
                        p.writeByte(0);
                        broadcast(roomId, AutoChessProtocol.ROOM_CLOSE, p);   // 604 房间关闭
                        ChannelGroup g = roomId == null ? null : rooms.remove(roomId);
                        if (g != null) { g.close(); }
                        ctx.close();
                    }
                    case 819, 827 -> ArknightsApplication.LOGGER.info("[autochess][room] op=" + op + " len=" + body.readableBytes() + " uid=" + uid);
                    case AutoChessProtocol.KICK -> ArknightsApplication.LOGGER.info("[autochess][room] kick by " + uid);
                    default -> ArknightsApplication.LOGGER.info("[autochess][room] unhandled op=" + op + " len=" + body.readableBytes());
                }
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("[autochess][room] op=" + op + " error: " + e);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (roomId != null) {
                ChannelGroup g = rooms.get(roomId);
                if (g != null) {
                    g.remove(ctx.channel());
                    if (g.isEmpty()) {
                        rooms.remove(roomId);
                    }
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ArknightsApplication.LOGGER.warn("[autochess][room] handler error: " + cause);
            ctx.close();
        }
    }

    /** 写 u32 长度前缀字符串（802 的 modeId 用这种） */
    private static void wStr32(ByteBuf b, String s) {
        byte[] d = s == null ? new byte[0] : s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        b.writeInt(d.length);
        b.writeBytes(d);
    }

    /**
     * 构建 802 房间快照。
     * 结构依据官服 194B 单人样本 + 2126B 四人样本逆向：
     *   u32 0 | u32 ts | u8 1 | str32(modeId) | str16(uid) | u32 0 | u16 玩家数
     *   每玩家: uid/nick/nickNumber/"ICON"/avatar/secretary/secretarySkin + 2×u8
     *           + nameCardSkin + u32 tmpl + u32 0 + str16(bandId)
     *   尾部: u16 0 | str16(sceneId) | str16(地址) | str16(token)
     *         | u32 0 | u32 ts | u32 0 | u8 玩家数 | 每玩家(uid+band+u32) | str16(stageId)
     */
    private ByteBuf buildSnapshot(ChannelHandlerContext ctx, String uid, String teamId) {
        ByteBuf b = Unpooled.buffer(512);
        long now = System.currentTimeMillis() / 1000L;

        com.alibaba.fastjson.JSONObject user = null;
        try {
            java.util.List<com.hypergryph.arknights.core.pojo.Account> accs =
                    com.hypergryph.arknights.core.dao.userDao.queryAccountByUid(Long.parseLong(uid));
            if (!accs.isEmpty()) {
                user = com.alibaba.fastjson.JSONObject.parseObject(accs.get(0).getUser());
            }
        } catch (Exception ignore) {
        }
        com.alibaba.fastjson.JSONObject status = user == null ? null : user.getJSONObject("status");
        String nick = status == null ? "博士" : status.getString("nickName");
        if (nick == null || nick.isEmpty()) {
            nick = "博士";
        }
        String nickNum = status == null ? null : status.getString("nickNumber");
        if (nickNum == null || nickNum.isEmpty() || "-1".equals(nickNum)) {
            nickNum = "0000";
        }
        // 头像优先取 status.avatar {type,id}，其次 status.avatarId，最后兜底
        String avatarType = "ICON";
        String avatar = null;
        if (status != null) {
            com.alibaba.fastjson.JSONObject av = status.getJSONObject("avatar");
            if (av != null) {
                String at = av.getString("type");
                String aid = av.getString("id");
                if (at != null && !at.isEmpty()) { avatarType = at; }
                if (aid != null && !aid.isEmpty()) { avatar = aid; }
            }
            if (avatar == null) {
                String aid2 = status.getString("avatarId");
                if (aid2 != null && !aid2.isEmpty() && !"0".equals(aid2)) { avatar = aid2; }
            }
        }
        if (avatar == null) { avatar = "avatar_def_10"; }
        String secretary = status == null ? "char_002_amiya" : status.getString("secretary");
        String secretarySkin = status == null ? "char_002_amiya#1" : status.getString("secretarySkinId");
        int level = status == null ? 1 : status.getIntValue("level");
        String cardSkin = "nc_rhodes_default";
        if (user != null && user.getJSONObject("nameCardStyle") != null
                && user.getJSONObject("nameCardStyle").getJSONObject("skin") != null) {
            String s = user.getJSONObject("nameCardStyle").getJSONObject("skin").getString("selected");
            if (s != null && !s.isEmpty()) {
                cardSkin = s;
            }
        }

        String sceneId = (teamId == null || teamId.isEmpty()) ? "loongling10029" : teamId;
        String addr = "127.0.0.1:" + AutoChessProtocol.port("multiServer", 6101);
        String token = java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "") + "-" + now;

        // ===== AutoChessTeamStatus =====
        // teamStateEndTs：以库里房间的 expireTs 为准（创建时固定，加入不刷新）
        long endTs = now + 900L;
        try {
            java.util.Map<String, Object> room =
                    com.hypergryph.arknights.core.dao.autochessRoomDao.queryRoom(teamId);
            if (room != null && room.get("expireTs") != null) {
                endTs = ((Number) room.get("expireTs")).longValue();
            }
        } catch (Exception ignore) {
        }
        b.writeLong(endTs);                                // teamStateEndTs
        b.writeByte(1);                                    // state (AutoChessTeamState)
        wStr32(b, "mode_multi_funny");                     // modeId（u32 长度前缀，样本 00 00 00 10）
        AutoChessProtocol.writeStr(b, uid);                // ownerUid
        b.writeShort(0);                                   // monsterKeys 空
        AutoChessProtocol.writeStr(b, "boss_1");           // bossId
        b.writeShort(1);                                   // players 数量

        // ===== MsgAutoChessPlayerStatus =====
        AutoChessProtocol.writeStr(b, uid);                // uid
        AutoChessProtocol.writeStr(b, nick);               // nickName
        AutoChessProtocol.writeStr(b, nickNum);            // nickNumber
        AutoChessProtocol.writeStr(b, avatarType);         // avatarType
        AutoChessProtocol.writeStr(b, avatar);             // avatarId
        AutoChessProtocol.writeStr(b, secretary);          // secretary
        AutoChessProtocol.writeStr(b, secretarySkin);      // secretarySkinId
        b.writeByte(0);                                    // secretarySkinSp
        AutoChessProtocol.writeStr(b, cardSkin);           // nameCardSkinId
        b.writeInt(0);                                     // nameCardSkinTmpl
        byte[] band = "band_damaztic".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        b.writeByte(band.length);                          // bandId（u8 长度）
        b.writeBytes(band);
        b.writeInt(level);                                 // level
        b.writeByte(0);                                    // state
        b.writeByte(0);                                    // connState（0=在线，1 会显示"失去信号"）
        b.writeInt((int) now);                             // jointTs
        b.writeInt(0);                                     // medalCount (u32)
        b.writeByte(0);                                    // gsChannel (u8!)
        b.writeByte(0);                                    // currentOptionId (u8!)
        b.writeShort(0);                                   // bannedOptions 空
        b.writeShort(0);                                   // diyChessIdentifiers 空

        // ===== STSceneInfo scene =====
        AutoChessProtocol.writeStr(b, sceneId);            // sceneID
        AutoChessProtocol.writeStr(b, addr);               // address
        AutoChessProtocol.writeStr(b, token);              // token

        // ===== StrategyDecisionBrief =====
        b.writeLong(0);                                    // roundFinTs
        b.writeByte(0);                                    // canSkip
        AutoChessProtocol.writeStr(b, uid);                // currUID
        b.writeShort(0);                                   // choices 空

        b.writeByte(0);                                    // matchFlag
        AutoChessProtocol.writeStr(b, "act3autochess_m01");// stageId

        return b;
    }

    private void broadcast(String roomId, int op, ByteBuf payload) {
        if (roomId == null) {
            return;
        }
        ChannelGroup g = rooms.get(roomId);
        if (g == null) {
            return;
        }
        byte[] copy = new byte[payload.readableBytes()];
        payload.getBytes(payload.readerIndex(), copy);
        for (var ch : g) {
            ch.writeAndFlush(new Object[]{op, Unpooled.wrappedBuffer(copy.clone())});
        }
        payload.release();
    }
}
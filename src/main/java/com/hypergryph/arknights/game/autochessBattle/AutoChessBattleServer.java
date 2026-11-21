package com.hypergryph.arknights.game.autochessBattle;

import com.hypergryph.arknights.ArknightsApplication;

import com.hypergryph.arknights.game.autochessRoom.AutoChessProtocol;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/** 卫戍协议对局服（config: server.autochess.multiServer，默认 6101） */
@Component
public class AutoChessBattleServer implements InitializingBean, DisposableBean {

    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private int port;

    @Override
    public void afterPropertiesSet() throws Exception {
        port = AutoChessProtocol.port("multiServer", 6101);
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
                                .addLast(new Encoder())
                                .addLast(new Handler());
                    }
                });
        b.bind(port).sync();
        ArknightsApplication.LOGGER.info("[autochess] battle server listening on " + port);
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

    static class Encoder extends MessageToByteEncoder<Object[]> {
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

    /** 最小可用 300：1 名玩家、空编队；字段顺序 = 客户端类声明顺序 */
    static ByteBuf buildAllState(String uid, String mode, String stage, String boss) {
        ByteBuf p = Unpooled.buffer(256);
        AutoChessProtocol.writeStr(p, mode);
        AutoChessProtocol.writeStr(p, stage);
        AutoChessProtocol.writeStr(p, boss);
        AutoChessProtocol.writeStr(p, "");
        p.writeInt((int) (System.currentTimeMillis() / 1000L));
        p.writeByte(1);
        p.writeShort(0);
        p.writeShort(1);
        AutoChessProtocol.writeStr(p, uid);
        p.writeByte(1);
        p.writeShort(0);
        AutoChessProtocol.writeStr(p, "band_dance");
        AutoChessProtocol.writeStr(p, "player");
        AutoChessProtocol.writeStr(p, "0000");
        AutoChessProtocol.writeStr(p, "ICON");
        AutoChessProtocol.writeStr(p, "avatar_def_10");
        AutoChessProtocol.writeStr(p, "char_002_amiya");
        AutoChessProtocol.writeStr(p, "char_002_amiya#1");
        p.writeByte(0);
        p.writeInt(1);
        AutoChessProtocol.writeStr(p, "nc_rhodes_default");
        p.writeInt(0);
        AutoChessProtocol.writeStr(p, "ac_n_initial");
        p.writeShort(0);
        return p;
    }

    class Handler extends SimpleChannelInboundHandler<ByteBuf> {

        private String uid;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf in) {
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
                        ArknightsApplication.LOGGER.info("[autochess][battle] auth uid=" + uid);
                        send(ctx, AutoChessProtocol.ALL_STATE,
                                buildAllState(uid, "mode_multi_funny", "act3autochess_m01", "boss_1"));
                    }
                    case AutoChessProtocol.CHANGE_POS, AutoChessProtocol.UNIT_ACTION,
                         AutoChessProtocol.KILL_ENEMY, AutoChessProtocol.ROUND_NEXT -> {
                        ByteBuf p = Unpooled.buffer(body.readableBytes());
                        p.writeBytes(body);
                        send(ctx, AutoChessProtocol.BROADCAST, p);
                    }
                    default -> ArknightsApplication.LOGGER.info("[autochess][battle] unhandled op=" + op + " len=" + body.readableBytes());
                }
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("[autochess][battle] op=" + op + " error: " + e);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
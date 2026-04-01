package com.example.bypass;

import io.netty.channel.*;
import org.bukkit.Bukkit;
import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Level;

public class NettyInjector {

    private final BypassAuthPlugin plugin;
    private ChannelFuture serverChannelFuture;

    public NettyInjector(BypassAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    public void inject() {
        try {
            Object craftServer = Bukkit.getServer();
            Object serverConnection = getField(craftServer.getClass(), "getServerConnection").get(craftServer);
            
            // In 1.21.1, it's often a list of futures
            Field channelsField = null;
            for (Field f : serverConnection.getClass().getDeclaredFields()) {
                if (f.getType() == List.class) {
                    channelsField = f;
                    break;
                }
            }
            
            if (channelsField != null) {
                channelsField.setAccessible(true);
                List<ChannelFuture> channels = (List<ChannelFuture>) channelsField.get(serverConnection);
                
                for (ChannelFuture channelFuture : channels) {
                    injectToChannel(channelFuture);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to inject Netty handler", e);
        }
    }

    private void injectToChannel(ChannelFuture channelFuture) {
        channelFuture.channel().pipeline().addFirst(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof Channel channel) {
                    channel.pipeline().addLast(new BypassHandler(plugin));
                }
                super.channelRead(ctx, msg);
            }
        });
    }

    public void uninject() {
        // Technically not needed for this plugin as it resets on restart, but good practice
    }

    private Field getField(Class<?> clazz, String name) {
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) return getField(clazz.getSuperclass(), name);
            return null;
        }
    }
}

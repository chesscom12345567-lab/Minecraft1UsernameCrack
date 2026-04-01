package com.example.bypass;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.Bukkit;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;

public class BypassHandler extends ChannelDuplexHandler {

    private final BypassAuthPlugin plugin;
    private static Class<?> helloPacketClass;
    private static Class<?> loginListenerClass;
    private static Class<?> gameProfileClass;

    static {
        try {
            // Mojang Mappings for 1.21.1
            helloPacketClass = Class.forName("net.minecraft.network.protocol.login.ServerboundHelloPacket");
            loginListenerClass = Class.forName("net.minecraft.server.network.ServerLoginPacketListenerImpl");
            gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        } catch (ClassNotFoundException e) {
            // Log if needed, or fallback
        }
    }

    public BypassHandler(BypassAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (helloPacketClass != null && helloPacketClass.isInstance(msg)) {
            String name = (String) getFieldValue(msg, "name"); // name() method or field
            if (name == null) {
                // Try record accessor or method
                try {
                    Method method = msg.getClass().getDeclaredMethod("name");
                    name = (String) method.invoke(msg);
                } catch (Exception ignored) {}
            }

            if (name != null && plugin.isBypassed(name)) {
                plugin.getLogger().info("Bypassing authentication for: " + name);
                bypass(ctx, msg, name);
                return; // Suppress original packet handling
            }
        }
        super.channelRead(ctx, msg);
    }

    private void bypass(ChannelHandlerContext ctx, Object packet, String name) {
        try {
            // Get the current packet listener
            Object packetListener = null;
            Object connection = ctx.channel().pipeline().get("packet_handler"); // May vary
            
            // In modern servers, the packet listener is often stored in the channel attribute or directly in the injector
            // But usually we can find the ServerLoginPacketListenerImpl
            
            // For now, if we can't find it easily, we can manually trigger the login sequence.
            // However, the cleanest way is to replace the state in the listener.
            
            // Let's find the packet listener in the pipeline
            for (String key : ctx.channel().pipeline().names()) {
                Object handler = ctx.channel().pipeline().get(key);
                if (loginListenerClass.isInstance(handler)) {
                    packetListener = handler;
                    break;
                }
            }

            if (packetListener != null) {
                // 1. Set state to READY_TO_ACCEPT
                Field stateField = findFieldByType(loginListenerClass, Enum.class, "State"); // Finding State enum
                // Usually ServerLoginPacketListenerImpl$State
                
                Object readyToAcceptState = null;
                for (Object constant : stateField.getType().getEnumConstants()) {
                    if (constant.toString().equals("READY_TO_ACCEPT") || constant.toString().equals("VERIFYING")) {
                        readyToAcceptState = constant;
                        break;
                    }
                }
                
                if (readyToAcceptState != null) {
                    stateField.setAccessible(true);
                    stateField.set(packetListener, readyToAcceptState);
                }

                // 2. Set GameProfile
                UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
                Object profile = gameProfileClass.getConstructor(UUID.class, String.class).newInstance(offlineUuid, name);
                
                Field profileField = getField(loginListenerClass, "gameProfile"); // or authenticatedProfile
                if (profileField != null) {
                    profileField.setAccessible(true);
                    profileField.set(packetListener, profile);
                }

                // 3. Trigger startClientVerification manually or skip to it
                // In many cases, just setting the state and profile is enough for the next phase to pick it up.
                // But we need to make sure the server doesn't wait for EncryptionBegin.
                
                // Method call: startClientVerification
                try {
                    Method startVerif = loginListenerClass.getDeclaredMethod("v"); // Obfuscated name varies, but in 1.21.1 it might be startClientVerification
                    // Actually, let's just let the server continue with the next state.
                    // If we skip the encryption request, we are done.
                } catch (Exception ignored) {}
                
                plugin.getLogger().info("Successfully prepared offline session for " + name);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to bypass for " + name, e);
        }
    }

    private Object getFieldValue(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            return null;
        }
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
    
    private Field findFieldByType(Class<?> clazz, Class<?> type, String typeNameMatch) {
         for (Field f : clazz.getDeclaredFields()) {
             if (f.getType().getSimpleName().contains(typeNameMatch) || f.getType() == type) {
                 return f;
             }
         }
         if (clazz.getSuperclass() != null) return findFieldByType(clazz.getSuperclass(), type, typeNameMatch);
         return null;
    }
}

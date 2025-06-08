package com.melluh.servertours.util.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.*;
import com.google.common.collect.Lists;
import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.event.RoutePlaybackEndEvent;
import com.melluh.servertours.editmode.EditingPlayer;
import com.melluh.servertours.nms.ModernMovementNmsHandler;
import com.melluh.servertours.nms.NmsHandler;
import com.melluh.servertours.playback.CraftTouringPlayer;
import com.melluh.servertours.route.point.CraftRoutePoint;
import com.melluh.servertours.util.nms.NmsAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Level;

public class PacketUtil {
    private static final Set<Integer> INVISIBLE_ENTITIES;
    private static int idCounter;

    static {
        INVISIBLE_ENTITIES = new HashSet<>();
        PacketUtil.idCounter = 5000000;
    }

    private PacketUtil() {
    }

    public static void registerProtocolLib() {
        ServerTours.getInstance().getProtocolManager().addPacketListener(new PacketAdapter(ServerTours.getInstance(), ListenerPriority.NORMAL, List.of(PacketType.Play.Client.USE_ENTITY, PacketType.Play.Client.STEER_VEHICLE, PacketType.Play.Client.TELEPORT_ACCEPT, PacketType.Play.Server.ENTITY_METADATA, PacketType.Play.Server.SPAWN_ENTITY)) {
            public void onPacketReceiving(PacketEvent packetEvent) {
                Player player = packetEvent.getPlayer();
                PacketContainer packet = packetEvent.getPacket();
                if (packet.getType() == PacketType.Play.Client.USE_ENTITY) {
                    int intValue = packet.getIntegers().read(0);
                    if (intValue == player.getEntityId()) {
                        packetEvent.setCancelled(true);
                        return;
                    }
                    EditingPlayer editingPlayer = ServerTours.getInstance().getEditModeManager().getEditingPlayer(player);
                    if (editingPlayer == null) {
                        return;
                    }
                    CraftRoutePoint pointByEntity = editingPlayer.getEditingRoute().getPointByEntity(intValue);
                    if (pointByEntity == null) {
                        return;
                    }
                    packetEvent.setCancelled(true);
                    if (pointByEntity == editingPlayer.getSelectedPoint()) {
                        editingPlayer.setSelectedPoint(null);
                    } else {
                        editingPlayer.setSelectedPoint(pointByEntity);
                    }
                } else if (packet.getType() == PacketType.Play.Client.STEER_VEHICLE) {
                    CraftTouringPlayer touringPlayer = ServerTours.getInstance().getPlaybackManager().getTouringPlayer(player);
                    if (touringPlayer != null && touringPlayer.isExitByMoving()) {
                        InternalStructure internalStructure = packet.getStructures().read(0);
                        if (internalStructure.getBooleans().read(0) || internalStructure.getBooleans().read(1) || internalStructure.getBooleans().read(2) || internalStructure.getBooleans().read(3)) {
                            Bukkit.getScheduler().runTask(ServerTours.getInstance(), () -> touringPlayer.exit(RoutePlaybackEndEvent.EndReason.EXITED));
                        }
                    }
                } else if (packet.getType() == PacketType.Play.Client.TELEPORT_ACCEPT && packet.getIntegers().read(0) == Integer.MAX_VALUE && ServerTours.getInstance().getPlaybackManager().isTouringPlayer(player)) {
                    packetEvent.setCancelled(true);
                }
            }

            public void onPacketSending(PacketEvent packetEvent) {
                Player player = packetEvent.getPlayer();
                PacketContainer packet = packetEvent.getPacket();
                if (packet.getType() == PacketType.Play.Server.NAMED_ENTITY_SPAWN || packet.getType() == PacketType.Play.Server.SPAWN_ENTITY) {
                    int intValue = packet.getIntegers().read(0);
                    if (PacketUtil.INVISIBLE_ENTITIES.contains(packet.getIntegers().read(0))) {
                        Bukkit.getScheduler().runTaskLater(ServerTours.getInstance(), () -> PacketUtil.sendEntityInvisible(List.of(player), intValue, true), 1L);
                    }
                } else if (packet.getType() == PacketType.Play.Server.ENTITY_METADATA && PacketUtil.INVISIBLE_ENTITIES.contains(packet.getIntegers().read(0))) {
                    packet.getDataValueCollectionModifier().write(0, packet.getDataValueCollectionModifier().read(0).stream().map(wrappedDataValue -> wrappedDataValue.getIndex() == 0 ? new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), (byte) 32) : wrappedDataValue).toList());
                }
            }
        });
    }

    public static void sendStand(Player player, Location location, int i) {
        PacketContainer packetContainer = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        packetContainer.getIntegers().write(0, i);
        packetContainer.getUUIDs().write(0, UUID.randomUUID());
        packetContainer.getDoubles().write(0, location.getX()).write(1, location.getY()).write(2, location.getZ());
        packetContainer.getEntityTypeModifier().write(0, EntityType.ARMOR_STAND);
        sendPacket(player, packetContainer);
    }

    public static void sendEntityData(Player player, int n, boolean b, String s) {
        List<MetadataValue> list = new ArrayList<>();
        if (b) {
            list.add(new MetadataValue(0, WrappedDataWatcher.Registry.get(Byte.class), 32));
        }
        if (s != null) {
            list.add(new MetadataValue(2, WrappedDataWatcher.Registry.getChatComponentSerializer(true), Optional.of(WrappedChatComponent.fromChatMessage(s)[0])));
            list.add(new MetadataValue(3, WrappedDataWatcher.Registry.get(Boolean.class), true));
        }
        sendPacket(player, createEntityMetadataPacket(n, list));
    }

    public static void sendStandHeadPose(Player player, int n, float n2, float n3, float n4) {
        sendPacket(player, createEntityMetadataPacket(n, List.of(new MetadataValue(16, WrappedDataWatcher.Registry.getVectorSerializer(), new Vector3F(n2, n3, n4)))));
    }

    private static PacketContainer createEntityMetadataPacket(int i, List<MetadataValue> list) {
        PacketContainer packetContainer = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        packetContainer.getIntegers().write(0, i);
        // TODO 这里有点问题 暂时无法工作 先这样
//        packetContainer.getDataValueCollectionModifier().write(0, list.stream().map(metadataValue -> new WrappedDataValue(metadataValue.index(), metadataValue.serializer(), getUnwrapped(metadataValue.value()))).collect(Collectors.toList()));
        return packetContainer;
    }

    private static Object getUnwrapped(Object o) {
        if (o instanceof Vector3F) {
            return Vector3F.getConverter().getGeneric((Vector3F) o);
        }
        if (o instanceof WrappedChatComponent wrappedChatComponent) {
            return wrappedChatComponent.getHandle();
        }
        if (o instanceof Optional<?> optional) {
            return optional.map(PacketUtil::getUnwrapped);
        }
        return o;
    }

    public static void setInvisible(int n, boolean b) {
        if (b) {
            sendEntityInvisible(Bukkit.getOnlinePlayers(), n, true);
            PacketUtil.INVISIBLE_ENTITIES.add(n);
        } else {
            PacketUtil.INVISIBLE_ENTITIES.remove(n);
            sendEntityInvisible(Bukkit.getOnlinePlayers(), n, false);
        }
    }

    private static void sendEntityInvisible(Collection<? extends Player> collection, int i, boolean z) {
        PacketContainer createEntityMetadataPacket = createEntityMetadataPacket(i, List.of(new MetadataValue(0, WrappedDataWatcher.Registry.get(Byte.class), z ? (byte) 32 : (byte) 0)));
        collection.forEach(player -> sendPacket(player, createEntityMetadataPacket));
    }

    public static void sendEntityEquipment(Player player, int i, EnumWrappers.ItemSlot itemSlot, ItemStack itemStack) {
        PacketContainer packetContainer = new PacketContainer(PacketType.Play.Server.ENTITY_EQUIPMENT);
        packetContainer.getIntegers().write(0, i);
        packetContainer.getSlotStackPairLists().write(0, Lists.newArrayList(new Pair<>(itemSlot, itemStack)));
        sendPacket(player, packetContainer);
    }

    public static void sendEntityTeleport(Player player, int i, Location location) {
        NmsHandler handler = NmsAdapter.getHandler();
        if (handler instanceof ModernMovementNmsHandler modernMovementNmsHandler) {
            modernMovementNmsHandler.sendEntityTeleportPacket(player, i, location);
            return;
        }
        PacketContainer packetContainer = new PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT);
        packetContainer.getIntegers().write(0, i);
        packetContainer.getDoubles().write(0, location.getX()).write(1, location.getY()).write(2, location.getZ());
        packetContainer.getBooleans().write(0, false);
        sendPacket(player, packetContainer);
    }

    public static void removeClientEntity(Player player, int i) {
        PacketContainer packetContainer = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        packetContainer.getIntLists().write(0, Lists.newArrayList(i));
        sendPacket(player, packetContainer);
    }

    public static int generateEntityId() {
        return PacketUtil.idCounter++;
    }

    private static void sendPacket(Player player, PacketContainer packetContainer) {
        try {
            ServerTours.getInstance().getProtocolManager().sendServerPacket(player, packetContainer);
        } catch (Exception thrown) {
            ServerTours.getInstance().getLogger().log(Level.SEVERE, "Failed to send packet", thrown);
        }
    }

    record MetadataValue(int index, WrappedDataWatcher.Serializer serializer, Object value) {
    }
}

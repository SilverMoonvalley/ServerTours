package com.melluh.servertours.util;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerRestoreWrapperTest {

    @Test
    void eagerlyDeepCopiesAndRestoresEveryOwnedFieldExactlyOnce() {
        FakePlayer fake = new FakePlayer();
        ItemStack originalStorageItem = mock(ItemStack.class);
        ItemStack capturedStorageItem = mock(ItemStack.class);
        ItemStack restoredStorageItem = mock(ItemStack.class);
        when(originalStorageItem.clone()).thenReturn(capturedStorageItem);
        when(capturedStorageItem.clone()).thenReturn(restoredStorageItem);
        when(restoredStorageItem.getAmount()).thenReturn(3);
        fake.inventory.storage = new ItemStack[]{originalStorageItem};
        fake.inventory.heldSlot = 4;
        fake.values.put("gameMode", GameMode.CREATIVE);
        fake.values.put("level", 12);
        fake.values.put("exp", 0.35f);
        fake.values.put("totalExperience", 456);
        fake.values.put("health", 17.0D);
        fake.values.put("collidable", false);
        fake.values.put("allowFlight", true);
        fake.values.put("flying", true);
        fake.values.put("velocity", new Vector(1.0D, 2.0D, 3.0D));
        fake.values.put("fallDistance", 8.5f);

        PlayerRestoreWrapper wrapper = new PlayerRestoreWrapper(fake.player);
        fake.values.put("gameMode", GameMode.ADVENTURE);
        fake.values.put("level", 0);
        fake.values.put("exp", 0.0f);
        fake.values.put("totalExperience", 0);
        fake.values.put("health", 20.0D);
        fake.values.put("collidable", true);
        fake.values.put("allowFlight", false);
        fake.values.put("flying", false);
        fake.values.put("velocity", new Vector());
        fake.values.put("fallDistance", 0.0f);

        wrapper.restore();
        wrapper.restore();

        assertEquals(3, fake.inventory.storage[0].getAmount());
        assertSame(restoredStorageItem, fake.inventory.storage[0]);
        assertEquals(4, fake.inventory.heldSlot);
        assertEquals(GameMode.CREATIVE, fake.values.get("gameMode"));
        assertEquals(12, fake.values.get("level"));
        assertEquals(0.35f, fake.values.get("exp"));
        assertEquals(456, fake.values.get("totalExperience"));
        assertEquals(17.0D, fake.values.get("health"));
        assertEquals(false, fake.values.get("collidable"));
        assertEquals(true, fake.values.get("allowFlight"));
        assertEquals(true, fake.values.get("flying"));
        assertEquals(new Vector(1.0D, 2.0D, 3.0D), fake.values.get("velocity"));
        assertEquals(8.5f, fake.values.get("fallDistance"));
        assertEquals(1, fake.inventory.storageRestoreCalls);
        assertTrue(wrapper.isRestored());
        assertTrue(wrapper.getRestoreFailures().isEmpty());
    }

    @Test
    void inventoryFailureDoesNotPreventRemainingStateRestore() {
        FakePlayer fake = new FakePlayer();
        fake.values.put("level", 9);
        PlayerRestoreWrapper wrapper = new PlayerRestoreWrapper(fake.player);
        fake.values.put("level", 0);
        fake.inventory.failStorageRestore = true;

        wrapper.restore();

        assertEquals(9, fake.values.get("level"));
        assertEquals(1, wrapper.getRestoreFailures().size());
        assertEquals("inventory", wrapper.getRestoreFailures().get(0).getOperation());
    }

    private static final class FakePlayer implements InvocationHandler {
        private final FakeInventory inventory = new FakeInventory();
        private final Map<String, Object> values = new HashMap<>();
        private final Player player = proxy(Player.class, this);

        private FakePlayer() {
            this.values.put("gameMode", GameMode.SURVIVAL);
            this.values.put("level", 0);
            this.values.put("exp", 0.0f);
            this.values.put("totalExperience", 0);
            this.values.put("health", 20.0D);
            this.values.put("collidable", true);
            this.values.put("allowFlight", false);
            this.values.put("flying", false);
            this.values.put("velocity", new Vector());
            this.values.put("fallDistance", 0.0f);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            return switch (name) {
                case "getInventory" -> this.inventory.proxy;
                case "getGameMode" -> this.values.get("gameMode");
                case "setGameMode" -> this.set("gameMode", args[0]);
                case "getLevel" -> this.values.get("level");
                case "setLevel" -> this.set("level", args[0]);
                case "getExp" -> this.values.get("exp");
                case "setExp" -> this.set("exp", args[0]);
                case "getTotalExperience" -> this.values.get("totalExperience");
                case "setTotalExperience" -> this.set("totalExperience", args[0]);
                case "getHealth" -> this.values.get("health");
                case "setHealth" -> this.set("health", args[0]);
                case "isCollidable" -> this.values.get("collidable");
                case "setCollidable" -> this.set("collidable", args[0]);
                case "getAllowFlight" -> this.values.get("allowFlight");
                case "setAllowFlight" -> this.set("allowFlight", args[0]);
                case "isFlying" -> this.values.get("flying");
                case "setFlying" -> this.set("flying", args[0]);
                case "getVelocity" -> ((Vector) this.values.get("velocity")).clone();
                case "setVelocity" -> this.set("velocity", ((Vector) args[0]).clone());
                case "getFallDistance" -> this.values.get("fallDistance");
                case "setFallDistance" -> this.set("fallDistance", args[0]);
                case "getMaxHealth" -> 20.0D;
                case "toString" -> "FakePlayer";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object set(String key, Object value) {
            this.values.put(key, value);
            return null;
        }
    }

    private static final class FakeInventory implements InvocationHandler {
        private ItemStack[] storage = new ItemStack[0];
        private ItemStack[] armor = new ItemStack[0];
        private ItemStack[] extra = new ItemStack[0];
        private ItemStack offhand;
        private int heldSlot;
        private int storageRestoreCalls;
        private boolean failStorageRestore;
        private final PlayerInventory proxy = proxy(PlayerInventory.class, this);

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getStorageContents" -> this.storage;
                case "setStorageContents" -> {
                    ++this.storageRestoreCalls;
                    if (this.failStorageRestore) {
                        throw new IllegalStateException("storage restore failed");
                    }
                    this.storage = (ItemStack[]) args[0];
                    yield null;
                }
                case "getArmorContents" -> this.armor;
                case "setArmorContents" -> this.setArmor((ItemStack[]) args[0]);
                case "getExtraContents" -> this.extra;
                case "setExtraContents" -> this.setExtra((ItemStack[]) args[0]);
                case "getItemInOffHand" -> this.offhand;
                case "setItemInOffHand" -> this.setOffhand((ItemStack) args[0]);
                case "getHeldItemSlot" -> this.heldSlot;
                case "setHeldItemSlot" -> this.setHeldSlot((Integer) args[0]);
                case "clear" -> this.clear();
                case "toString" -> "FakeInventory";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object setArmor(ItemStack[] value) {
            this.armor = value;
            return null;
        }

        private Object setExtra(ItemStack[] value) {
            this.extra = value;
            return null;
        }

        private Object setOffhand(ItemStack value) {
            this.offhand = value;
            return null;
        }

        private Object setHeldSlot(int value) {
            this.heldSlot = value;
            return null;
        }

        private Object clear() {
            this.storage = new ItemStack[0];
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0D;
        if (type == char.class) return '\0';
        return null;
    }
}

package com.melluh.servertours.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public enum CustomItem {
    CREATE_ROUTE_POINT(Material.EMERALD, "&bCreate route point"),
    PREVIEW_ROUTE(new ItemBuilder(Material.PLAYER_HEAD).name("&bPreview route").textureUrl("adf8909498c25f6e75ef19e376a7f84f61af234525d639ba463f991f4c82d0").build()),
    SELECT_ROUTE_POINT(Material.PAPER, "&bSelect route point"),
    TOGGLE_PARTICLES_ON(Material.LIME_DYE, "&bToggle particles &a(ON)"),
    TOGGLE_PARTICLES_OFF(Material.GRAY_DYE, "&bToggle particles &c(OFF)"),
    EXIT_EDIT_MODE(Material.NETHER_STAR, "&eExit edit mode");

    private final ItemStack item;

    CustomItem(Material material, String s2) {
        this(new ItemBuilder(material).name(s2).build());
    }

    CustomItem(ItemStack item) {
        this.item = item;
    }

    public static CustomItem fromItemStack(ItemStack itemStack) {
        return Arrays.stream(values()).filter(customItem -> customItem.getItemStack().equals(itemStack)).findFirst().orElse(null);
    }

    public ItemStack getItemStack() {
        return this.item;
    }
}

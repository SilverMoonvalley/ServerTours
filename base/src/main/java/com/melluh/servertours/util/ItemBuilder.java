package com.melluh.servertours.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

public class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder(ItemStack itemStack) {
        this.item = new ItemStack(itemStack);
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder name(String s) {
        this.meta.setDisplayName(Utils.color(s));
        return this;
    }

    public ItemBuilder lore(List<String> list) {
        this.meta.setLore(Utils.color(list));
        return this;
    }

    public ItemBuilder itemFlags(ItemFlag... array) {
        this.meta.addItemFlags(array);
        return this;
    }

    public <T, Z> ItemBuilder data(NamespacedKey namespacedKey, PersistentDataType<T, Z> persistentDataType, Z b) {
        this.meta.getPersistentDataContainer().set(namespacedKey, persistentDataType, b);
        return this;
    }

    public ItemBuilder textureUrl(String s) {
        ItemMeta meta = this.meta;
        if (meta instanceof SkullMeta skullMeta) {
            PlayerProfile playerProfile = Bukkit.createPlayerProfile(UUID.randomUUID(), "CustomTextures");
            try {
                playerProfile.getTextures().setSkin(URI.create("http://textures.minecraft.net/texture/" + s).toURL());
            } catch (MalformedURLException cause) {
                throw new RuntimeException(cause);
            }
            skullMeta.setOwnerProfile(playerProfile);
        }
        return this;
    }

    public ItemStack build() {
        ItemStack itemStack = new ItemStack(this.item);
        itemStack.setItemMeta(this.meta);
        return itemStack;
    }
}

package com.melluh.servertours.editmode;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.route.point.CraftRoutePoint;
import com.melluh.servertours.util.ItemBuilder;
import com.melluh.servertours.util.Utils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PointSelectMenu {
    private final EditingPlayer ep;
    private final Inventory inv;
    @Getter
    private int currentPage;

    public PointSelectMenu(EditingPlayer ep) {
        this.ep = ep;
        this.inv = Bukkit.createInventory(null, 54, ServerTours.translate("selectInventory.title", ep.getEditingRoute().getName()));
        this.refresh();
    }

    public void refresh() {
        this.inv.clear();
        boolean b = this.currentPage > 0;
        boolean b2 = this.currentPage < this.ep.getEditingRoute().getNumPoints() / 45;
        ItemStack build = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        ItemStack build2 = new ItemBuilder(Material.ARROW).name((b ? ChatColor.GREEN : ChatColor.GRAY) + ServerTours.translate("selectInventory.prevPage")).build();
        ItemStack build3 = new ItemBuilder(Material.ARROW).name((b2 ? ChatColor.GREEN : ChatColor.GRAY) + ServerTours.translate("selectInventory.nextPage")).build();
        this.inv.setItem(45, build2);
        this.inv.setItem(53, build3);
        for (int i = 46; i <= 52; ++i) {
            this.inv.setItem(i, build);
        }
        List<CraftRoutePoint> points = this.ep.getEditingRoute().getPoints();
        for (int j = this.currentPage * 45; j < Math.min(points.size(), (this.currentPage + 1) * 45); ++j) {
            CraftRoutePoint craftRoutePoint = points.get(j);
            List<String> list = new ArrayList<>();
            if (craftRoutePoint.getLabel() != null) {
                list.add(ChatColor.WHITE + craftRoutePoint.getLabel());
                list.add("");
            }
            Location location = craftRoutePoint.getLocation();
            list.add(ChatColor.WHITE + ServerTours.translate("selectInventory.type", craftRoutePoint.getType().getName()));
            list.add(ChatColor.WHITE + ServerTours.translate("selectInventory.world", Utils.getWorldName(location.getWorld())));
            list.add(ChatColor.WHITE + ServerTours.translate("selectInventory.coords", location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            List<String> warnings = craftRoutePoint.getWarnings();
            if (warnings != null && !warnings.isEmpty()) {
                list.add("");
                list.addAll(warnings);
            }
            this.inv.addItem(new ItemBuilder(Material.EMERALD).name(ServerTours.translate("selectInventory.pointTitle", j + 1)).lore(list).build());
        }
    }

    public CraftRoutePoint getClickedPoint(int n) {
        return this.ep.getEditingRoute().getPoint(this.currentPage * 45 + n);
    }

    public void open(Player player) {
        this.currentPage = 0;
        player.openInventory(this.inv);
    }

    public void setPage(int currentPage) {
        if (currentPage < 0 || currentPage > this.ep.getEditingRoute().getNumPoints() / 45) {
            return;
        }
        this.currentPage = currentPage;
        this.refresh();
    }

    public Inventory getInventory() {
        return this.inv;
    }
}

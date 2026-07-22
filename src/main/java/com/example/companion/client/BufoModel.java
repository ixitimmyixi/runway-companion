package com.example.companion.client;

import com.example.companion.entity.BufoEntity;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;

/** Villager geometry with the legs hidden (he hovers, so no dangling legs). */
public class BufoModel extends VillagerModel<BufoEntity> {
    public BufoModel(ModelPart root) {
        super(root);
        hide(root, "right_leg");
        hide(root, "left_leg");
        hide(root, "leg0");
        hide(root, "leg1");
    }

    private static void hide(ModelPart root, String name) {
        try {
            root.getChild(name).visible = false;
        } catch (Exception ignored) {
            // that name doesn't exist in this model; fine.
        }
    }
}

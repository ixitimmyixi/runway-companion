package com.example.companion;

import com.example.companion.entity.BufoEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers Bufo the companion entity and its spawn egg, and wires up the
 * mod-bus events for attributes and the creative spawn-egg tab.
 */
@Mod.EventBusSubscriber(modid = CompanionMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModRegistry {
    private ModRegistry() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CompanionMod.MODID);
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, CompanionMod.MODID);

    public static final RegistryObject<EntityType<BufoEntity>> BUFO = ENTITIES.register("bufo",
        () -> EntityType.Builder.of(BufoEntity::new, MobCategory.CREATURE)
            .sized(0.5f, 0.5f)
            .clientTrackingRange(10)
            .build("bufo"));

    public static final RegistryObject<Item> BUFO_SPAWN_EGG = ITEMS.register("bufo_spawn_egg",
        () -> new ForgeSpawnEggItem(BUFO, 0x6a8f3c, 0xd7e894, new Item.Properties()));

    /** Called from the mod constructor to attach the registers to the mod event bus. */
    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(BUFO.get(), BufoEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(BUFO_SPAWN_EGG);
        }
    }
}

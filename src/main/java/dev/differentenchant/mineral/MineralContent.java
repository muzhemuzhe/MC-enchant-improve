package dev.differentenchant.mineral;

import dev.differentenchant.realm.RealmContent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class MineralContent {
    public static final TagKey<Block> HOSTS=TagKey.create(Registries.BLOCK,RealmContent.id("realm_ore_replaceables"));
    private static Item item(String name){return Registry.register(BuiltInRegistries.ITEM,RealmContent.id(name),new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,RealmContent.id(name)))));}
    public static final Item LUMINOUS_FRAGMENT=item("luminous_fragment");
    public static final Item RAW_SILVER=item("raw_harmonic_silver");
    public static final Item RIFT_CRYSTAL=item("rift_crystal");
    private static Block block(String name,java.util.function.Function<BlockBehaviour.Properties,Block> factory,boolean cluster){
        var properties=BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK,RealmContent.id(name))).strength(3f,6f)
            .sound(cluster?SoundType.AMETHYST_CLUSTER:SoundType.STONE).pushReaction(cluster?PushReaction.DESTROY:PushReaction.BLOCK);
        if(cluster)properties.noOcclusion().lightLevel(s->5);
        var b=Registry.register(BuiltInRegistries.BLOCK,RealmContent.id(name),factory.apply(properties));
        Registry.register(BuiltInRegistries.ITEM,RealmContent.id(name),new BlockItem(b,new Item.Properties().setId(ResourceKey.create(Registries.ITEM,RealmContent.id(name))).useBlockDescriptionPrefix()));return b;
    }
    public static final Block LUMINOUS_STONE=block("luminous_stone",p->new EnchantedOreBlock(p,EnchantmentGate.PROTECTION,()->LUMINOUS_FRAGMENT),false);
    public static final Block HARMONIC_SILVER=block("harmonic_silver_ore",p->new EnchantedOreBlock(p,EnchantmentGate.FIRE,()->RAW_SILVER),false);
    public static final Block RIFT_CLUSTER=block("rift_crystal_cluster",RiftClusterBlock::new,true);
    public static void initialize(){
        Registry.register(BuiltInRegistries.FEATURE,RealmContent.id("realm_ore"),new RealmOreFeature());
        Registry.register(BuiltInRegistries.FEATURE,RealmContent.id("rift_crystals"),new RiftCrystalFeature());
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(o->{o.accept(LUMINOUS_STONE);o.accept(HARMONIC_SILVER);o.accept(RIFT_CLUSTER);});
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(o->{o.accept(LUMINOUS_FRAGMENT);o.accept(RAW_SILVER);o.accept(RIFT_CRYSTAL);});
    }
    private MineralContent(){}
}

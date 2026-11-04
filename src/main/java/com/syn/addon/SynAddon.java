package com.syn.addon;

import com.mojang.logging.LogUtils;
import com.syn.addon.modules.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class SynAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("SynAddon");

    @Override
    public void onInitialize() {
        LOG.info("Initializing SynAddon - Ported from DeepSeek Client & Forever");

        // Meteor 原版有同名模块，加 Plus 后缀避免 ID 冲突
        Modules.get().add(new AutoArmorPlus());
        Modules.get().add(new OffhandPlus());
        Modules.get().add(new BurrowPlus());
        Modules.get().add(new ElytraFlyPlus());

        // deepseekclient / Forever 原版没有 Meteor 同名模块，直接原名
        Modules.get().add(new Mace32K());
        Modules.get().add(new CrystalHurtPeople());
        Modules.get().add(new FlatGroundMace());
        Modules.get().add(new MaceAura());
        Modules.get().add(new MaceGod());
        Modules.get().add(new MacePlus());
        Modules.get().add(new Skybox());
        Modules.get().add(new MaceSpoof());
        Modules.get().add(new BowBomb());
        Modules.get().add(new MaceSpoof2());
        Modules.get().add(new AutoEZ());
        Modules.get().add(new BlockLag());
        Modules.get().add(new FakePlayerPlus());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.syn.addon";
    }
}

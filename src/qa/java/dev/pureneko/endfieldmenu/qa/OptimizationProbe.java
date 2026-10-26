package dev.pureneko.endfieldmenu.qa;

import com.google.gson.*;
import dev.pureneko.endfieldmenu.client.EndfieldMenuScreen;
import java.nio.file.*;
import java.lang.management.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public final class OptimizationProbe {
  private boolean started;private long start,next,sample;private int phase,cycles,worlds,preset;private String label;
  public OptimizationProbe(){NeoForge.EVENT_BUS.addListener(this::tick);}
  private void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){
    label=System.getProperty("endfieldmenu.optimizationQa");if(label==null)return;
    var mc=Minecraft.getInstance();long now=System.nanoTime();
    try{
      if(!started&&mc.screen!=null&&mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))mc.setScreen(new TitleScreen());
      if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
        started=true;start=now;next=now+20_000_000_000L;
        mc.options.pauseOnLostFocus=false;mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);
        mc.createWorldOpenFlows().openWorld("endfield-qa",()->mc.setScreen(new TitleScreen()));
      }
      if(!started)return;
      if(now>=sample){record(mc,now);sample=now+5_000_000_000L;}
      if(now<next||mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
      switch(phase){
        case 0->{key();phase=1;next=now+30_000_000_000L;}
        case 1->{GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),2560,1440);phase=2;next=now+30_000_000_000L;}
        case 2->{key();phase=3;next=now+70_000_000_000L;}
        case 3->{require(mc.screen==null,"hidden world after 70 seconds");var m=menu();var report=m.timingReport();
          require(report.get("cacheEvicted").getAsBoolean(),"default cache evicted");
          require(report.getAsJsonObject("browser").get("gpuTextureBytes").getAsLong()==0,"web GPU freed");
          require(m.backgroundTexture()<0&&m.terrainTexture()<0,"frost and terrain freed");key();phase=4;next=now+1_100_000_000L;}
        case 4->{key();phase=5;next=now+650_000_000L;}
        case 5->{if(mc.screen!=null)throw new IllegalStateException("cycle did not close");cycles++;key();phase=cycles<100?4:6;next=now+1_100_000_000L;}
        case 6->{key();phase=7;next=now+650_000_000L;}
        case 7->{mc.level.disconnect();mc.disconnect();phase=8;next=now+1_000_000_000L;mc.createWorldOpenFlows().openWorld("endfield-qa",()->mc.setScreen(new TitleScreen()));}
        case 8->{if(mc.screen!=null)return;worlds++;key();phase=worlds<10?6:9;next=now+5_000_000_000L;}
        case 9->{if(mc.screen instanceof EndfieldMenuScreen)key();phase=10;next=now+70_000_000_000L;}
        case 10->{require(mc.screen==null,"second idle remains world");
          dev.pureneko.endfieldmenu.client.MenuConfig.LOW_MEMORY.set(true);key();phase=12;next=now+5_000_000_000L;}
        case 12->{require(menu().timingReport().getAsJsonObject("browser").get("completeFrames").getAsLong()>0,"default idle reopens");key();phase=13;next=now+70_000_000_000L;}
        case 13->{require(menu().getLinkedDocument()==null,"low memory closes document and browser");key();phase=14;next=now+8_000_000_000L;}
        case 14->{require(menu().timingReport().getAsJsonObject("browser").get("completeFrames").getAsLong()>0,"low memory reloads");
          dev.pureneko.endfieldmenu.client.MenuConfig.LOW_MEMORY.set(false);preset=0;phase=16;next=now;}
        case 16->{mc.setScreen(new net.minecraft.client.gui.screens.options.VideoSettingsScreen(menu(),mc,mc.options));phase=17;next=now+500_000_000L;}
        case 17->{var list=((dev.pureneko.endfieldmenu.mixin.OptionsSubScreenAccess)mc.screen).endfieldOptions();
          var widgets=((java.util.List<?>)list.children()).stream().flatMap(entry->((net.minecraft.client.gui.components.events.ContainerEventHandler)entry).children().stream()).toList();
          var option=widgets.stream().filter(w->w instanceof net.minecraft.client.gui.components.CycleButton<?> c && c.getMessage().getString().contains(net.minecraft.client.resources.language.I18n.get("endfieldmenu.resolution"))).findFirst().orElseThrow();
          ((net.minecraft.client.gui.components.CycleButton<?>)option).onPress();
          require(dev.pureneko.endfieldmenu.client.MenuConfig.RESOLUTION.get()==(preset+1)%5,"video resolution button callback");
          mc.screen.onClose();phase=18;next=now+6_000_000_000L;}
        case 18->{var report=menu().timingReport().getAsJsonObject("browser");var size=dev.pureneko.endfieldmenu.client.MenuResolution.current();
          require(report.get("width").getAsInt()==size.width()&&report.get("height").getAsInt()==size.height(),"preset raster matches requested dimensions");
          require(menu().actionPoint("settings")!=null,"preset has input coordinates");
          net.minecraft.client.Screenshot.grab(mc.gameDirectory, "optimization-preset-"+dev.pureneko.endfieldmenu.client.MenuConfig.RESOLUTION.get()+".png",mc.getMainRenderTarget(),m->{});
          preset++;if(preset<5){phase=16;next=now;}else{key();phase=11;next=start+900_000_000_000L;}}
        case 11->{require(mc.screen==null,"final idle world");record(mc,now);com.mojang.logging.LogUtils.getLogger().info("[Optimization QA] DONE cycles={} worlds={} seconds={}",cycles,worlds,(now-start)/1e9);mc.stop();phase=15;}
      }
    }catch(Throwable ex){com.mojang.logging.LogUtils.getLogger().error("[Optimization QA] FAILED",ex);mc.stop();phase=12;}
  }
  private void record(Minecraft mc,long now)throws Exception{
    JsonObject j=new JsonObject();j.addProperty("pid",ProcessHandle.current().pid());j.addProperty("label",label);j.addProperty("elapsed",(now-start)/1e9);j.addProperty("phase",phase);j.addProperty("cycles",cycles);j.addProperty("worlds",worlds);
    var memory=ManagementFactory.getMemoryMXBean();j.addProperty("heapUsed",memory.getHeapMemoryUsage().getUsed());j.addProperty("heapCommitted",memory.getHeapMemoryUsage().getCommitted());
    var pools=new JsonObject();for(var b:ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)){var p=new JsonObject();p.addProperty("bytes",b.getMemoryUsed());p.addProperty("count",b.getCount());pools.add(b.getName(),p);}j.add("buffers",pools);
    j.addProperty("screen",mc.screen==null?"world":mc.screen.getClass().getSimpleName());j.addProperty("fps",mc.getFps());
    var field=dev.pureneko.endfieldmenu.client.MenuSession.class.getDeclaredField("menu");field.setAccessible(true);
    if(field.get(null) instanceof EndfieldMenuScreen menu && mc.level!=null)j.add("menu",menu.timingReport());
    Files.writeString(mc.gameDirectory.toPath().resolve("optimization-"+label+".jsonl"),j+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
  }
  private void key(){var mc=Minecraft.getInstance();mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),GLFW.GLFW_KEY_ESCAPE,0,GLFW.GLFW_PRESS,0);mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),GLFW.GLFW_KEY_ESCAPE,0,GLFW.GLFW_RELEASE,0);}
  private static EndfieldMenuScreen menu()throws Exception {var f=dev.pureneko.endfieldmenu.client.MenuSession.class.getDeclaredField("menu");f.setAccessible(true);return (EndfieldMenuScreen)f.get(null);}
  private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);com.mojang.logging.LogUtils.getLogger().info("[Optimization QA] PASS {}",message);}
}

package me.chyxelmc.mmoblock.gui.management;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.gui.Gui;
import me.chyxelmc.mmoblock.gui.GuiAction;
import me.chyxelmc.mmoblock.gui.item.SimpleItem;
import me.chyxelmc.mmoblock.gui.window.GuiWindow;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel;
import me.chyxelmc.mmoblock.utils.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Complete hardcoded editor for one block YAML definition. */
final class BlockEditorGui {
    private static final List<String> FACINGS = List.of("north", "south", "east", "west", "up", "down", "randomUp", "randomDown", "random");
    private final MMOBlock plugin;
    private final BlockConfigLoader loader;
    private final BlockDefinitionStore store;
    private final BlockChatInput input;
    private final BiConsumer<Player, Integer> listOpener;

    BlockEditorGui(final MMOBlock plugin, final BlockConfigLoader loader, final BlockDefinitionStore store,
                   final BlockChatInput input, final BiConsumer<Player, Integer> listOpener) {
        this.plugin = plugin; this.loader = loader; this.store = store; this.input = input; this.listOpener = listOpener;
    }

    void open(final Player p, final String id, final int page) {
        final BlockDefinitionModel d = this.loader.findBlock(id);
        if (d == null) { this.listOpener.accept(p, page); return; }
        final Gui g = base();
        g.setItem(2, button(p, Material.ARROW, "back", "&eBack", List.of(), c -> this.listOpener.accept(p, page)));
        g.setItem(4, new SimpleItem(v -> stack(d.itemMaterial() == null ? Material.IRON_ORE : d.itemMaterial(),
                localizedName(v, id, d), lines(v, "icon_lmb", "&7LMB to edit material", "icon_rmb", "&7RMB to edit block name")), c -> {
            if (c.clickType().isLeftClick()) prompt(p,id,page,"item.material","material_prompt","&eEnter Bukkit material:",s -> { Material m=Material.matchMaterial(s); return m!=null; },s -> s.toLowerCase(Locale.ROOT),null);
            else if (c.clickType().isRightClick()) prompt(p,id,page,"item.name","name_prompt","&eEnter block name:",s -> !s.isBlank(),String::trim,null);
        }));
        g.setItem(19, button(p,Material.CLOCK,"respawn","&eRespawn Time",List.of(current(p,store.string(id,"respawnTime","60")+"s")),c -> prompt(p,id,page,"respawnTime","respawn_prompt","&eEnter positive seconds:",this::positiveInt,s -> Integer.parseInt(s),null)));
        g.setItem(20, button(p,Material.BLAZE_POWDER,"particle","&eParticle Break",lines(p,"particle_lmb","&7LMB edit source","particle_rmb","&7RMB edit fallback"),c -> { if(c.clickType().isLeftClick()) particle(p,id,page); else if(c.clickType().isRightClick()) promptPair(p,id,page,"particleBreak.fallback"); }));
        g.setItem(21, button(p,Material.MUSIC_DISC_13,"sound","&eSound",lines(p,"sound_lmb","&7LMB edit hit sound","sound_rmb","&7RMB edit break sound","sound_shift","&7SHIFT LMB edit respawn sound"),c -> {
            final String path=c.clickType().isShiftClick()&&c.clickType().isLeftClick()?"sound.onRespawn":c.clickType().isRightClick()?"sound.onDead":"sound.onClick";
            prompt(p,id,page,path,"sound_prompt","&eEnter sound key:",s->!s.isBlank(),String::trim,null);
        }));
        g.setItem(22, toggle(p,id,page,"breakAnimation","animation","&eBreak Animation"));
        g.setItem(23, button(p,Material.ARMOR_STAND,"model","&eModel",List.of(status(p,"schematics",store.bool(id,"modelType.schematics.enabled")),status(p,"block",store.bool(id,"modelType.block.enabled"))),c -> model(p,id,page)));
        g.setItem(24, button(p,Material.IRON_PICKAXE,"tools","&eAllowed Tools",List.of(t(p,"click_manage","&7Click to add or remove")),c -> tools(p,id,page)));
        g.setItem(25, button(p,Material.COMPARATOR,"conditions","&eConditions",List.of(t(p,"click_manage","&7Click to manage")),c -> conditions(p,id,page)));
        final SimpleItem[] hologram = new SimpleItem[1];
        hologram[0] = new SimpleItem(v -> stack(Material.NAME_TAG, t(v,"hologram","&eHologram"), hologramLore(v,id)), c -> {
            if (c.clickType().isRightClick() && !c.clickType().isShiftClick()) {
                final String old=store.string(id,"display.displayFacing.facing","cardinal");
                save(id,"display.displayFacing.facing",old.equalsIgnoreCase("cardinal")?"intercardinal":"cardinal");
                hologram[0].setProvider(v -> stack(Material.NAME_TAG,t(v,"hologram","&eHologram"),hologramLore(v,id)));
            } else hologramClick(p,id,page,c.clickType());
        });
        g.setItem(28, hologram[0]);
        g.setItem(29, button(p,Material.FEATHER,"lines","&eHologram Lines",List.of(t(p,"click_manage","&7Click to manage")),c -> displayLines(p,id,page)));
        show(p,g,d.id());
    }

    private void particle(Player p,String id,int page){ Gui g=base(); back(g,p,()->open(p,id,page)); g.setItem(20,toggle(p,id,"particleBreak.enabled","particle_enabled","&eParticle Enabled",()->particle(p,id,page))); g.setItem(22,button(p,Material.BLAZE_POWDER,"source","&eParticle Source",List.of(),c->promptPair(p,id,page,"particleBreak.source"))); show(p,g,t(p,"particle_title","Particle Break")); }
    private void promptPair(Player p,String id,int page,String root){ prompt(p,id,page,root+".type","type_prompt","&eEnter source type:",s->!s.isBlank(),s->s.toUpperCase(Locale.ROOT),()->prompt(p,id,page,root+".value","value_prompt","&eEnter particle/block id:",s->!s.isBlank(),String::trim,()->open(p,id,page))); }

    private void model(Player p,String id,int page){ Gui g=base(); back(g,p,()->open(p,id,page));
        g.setItem(10,toggle(p,id,"modelType.schematics.enabled","schematic_toggle","&eSchematics",()->model(p,id,page)));
        g.setItem(11,edit(p,"normal_file","&eNormal File",c->schematicPrompt(p,id,page,"modelType.schematics.file.normal")));
        g.setItem(12,edit(p,"dead_file","&eDead File",c->schematicPrompt(p,id,page,"modelType.schematics.file.dead")));
        g.setItem(13,button(p,Material.COMPASS,"facing","&ePlace Facing",List.of(current(p,store.string(id,"modelType.schematics.placeFacing","north"))),c->{ cycle(id,"modelType.schematics.placeFacing",FACINGS); model(p,id,page); }));
        g.setItem(14,edit(p,"normal_offset","&eNormal Offset",c->offsetPrompt(p,id,page,"modelType.schematics.adjustPos.normal")));
        g.setItem(15,edit(p,"dead_offset","&eDead Offset",c->offsetPrompt(p,id,page,"modelType.schematics.adjustPos.dead")));
        g.setItem(28,toggle(p,id,"modelType.block.enabled","block_toggle","&eBlock Model",()->model(p,id,page)));
        g.setItem(29,edit(p,"block_type","&eBlock Type",c->prompt(p,id,page,"modelType.block.type","type_prompt","&eEnter model type:",s->!s.isBlank(),String::trim,()->model(p,id,page))));
        g.setItem(30,edit(p,"block_material","&eBlock Material",c->prompt(p,id,page,"modelType.block.material","value_prompt","&eEnter block id:",s->!s.isBlank(),String::trim,()->model(p,id,page)))); show(p,g,t(p,"model_title","Model")); }
    private void schematicPrompt(Player p,String id,int page,String path){ prompt(p,id,page,path,"schematic_prompt","&eEnter schematic path:",store::schematicExists,String::trim,()->model(p,id,page)); }
    private void offsetPrompt(Player p,String id,int page,String path){ prompt(p,id,page,path,"offset_prompt","&eEnter x, y, z:",this::offset,s->List.of(s.split("\\s*,\\s*")),()->model(p,id,page)); }

    private void tools(Player p,String id,int page){ Gui g=base(); back(g,p,()->open(p,id,page)); List<String> values=new ArrayList<>(store.strings(id,"allowedTools")); int slot=10; for(String tool:values){ if(slot>34)break; g.setItem(slot++,button(p,Material.IRON_PICKAXE,null,tool,List.of(t(p,"click_remove","&cClick to remove")),c->{values.remove(tool);save(id,"allowedTools",values);tools(p,id,page);})); } g.setItem(40,button(p,Material.LIME_DYE,"add_tool","&aAdd Tool",List.of(),c->prompt(p,id,page,"allowedTools","tool_prompt","&eEnter tool id:",s->loader.tools().containsKey(s.toLowerCase(Locale.ROOT)),s->{List<String> n=new ArrayList<>(store.strings(id,"allowedTools"));if(!n.contains(s))n.add(s);return n;},()->tools(p,id,page)))); show(p,g,t(p,"tools_title","Allowed Tools")); }

    private void conditions(Player p,String id,int page){ Gui g=base(); back(g,p,()->open(p,id,page)); List<Map<?,?>> list=new ArrayList<>(store.maps(id,"conditions")); int slot=10; for(int i=0;i<list.size()&&slot<=34;i++){final int x=i;Map<?,?> m=list.get(i);g.setItem(slot++,button(p,Material.COMPARATOR,null,"#"+m.get("condition")+" "+m.get("value")+" "+m.get("operator")+" "+m.get("compareTo"),lines(p,"click_edit","&eLMB edit","right_delete","&cRMB delete"),c->{if(c.clickType().isRightClick())confirmDelete(p,()->{list.remove(x);save(id,"conditions",list);conditions(p,id,page);},()->conditions(p,id,page));else conditionEdit(p,id,page,x);}));} g.setItem(40,button(p,Material.LIME_DYE,"add_condition","&aAdd Condition",List.of(),c->{Map<String,Object> n=new LinkedHashMap<>();n.put("condition",list.stream().mapToInt(m->Integer.parseInt(String.valueOf(m.get("condition")))).max().orElse(0)+1);n.put("type","placeholder");n.put("operator","==");n.put("compareTo","");list.add(n);save(id,"conditions",list);conditionEdit(p,id,page,list.size()-1);}));show(p,g,t(p,"conditions_title","Conditions")); }
    private void conditionEdit(Player p,String id,int page,int index){String[] paths={"type","value","operator","compareTo","placeholderText.require","placeholderText.notMet","sendTitle","sendSubtitle"};Gui g=base();back(g,p,()->conditions(p,id,page));for(int i=0;i<paths.length;i++){final String path=paths[i];g.setItem(10+i,edit(p,path,"&e"+path,c->mapPrompt(p,id,page,"conditions",index,path,()->conditionEdit(p,id,page,index))));}show(p,g,t(p,"condition_title","Edit Condition"));}

    private void displayLines(Player p,String id,int page){Gui g=base();back(g,p,()->open(p,id,page));List<Map<?,?>> list=new ArrayList<>(store.maps(id,"display.lines"));list.sort((a,b)->Integer.compare(intv(a.get("line")),intv(b.get("line"))));int slot=10;for(int i=0;i<list.size()&&i<4;i++){final int x=i;Map<?,?>m=list.get(i);Object contents=m.get("contents");g.setItem(slot++,button(p,Material.PAPER,null,"Line "+m.get("line")+" "+shorten(String.valueOf(contents)),lines(p,"click_edit","&eLMB edit","right_delete","&cRMB delete","shift_move","&7Shift L/R move"),c->{if(c.clickType().isShiftClick()){int to=c.clickType().isLeftClick()?x-1:x+1;if(to>=0&&to<list.size()){var tmp=list.get(x);list.set(x,list.get(to));list.set(to,tmp);renumber(list);save(id,"display.lines",list);}displayLines(p,id,page);}else if(c.clickType().isRightClick())confirmDelete(p,()->{list.remove(x);renumber(list);save(id,"display.lines",list);displayLines(p,id,page);},()->displayLines(p,id,page));else lineEdit(p,id,page,x);}));}if(list.size()<4)g.setItem(40,button(p,Material.LIME_DYE,"add_line","&aAdd Line",List.of(),c->{Map<String,Object> n=new LinkedHashMap<>();n.put("line",list.size()+1);n.put("contents",new LinkedHashMap<>(Map.of("text","")));list.add(n);save(id,"display.lines",list);lineEdit(p,id,page,list.size()-1);}));show(p,g,t(p,"lines_title","Hologram Lines"));}
    private void lineEdit(Player p,String id,int page,int index){Gui g=base();back(g,p,()->displayLines(p,id,page));g.setItem(10,edit(p,"line_item","&eSet Item",c->mapPrompt(p,id,page,"display.lines",index,"contents.item",()->{mapSet(id,"display.lines",index,"contents.text",null);lineEdit(p,id,page,index);})));g.setItem(11,edit(p,"line_text","&eSet Text",c->mapPrompt(p,id,page,"display.lines",index,"contents.text",()->{mapSet(id,"display.lines",index,"contents.item",null);lineEdit(p,id,page,index);})));g.setItem(12,edit(p,"line_click","&eClick Content",c->mapPrompt(p,id,page,"display.lines",index,"contents.click",()->lineEdit(p,id,page,index))));g.setItem(13,edit(p,"line_dead","&eDead Content",c->mapPrompt(p,id,page,"display.lines",index,"contents.dead",()->lineEdit(p,id,page,index))));show(p,g,t(p,"line_title","Edit Line"));}

    private void hologramClick(Player p,String id,int page,org.bukkit.event.inventory.ClickType c){if(c.isShiftClick()&&c.isLeftClick())prompt(p,id,page,"display.displayFacing.distance","distance_prompt","&eEnter distance:",this::number,Double::parseDouble,null);else if(c.isShiftClick()&&c.isRightClick())prompt(p,id,page,"display.displayFacing.detectRange","range_prompt","&eEnter positive range:",this::positiveInt,Integer::parseInt,null);else if(c.isRightClick()){String old=store.string(id,"display.displayFacing.facing","cardinal");save(id,"display.displayFacing.facing",old.equalsIgnoreCase("cardinal")?"intercardinal":"cardinal");open(p,id,page);}else prompt(p,id,page,"display.displayHeight","height_prompt","&eEnter display height:",this::number,Double::parseDouble,null);}

    private SimpleItem toggle(Player p,String id,int page,String path,String key,String fallback){return toggle(p,id,path,key,fallback,()->open(p,id,page));}
    private SimpleItem toggle(Player p,String id,String path,String key,String fallback,Runnable after){final SimpleItem[] item=new SimpleItem[1];item[0]=new SimpleItem(v->stack(Material.LEVER,t(v,key,fallback),List.of(status(v,"status",store.bool(id,path)))),c->{save(id,path,!store.bool(id,path));item[0].setProvider(v->stack(Material.LEVER,t(v,key,fallback),List.of(status(v,"status",store.bool(id,path)))));});return item[0];}
    private void prompt(Player p,String id,int page,String path,String key,String fallback,Predicate<String> valid,java.util.function.Function<String,Object> convert,Runnable after){p.closeInventory();p.sendMessage(TextColor.toComponent(t(p,key,fallback)));input.await(p,s->{if(cancel(p,s,()->open(p,id,page)))return;if(!valid.test(s)){p.sendMessage(TextColor.toComponent(t(p,"invalid","&cInvalid value. Try again.")));return;}try{store.set(id,path,convert.apply(s));input.complete(p);if(after!=null)after.run();else open(p,id,page);}catch(Exception e){p.sendMessage(TextColor.toComponent(t(p,"save_failed","&cCould not save.")));}});}
    private void mapPrompt(Player p,String id,int page,String root,int index,String path,Runnable after){p.closeInventory();p.sendMessage(TextColor.toComponent(t(p,"value_prompt","&eEnter value (clear to unset):")));input.await(p,s->{if(cancel(p,s,after))return;mapSet(id,root,index,path,s.equalsIgnoreCase("clear")?null:s);input.complete(p);after.run();});}
    @SuppressWarnings("unchecked") private void mapSet(String id,String root,int index,String path,Object value){List<Map<?,?>> raw=new ArrayList<>(store.maps(id,root));if(index<0||index>=raw.size())return;Map<String,Object> map=deep(raw.get(index));put(map,path,value);raw.set(index,map);save(id,root,raw);}
    private Map<String,Object> deep(Map<?,?> source){Map<String,Object> out=new LinkedHashMap<>();source.forEach((k,v)->out.put(String.valueOf(k),v instanceof Map<?,?>m?deep(m):v));return out;}
    @SuppressWarnings("unchecked") private void put(Map<String,Object> map,String path,Object value){String[] p=path.split("\\.");Map<String,Object> at=map;for(int i=0;i<p.length-1;i++){Object n=at.get(p[i]);if(!(n instanceof Map)){n=new LinkedHashMap<String,Object>();at.put(p[i],n);}at=(Map<String,Object>)n;}if(value==null)at.remove(p[p.length-1]);else at.put(p[p.length-1],value);}
    private boolean cancel(Player p,String s,Runnable back){String word=plugin.getConfig().getString("gui.block-management.cancel-keyword","cancel");if(!s.equalsIgnoreCase(word))return false;input.complete(p);back.run();return true;}
    private void confirmDelete(Player p,Runnable yes,Runnable no){p.closeInventory();p.sendMessage(TextColor.toComponent(t(p,"confirm_delete","&cType delete to confirm or cancel.")));input.await(p,s->{input.complete(p);if(s.equalsIgnoreCase("delete"))yes.run();else no.run();});}
    private void save(String id,String path,Object value){try{store.set(id,path,value);}catch(IOException ignored){}}
    private void cycle(String id,String path,List<String> values){String old=store.string(id,path,values.get(0));int i=values.indexOf(old);save(id,path,values.get((i+1+values.size())%values.size()));}
    private Gui base(){Gui g=Gui.empty(9,5);SimpleItem empty=new SimpleItem(stack(Material.GRAY_STAINED_GLASS_PANE," ",List.of()));g.fill(empty,true);return g;}
    private void back(Gui g,Player p,Runnable action){g.setItem(2,button(p,Material.ARROW,"back","&eBack",List.of(),c->action.run()));}
    private SimpleItem edit(Player p,String key,String fallback,GuiAction a){return button(p,Material.WRITABLE_BOOK,key,fallback,List.of(t(p,"click_edit","&7Click to edit")),a);}
    private SimpleItem button(Player p,Material m,String key,String fallback,List<String> lore,GuiAction a){return new SimpleItem(v->stack(m,key==null?fallback:t(v,key,fallback),lore),a);}
    private ItemStack stack(Material m,String name,List<String> lore){ItemStack s=new ItemStack(m);ItemMeta meta=s.getItemMeta();meta.displayName(TextColor.toComponent(name).decoration(TextDecoration.ITALIC,false));meta.lore(lore.stream().map(TextColor::toComponent).map(x->x.decoration(TextDecoration.ITALIC,false)).toList());s.setItemMeta(meta);return s;}
    private void show(Player p,Gui g,String title){GuiWindow.builder().viewer(p).gui(g).title(TextColor.toLegacySection(title)).open(plugin.guiEngine());}
    private String t(Player p,String key,String fallback){return plugin.translationService().translate(p,"gui.blocks.editor."+key,fallback);}
    private List<String> lines(Player p,String... pairs){List<String> l=new ArrayList<>();for(int i=0;i<pairs.length;i+=2)l.add(t(p,pairs[i],pairs[i+1]));return l;}
    private String current(Player p,String v){return t(p,"current","&7Current: {value}").replace("{value}",v);}
    private String status(Player p,String label,boolean on){return t(p,"status_line","&7{label}: {status}").replace("{label}",label).replace("{status}",on?t(p,"enabled","&aEnabled"):t(p,"disabled","&cDisabled"));}
    private List<String> hologramLore(Player p,String id){return List.of(current(p,store.string(id,"display.displayHeight","1.6")),t(p,"hologram_help","&7LMB height | RMB facing | Shift LMB distance | Shift RMB range"));}
    private String localizedName(Player p,String id,BlockDefinitionModel d){String n=loader.blockListName(id);return plugin.translationService().resolveInline(p,n==null?d.displayName():n);}
    private boolean positiveInt(String s){try{return Integer.parseInt(s.trim())>0;}catch(Exception e){return false;}}
    private boolean number(String s){try{Double.parseDouble(s.trim());return true;}catch(Exception e){return false;}}
    private boolean offset(String s){String[] p=s.split("\\s*,\\s*");return p.length==3&&number(p[0])&&number(p[1])&&number(p[2]);}
    private int intv(Object o){try{return Integer.parseInt(String.valueOf(o));}catch(Exception e){return 0;}}
    private void renumber(List<Map<?,?>> list){for(int i=0;i<list.size();i++){Map<String,Object> m=deep(list.get(i));m.put("line",i+1);list.set(i,m);}}
    private String shorten(String s){return s.length()>36?s.substring(0,36)+"...":s;}
}

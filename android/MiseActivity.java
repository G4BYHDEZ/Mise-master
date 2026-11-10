package com.mise.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

public class MiseActivity extends Activity {
    private static final int REQUEST_VOICE = 42;
    private static final int INK = Color.rgb(37,38,31), GREEN = Color.rgb(83,102,75), PAPER = Color.rgb(247,243,237), MUTED = Color.rgb(115,114,106), LINE = Color.rgb(229,223,213);
    private LinearLayout content;
    private boolean watch;
    private boolean roundWatch;
    private boolean showingShopping;
    private boolean syncReceiverRegistered;
    private final BroadcastReceiver recipeSyncReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (showingShopping) showShopping();
            else showHome();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        watch = getPackageManager().hasSystemFeature("android.hardware.type.watch");
        roundWatch = watch && getResources().getConfiguration().isScreenRound();
        if (!watch) removeExampleRecipes();
        getWindow().setStatusBarColor(PAPER); getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        showHome();
    }

    @Override protected void onStart() {
        super.onStart();
        registerReceiver(recipeSyncReceiver, new IntentFilter(RecipeSyncService.ACTION_RECIPES_UPDATED), Context.RECEIVER_NOT_EXPORTED);
        syncReceiverRegistered = true;
        if (watch) {
            RecipeSyncService.loadLatestRecipes(this);
            RecipeSyncService.loadLatestShopping(this);
        } else {
            RecipeSyncService.publishRecipes(this, recipes().toString());
            RecipeSyncService.publishShopping(this, shopping().toString());
        }
    }

    @Override protected void onStop() {
        if (syncReceiverRegistered) {
            unregisterReceiver(recipeSyncReceiver);
            syncReceiverRegistered = false;
        }
        super.onStop();
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private GradientDrawable bg(int color, int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(watch?13:14); b.setTextColor(Color.WHITE); b.setBackground(bg(INK,12));
        b.setPadding(dp(14),dp(8),dp(14),dp(8)); b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); p.topMargin=dp(8); b.setLayoutParams(p); return b;
    }
    private void line(int height) { View v=new View(this); v.setBackgroundColor(LINE); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(height)); p.setMargins(0,dp(12),0,dp(12)); content.addView(v,p); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); int horizontal=watch?(roundWatch?30:20):22; l.setPadding(dp(horizontal),dp(watch?24:26),dp(horizontal),dp(32)); l.setBackgroundColor(PAPER); return l; }
    private void base(String title) {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); content=column(); scroll.addView(content); setContentView(scroll);
        TextView brand=text("mise.",watch?22:25,GREEN,true); if(roundWatch)brand.setGravity(Gravity.CENTER); content.addView(brand);
        TextView heading=text(title,watch?23:29,INK,true); if(roundWatch)heading.setGravity(Gravity.CENTER); LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2); hp.topMargin=dp(8); content.addView(heading,hp);
    }
    private JSONArray recipes() { try { return new JSONArray(getPreferences(0).getString("recipes", "[]")); } catch(Exception e){return new JSONArray();} }
    private JSONArray shopping() { try { return new JSONArray(getPreferences(0).getString("shopping", "[]")); } catch(Exception e){return new JSONArray();} }
    private void store(String key, JSONArray array) {
        getPreferences(0).edit().putString(key,array.toString()).apply();
        if ("recipes".equals(key) && !watch) RecipeSyncService.publishRecipes(this,array.toString());
        if ("shopping".equals(key)) RecipeSyncService.publishShopping(this,array.toString());
    }
    private void removeExampleRecipes() {
        JSONArray saved = recipes(), remaining = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < saved.length(); i++) {
            JSONObject recipe = saved.optJSONObject(i);
            if (isExampleRecipe(recipe)) removed = true;
            else if (recipe != null) remaining.put(recipe);
        }
        if (removed) store("recipes", remaining);
    }
    private boolean isExampleRecipe(JSONObject recipe) {
        if (recipe == null) return false;
        String name = recipe.optString("name");
        if ("Pasta cremosa de limón".equals(name)) {
            return "🍋".equals(recipe.optString("emoji"))
                    && recipe.optInt("time") == 20
                    && matchesValues(recipe.optJSONArray("ingredients"), "200 g de pasta", "1 limón", "Crema", "Queso parmesano")
                    && matchesValues(recipe.optJSONArray("steps"), "Hierve la pasta hasta que quede al dente.", "Ralla y exprime el limón.", "Mezcla crema, limón y un poco de agua de cocción.", "Agrega la pasta y termina con parmesano.");
        }
        if ("Tostadas de aguacate".equals(name)) {
            return "🥑".equals(recipe.optString("emoji"))
                    && recipe.optInt("time") == 10
                    && matchesValues(recipe.optJSONArray("ingredients"), "2 rebanadas de pan", "1 aguacate", "1 limón")
                    && matchesValues(recipe.optJSONArray("steps"), "Tuesta el pan.", "Machaca el aguacate con limón y sal.", "Sirve sobre el pan y añade chile.");
        }
        return false;
    }
    private boolean matchesValues(JSONArray array, String... values) {
        if (array == null || array.length() != values.length) return false;
        for (int i = 0; i < values.length; i++) {
            if (!values[i].equals(array.optString(i))) return false;
        }
        return true;
    }
    private void showHome() {
        showingShopping=false;
        base(watch?"Mis recetas":"¿Qué se te antoja hoy?");
        JSONArray all=recipes();
        String introText=watch?(all.length()==0?"Abre Mise en el teléfono para sincronizar tus recetas.":"Toca una receta para ver ingredientes y pasos."):"Tus recetas, ingredientes y tiempos en un solo lugar.";
        TextView intro=text(introText,watch?12:14,MUTED,false); if(roundWatch)intro.setGravity(Gravity.CENTER); content.addView(intro);
        if(!watch) {
            content.addView(button("🎙  Buscar receta por ingrediente",()->searchIngredientByVoice()));
            content.addView(button("＋  Nueva receta",()->manualRecipe()));
            content.addView(button("▤  Lista de compras ("+shopping().length()+")",()->showShopping()));
        } else {
            content.addView(button("🛒 Compras ("+shopping().length()+")",()->showShopping()));
        }
        line(1); content.addView(text("RECETARIO",11,GREEN,true));
        if(all.length()==0) content.addView(text("Aún no tienes ninguna receta guardada.",13,MUTED,false));
        for(int i=0;i<all.length();i++) { JSONObject r=all.optJSONObject(i); if(r==null)continue; final int index=i;
            JSONArray ingredients=r.optJSONArray("ingredients");
            String recipeTitle=r.optString("emoji","🍲")+"   "+r.optString("name");
            String recipeMeta="◷ "+r.optInt("time",20)+" min   ·   "+(ingredients==null?0:ingredients.length())+" ingredientes";
            TextView card=text(recipeTitle+"\n"+recipeMeta,watch?13:15,INK,true);
            if(roundWatch)card.setGravity(Gravity.CENTER);
            card.setPadding(dp(12),dp(watch?14:10),dp(12),dp(watch?14:10)); card.setMinHeight(dp(watch?70:0)); card.setLineSpacing(dp(2),1f);
            card.setBackgroundColor(Color.TRANSPARENT);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(bg(Color.WHITE,watch?18:12));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(watch?10:8); content.addView(row,p);
            row.addView(card,new LinearLayout.LayoutParams(0,-2,1));
            card.setOnClickListener(v->showRecipe(index));
            if (!watch) {
                Button delete = new Button(this);
                delete.setText("Eliminar");
                delete.setAllCaps(false);
                delete.setTextSize(12);
                delete.setTextColor(Color.rgb(150,55,48));
                delete.setContentDescription("Eliminar receta: "+r.optString("name"));
                delete.setBackground(bg(Color.rgb(250,237,234),10));
                delete.setPadding(dp(8),dp(4),dp(8),dp(4));
                LinearLayout.LayoutParams deleteParams=new LinearLayout.LayoutParams(-2,-2);
                deleteParams.setMargins(0,0,dp(8),0);
                row.addView(delete,deleteParams);
                delete.setOnClickListener(v->confirmDeleteRecipe(index,r.optString("name")));
            }
        }
    }
    private void confirmDeleteRecipe(int index, String name) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar receta")
                .setMessage("¿Quieres eliminar \""+name+"\" de tu recetario?")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Eliminar",(dialog,which)->{
                    JSONArray saved=recipes();
                    if(index<0||index>=saved.length())return;
                    saved.remove(index);
                    store("recipes",saved);
                    showHome();
                    Toast.makeText(this,"Receta eliminada",Toast.LENGTH_SHORT).show();
                })
                .show();
    }
    private void searchIngredientByVoice() {
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-MX"); i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Di un ingrediente para buscar recetas que lo usen");
        try { startActivityForResult(i,REQUEST_VOICE); } catch(Exception e){ new AlertDialog.Builder(this).setMessage("No encontramos un servicio de reconocimiento de voz. Activa o instala el dictado por voz e inténtalo de nuevo.").setPositiveButton("Cerrar",null).show(); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data); if(request!=REQUEST_VOICE||result!=RESULT_OK||data==null)return;
        ArrayList<String> matches=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS); if(matches==null||matches.isEmpty())return; String spoken=matches.get(0).trim();
        if(!spoken.isEmpty())searchMealsForIngredient(spoken);
    }
    private JSONObject fetchWikibooks(String endpoint) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL("https://es.wikibooks.org/w/api.php?"+endpoint).openConnection();
        connection.setRequestMethod("GET");connection.setRequestProperty("User-Agent","MiseCocina/1.0 (Wikilibros API client)");connection.setConnectTimeout(10000);connection.setReadTimeout(10000);
        try {
            int status=connection.getResponseCode();if(status<200||status>=300)throw new java.io.IOException("HTTP "+status);
            StringBuilder body=new StringBuilder();try(BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),"UTF-8"))){String line;while((line=reader.readLine())!=null)body.append(line);}
            return new JSONObject(body.toString());
        } finally {connection.disconnect();}
    }
    private void searchMealsForIngredient(String ingredient) {
        Toast.makeText(this,"Buscando recetas…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try {
                String endpoint="action=query&list=search&srsearch="+URLEncoder.encode(ingredient,"UTF-8")+"&srnamespace=0&srlimit=50&format=json&origin=*";
                JSONObject query=fetchWikibooks(endpoint).optJSONObject("query");
                JSONArray results=query==null?null:query.optJSONArray("search");
                runOnUiThread(()->{
                    ArrayList<String> names=new ArrayList<>(),ids=new ArrayList<>();
                    for(int i=0;results!=null&&i<results.length()&&names.size()<8;i++){
                        JSONObject page=results.optJSONObject(i);if(page==null)continue;
                        String title=page.optString("title","");
                        if(!title.startsWith("Artes culinarias/Recetas/")||title.substring(title.lastIndexOf('/')+1).length()<2)continue;
                        names.add(title.substring(title.lastIndexOf('/')+1));ids.add(page.optString("pageid"));
                    }
                    if(names.isEmpty()){new AlertDialog.Builder(this).setTitle("Sin resultados").setMessage("No encontré recetas en español para "+ingredient+". Prueba con otro ingrediente.").setPositiveButton("Cerrar",null).show();return;}
                    String[] nameArray=names.toArray(new String[0]),idArray=ids.toArray(new String[0]);
                    new AlertDialog.Builder(this).setTitle("Recetas con "+ingredient).setItems(nameArray,(dialog,which)->loadOnlineMeal(idArray[which])).setNegativeButton("Cerrar",null).show();
                });
            } catch(Exception error) {
                runOnUiThread(()->new AlertDialog.Builder(this).setTitle("No se pudo buscar").setMessage("Revisa tu conexión a internet e inténtalo de nuevo.").setPositiveButton("Cerrar",null).show());
            }
        }).start();
    }
    private void loadOnlineMeal(String mealId) {
        new Thread(()->{
            try {
                JSONObject response=fetchWikibooks("action=parse&pageid="+URLEncoder.encode(mealId,"UTF-8")+"&prop=wikitext&format=json&origin=*");
                JSONObject page=response.optJSONObject("parse");
                if(page==null)throw new java.io.IOException("La receta ya no está disponible.");
                String title=page.optString("title","Artes culinarias/Recetas/Receta");
                JSONObject wiki=page.optJSONObject("wikitext");
                JSONObject parsed=parseWikiRecipe(wiki==null?"":wiki.optString("*",""));
                String sourceUrl="https://es.wikibooks.org/wiki/"+URLEncoder.encode(title.replace(' ','_'),"UTF-8").replace("+","%20").replace("%2F","/");
                JSONObject recipe=new JSONObject().put("name",title.substring(title.lastIndexOf('/')+1)).put("emoji","🍲").put("time",25).put("ingredients",parsed.getJSONArray("ingredients")).put("steps",parsed.getJSONArray("steps")).put("sourceUrl",sourceUrl);
                runOnUiThread(()->showOnlineRecipePreview(recipe));
            } catch(Exception error) {
                runOnUiThread(()->new AlertDialog.Builder(this).setTitle("No se pudo cargar").setMessage("No fue posible obtener el detalle de la receta. Intenta de nuevo.").setPositiveButton("Cerrar",null).show());
            }
        }).start();
    }
    private JSONObject parseWikiRecipe(String extract) throws Exception {
        JSONArray ingredients=new JSONArray(),steps=new JSONArray();
        String ingredientText=wikiField(extract,"ingredientes"),stepText=wikiField(extract,"procedimiento");
        for(String raw:ingredientText.split("\\r?\\n+")){String line=cleanWikiLine(raw);if(!line.isEmpty())ingredients.put(line);}
        for(String raw:stepText.split("\\r?\\n+")){String line=cleanWikiLine(raw);if(!line.isEmpty())steps.put(line);}
        if(steps.length()==0)steps.put("Consulta la receta original en Wikilibros para ver la preparación.");
        return new JSONObject().put("ingredients",ingredients).put("steps",steps);
    }
    private String wikiField(String source,String name) {
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("\\|"+name+"\\s*=([\\s\\S]*?)(?=\\n\\s*\\|[\\wáéíóúñ_ -]+\\s*=|\\n\\s*\\}\\})",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(source);
        return matcher.find()?matcher.group(1).trim():"";
    }
    private String cleanWikiLine(String raw) {
        java.util.regex.Matcher ingredient=java.util.regex.Pattern.compile("\\{\\{ing\\|([^|}]+)(?:\\|([^}]*))?\\}\\}",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(raw);
        StringBuffer cleaned=new StringBuffer();
        while(ingredient.find())ingredient.appendReplacement(cleaned,java.util.regex.Matcher.quoteReplacement(ingredient.group(2)==null?ingredient.group(1):ingredient.group(2)));
        ingredient.appendTail(cleaned);
        return cleaned.toString().replaceAll("\\[\\[(?:[^\\]|]*\\|)?([^\\]]+)\\]\\]","$1").replaceAll("'{2,5}","").replaceFirst("^[*#;:]+\\s*","").trim();
    }
    private void manualRecipe() {
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(18),dp(8),dp(18),0);
        EditText name=new EditText(this);name.setHint("Nombre de la receta");form.addView(name);
        EditText ingredients=new EditText(this);ingredients.setHint("Ingredientes separados por coma");form.addView(ingredients);
        EditText steps=new EditText(this);steps.setHint("Pasos separados por punto");form.addView(steps);
        new AlertDialog.Builder(this).setTitle("Nueva receta").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->saveManual(name.getText().toString(),ingredients.getText().toString(),steps.getText().toString())).show();
    }
    private void saveManual(String name,String ingredientText,String stepText) {
        if(name.trim().isEmpty()){Toast.makeText(this,"Escribe un nombre",Toast.LENGTH_SHORT).show();return;}
        JSONArray ing=new JSONArray();for(String s:ingredientText.split(","))if(!s.trim().isEmpty())ing.put(s.trim());JSONArray st=new JSONArray();for(String s:stepText.split("[.!?]+"))if(!s.trim().isEmpty())st.put(s.trim());if(st.length()==0)st.put("Prepara y combina los ingredientes.");if(ing.length()==0)ing.put("Añade ingredientes a esta receta");
        try{JSONArray a=recipes();a.put(new JSONObject().put("name",name.trim()).put("emoji","🍲").put("time",25).put("ingredients",ing).put("steps",st));store("recipes",a);}catch(Exception ignored){}showHome();
    }
    private void showRecipe(int index) {
        JSONObject r=recipes().optJSONObject(index);if(r==null)return;JSONArray ing=r.optJSONArray("ingredients"),steps=r.optJSONArray("steps");StringBuilder detail=new StringBuilder(r.optString("emoji","🍲")).append("  ").append(r.optString("name")).append("\n◷ ").append(r.optInt("time",20)).append(" min\n\nINGREDIENTES\n");
        for(int i=0;ing!=null&&i<ing.length();i++)detail.append("• ").append(ing.optString(i)).append("\n");detail.append("\nPREPARACIÓN\n");for(int i=0;steps!=null&&i<steps.length();i++)detail.append(i+1).append(". ").append(steps.optString(i)).append("\n");
        if(r.has("sourceUrl"))detail.append("\nFuente: Wikilibros (CC BY-SA 4.0)\n").append(r.optString("sourceUrl"));
        new AlertDialog.Builder(this).setTitle("Tu receta").setMessage(detail.toString()).setPositiveButton("Empezar",(d,w)->showCooking(r,0)).setNeutralButton("Añadir a compras",(d,w)->{
            try {
                JSONArray list=shopping();
                for(int i=0;ing!=null&&i<ing.length();i++)list.put(new JSONObject().put("name",ing.optString(i)).put("purchased",false));
                store("shopping",list);
                Toast.makeText(this,"Ingredientes añadidos",Toast.LENGTH_SHORT).show();
                showHome();
            } catch(org.json.JSONException error) {
                Toast.makeText(this,"No se pudieron añadir los ingredientes.",Toast.LENGTH_LONG).show();
            }
        }).setNegativeButton("Cerrar",null).show();
    }
    private void showCooking(JSONObject r,int step) {
        JSONArray steps=r.optJSONArray("steps");if(steps==null||steps.length()==0)return;final int current=Math.min(step,steps.length()-1);
        String msg="PASO "+(current+1)+" DE "+steps.length()+"\n\n"+steps.optString(current);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(r.optString("name")).setMessage(msg).setNegativeButton(current>0?"← Anterior":"Cerrar",null).setPositiveButton(current+1<steps.length()?"Siguiente →":"Terminar",null).create();
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(x->{
                if(current>0){dialog.dismiss();showCooking(r,current-1);}else dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{
                if(current+1<steps.length()){dialog.dismiss();showCooking(r,current+1);}else dialog.dismiss();
            });
        });
        dialog.show();
    }
    private void showShopping() {
        showingShopping=true;
        base("Lista de compras");
        content.addView(button("← Volver al recetario",this::showHome));
        JSONArray items=shopping();
        if(items.length()==0) {
            TextView empty=text("Tu lista de compras está vacía.",14,MUTED,false);
            LinearLayout.LayoutParams emptyParams=new LinearLayout.LayoutParams(-1,-2);emptyParams.topMargin=dp(16);content.addView(empty,emptyParams);
        }
        for(int i=0;i<items.length();i++) {
            final int index=i;
            JSONObject item=shoppingItem(items.opt(i));
            if(item==null)continue;
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.topMargin=dp(8);content.addView(row,rowParams);
            CheckBox check=new CheckBox(this);
            check.setText(item.optString("name"));
            check.setTextSize(15);
            check.setTextColor(item.optBoolean("purchased")?MUTED:INK);
            check.setChecked(item.optBoolean("purchased"));
            if(watch) {
                check.setButtonTintList(new ColorStateList(
                        new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
                        new int[]{Color.rgb(35,112,190),Color.rgb(105,110,118)}));
            }
            row.addView(check,new LinearLayout.LayoutParams(0,-2,1));
            check.setOnCheckedChangeListener((button,checked)->{
                JSONArray current=shopping();
                JSONObject updated=shoppingItem(current.opt(index));
                if(updated==null)return;
                try {
                    updated.put("purchased",checked);
                    current.put(index,updated);
                    store("shopping",current);
                    showShopping();
                } catch(org.json.JSONException error) {
                    Toast.makeText(this,"No se pudo actualizar el ingrediente.",Toast.LENGTH_LONG).show();
                }
            });
            if (!watch) {
                Button delete=new Button(this);
                delete.setText("Eliminar");
                delete.setAllCaps(false);
                delete.setTextSize(12);
                delete.setTextColor(Color.rgb(150,55,48));
                delete.setContentDescription("Eliminar ingrediente: "+item.optString("name"));
                delete.setBackground(bg(Color.rgb(250,237,234),10));
                row.addView(delete,new LinearLayout.LayoutParams(-2,-2));
                delete.setOnClickListener(v->{
                    JSONArray current=shopping();
                    if(index>=0&&index<current.length())current.remove(index);
                    store("shopping",current);
                    showShopping();
                });
            }
        }
        if(!watch) {
            line(1);
            EditText add=new EditText(this);
            add.setSingleLine(true);
            add.setHint("Añadir ingrediente…");
            content.addView(add,new LinearLayout.LayoutParams(-1,-2));
            content.addView(button("＋  Añadir ingrediente",()->{
                String name=add.getText().toString().trim();
                if(name.isEmpty()) {
                    Toast.makeText(this,"Escribe un ingrediente",Toast.LENGTH_SHORT).show();
                    return;
                }
                JSONArray current=shopping();
                try {
                    current.put(new JSONObject().put("name",name).put("purchased",false));
                    store("shopping",current);
                    showShopping();
                } catch(org.json.JSONException error) {
                    Toast.makeText(this,"No se pudo añadir el ingrediente.",Toast.LENGTH_LONG).show();
                }
            }));
        }
    }
    private JSONObject shoppingItem(Object value) {
        if(value instanceof JSONObject)return (JSONObject)value;
        if(value instanceof String) {
            try{return new JSONObject().put("name",(String)value).put("purchased",false);}
            catch(org.json.JSONException error){return null;}
        }
        return null;
    }
    private void showOnlineRecipePreview(JSONObject recipe) {
        showingShopping=false;
        String name=recipe.optString("name","Receta");
        base(name);
        TextView summary=text("Revisa los ingredientes y los pasos antes de agregar esta receta a tu recetario.",14,MUTED,false);
        content.addView(summary);
        line(1);
        content.addView(text("INGREDIENTES",12,GREEN,true));
        JSONArray ingredients=recipe.optJSONArray("ingredients");
        if(ingredients==null||ingredients.length()==0) {
            content.addView(text("No se encontraron ingredientes en la página.",14,MUTED,false));
        } else {
            for(int i=0;i<ingredients.length();i++)content.addView(text("•  "+ingredients.optString(i),14,INK,false));
        }
        line(1);
        content.addView(text("PREPARACIÓN",12,GREEN,true));
        JSONArray steps=recipe.optJSONArray("steps");
        for(int i=0;steps!=null&&i<steps.length();i++) {
            LinearLayout.LayoutParams stepParams=new LinearLayout.LayoutParams(-1,-2);stepParams.topMargin=dp(6);
            TextView step=text((i+1)+".  "+steps.optString(i),14,INK,false);
            step.setGravity(Gravity.TOP);
            content.addView(step,stepParams);
        }
        if(recipe.has("sourceUrl")) {
            TextView source=text("Fuente: Wikilibros (CC BY-SA 4.0)\n"+recipe.optString("sourceUrl"),12,MUTED,false);
            source.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
            LinearLayout.LayoutParams sourceParams=new LinearLayout.LayoutParams(-1,-2);sourceParams.topMargin=dp(14);content.addView(source,sourceParams);
        }
        line(1);
        content.addView(button("Agregar al recetario",()->{
            JSONArray saved=recipes();
            if(isRecipeAlreadySaved(recipe,saved)) {
                Toast.makeText(this,"La receta ya se agregó al recetario",Toast.LENGTH_SHORT).show();
                showHome();
                return;
            }
            saved.put(recipe);
            store("recipes",saved);
            Toast.makeText(this,"Receta agregada al recetario",Toast.LENGTH_SHORT).show();
            showHome();
        }));
        content.addView(button("No agregar",this::showHome));
    }
    private boolean isRecipeAlreadySaved(JSONObject recipe, JSONArray saved) {
        String sourceUrl=recipe.optString("sourceUrl","");
        String normalizedName=normalizeRecipeName(recipe.optString("name",""));
        for(int i=0;i<saved.length();i++) {
            JSONObject existing=saved.optJSONObject(i);
            if(existing==null)continue;
            String existingUrl=existing.optString("sourceUrl","");
            if(!sourceUrl.isEmpty()&&sourceUrl.equals(existingUrl))return true;
            if(!normalizedName.isEmpty()
                    &&normalizedName.equals(normalizeRecipeName(existing.optString("name",""))))return true;
        }
        return false;
    }
    private String normalizeRecipeName(String name) {
        String normalized=Normalizer.normalize(name.trim(),Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","");
        return normalized.replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
    }
}

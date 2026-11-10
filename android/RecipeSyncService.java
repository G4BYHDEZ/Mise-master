package com.mise.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

public final class RecipeSyncService extends com.google.android.gms.wearable.WearableListenerService {
    public static final String ACTION_RECIPES_UPDATED = "com.mise.app.RECIPES_UPDATED";
    private static final String TAG = "MiseRecipeSync";
    private static final String RECIPES_PATH = "/recipes";
    private static final String SHOPPING_PATH = "/shopping";
    private static final String PREFERENCES = "MiseActivity";

    public static void publishRecipes(Context context, String recipesJson) {
        try {
            PutDataMapRequest request = PutDataMapRequest.create(RECIPES_PATH);
            request.getDataMap().putString("json", recipesJson);
            request.getDataMap().putLong("publishedAt", System.currentTimeMillis());
            Task<DataItem> task = Wearable.getDataClient(context)
                    .putDataItem(request.setUrgent().asPutDataRequest());
            task.addOnFailureListener(error -> Log.e(TAG, "No se pudieron sincronizar las recetas con el reloj.", error));
        } catch (RuntimeException error) {
            Log.e(TAG, "La sincronización con Wear OS no está disponible.", error);
        }
    }

    public static void publishShopping(Context context, String shoppingJson) {
        try {
            PutDataMapRequest request = PutDataMapRequest.create(SHOPPING_PATH);
            request.getDataMap().putString("json", shoppingJson);
            request.getDataMap().putLong("publishedAt", System.currentTimeMillis());
            Task<DataItem> task = Wearable.getDataClient(context)
                    .putDataItem(request.setUrgent().asPutDataRequest());
            task.addOnFailureListener(error -> Log.e(TAG, "No se pudo sincronizar la lista de compras.", error));
        } catch (RuntimeException error) {
            Log.e(TAG, "La sincronización de compras con Wear OS no está disponible.", error);
        }
    }

    public static void loadLatestRecipes(Context context) {
        try {
            Uri uri = Uri.parse("wear://*/recipes");
            Wearable.getDataClient(context).getDataItems(uri, DataClient.FILTER_PREFIX)
                    .addOnSuccessListener(items -> {
                        try {
                            for (int i = 0; i < items.getCount(); i++) {
                                DataItem item = items.get(i);
                                if (item.getUri().getPath().startsWith(RECIPES_PATH)) {
                                    applyRecipes(context, DataMapItem.fromDataItem(item).getDataMap().getString("json"));
                                    break;
                                }
                            }
                        } finally {
                            items.release();
                        }
                    })
                    .addOnFailureListener(error -> Log.e(TAG, "No se pudieron cargar las recetas sincronizadas.", error));
        } catch (RuntimeException error) {
            Log.e(TAG, "No se pudo consultar la sincronización de Wear OS.", error);
        }
    }

    public static void loadLatestShopping(Context context) {
        try {
            Uri uri = Uri.parse("wear://*/shopping");
            Wearable.getDataClient(context).getDataItems(uri, DataClient.FILTER_PREFIX)
                    .addOnSuccessListener(items -> {
                        try {
                            for (int i = 0; i < items.getCount(); i++) {
                                DataItem item = items.get(i);
                                if (SHOPPING_PATH.equals(item.getUri().getPath())) {
                                    applyShopping(context, DataMapItem.fromDataItem(item).getDataMap().getString("json"));
                                    break;
                                }
                            }
                        } finally {
                            items.release();
                        }
                    })
                    .addOnFailureListener(error -> Log.e(TAG, "No se pudo cargar la lista de compras sincronizada.", error));
        } catch (RuntimeException error) {
            Log.e(TAG, "No se pudo consultar la lista de compras de Wear OS.", error);
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer events) {
        try {
            for (DataEvent event : events) {
                if (event.getType() != DataEvent.TYPE_CHANGED) continue;
                String path = event.getDataItem().getUri().getPath();
                String json = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getString("json");
                if (RECIPES_PATH.equals(path)) {
                    applyRecipes(this, json);
                } else if (SHOPPING_PATH.equals(path)) {
                    applyShopping(this, json);
                }
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "No se pudieron aplicar los datos recibidos.", error);
        } finally {
            events.release();
        }
    }

    private static void applyRecipes(Context context, String recipesJson) {
        if (recipesJson == null || recipesJson.isEmpty()) {
            Log.e(TAG, "Se recibió una lista de recetas vacía.");
            return;
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString("recipes", recipesJson)
                .apply();
        Intent update = new Intent(ACTION_RECIPES_UPDATED).setPackage(context.getPackageName());
        context.sendBroadcast(update);
    }

    private static void applyShopping(Context context, String shoppingJson) {
        if (shoppingJson == null || shoppingJson.isEmpty()) {
            Log.e(TAG, "Se recibió una lista de compras sin contenido JSON.");
            return;
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString("shopping", shoppingJson)
                .apply();
        Intent update = new Intent(ACTION_RECIPES_UPDATED).setPackage(context.getPackageName());
        context.sendBroadcast(update);
    }
}
